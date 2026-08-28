package org.example.fastfinder.index

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.example.fastfinder.util.AppPaths
import org.example.fastfinder.util.Logger
import org.example.fastfinder.util.getFileType
import java.io.File
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.io.AccessDeniedException

/**
 * Builds and maintains the Lucene index of the local filesystem.
 *
 * Indexing always writes to a temporary directory first and only swaps it
 * into place once the new index has committed successfully, so [indexPath]
 * keeps serving searches from the previous index while a new one is built.
 */
class DBManager(
    indexDirectoryName: String = "database",
    baseDirectory: Path = AppPaths.root,
    // How many indexed items pass between progress updates - throttled so the fork-join
    // indexing threads aren't all hammering a shared StateFlow write on every single file.
    private val progressUpdateInterval: Int = 1000,
) {
    private val analyzer = StandardAnalyzer()
    private val totalIndexed = AtomicInteger(0)
    private val skippedPaths = Collections.synchronizedList(mutableListOf<String>())
    private val lock = ReentrantLock()

    val indexPath: Path
    private val stateFilePath: Path
    private var indexDirectory: FSDirectory

    var isFirstIndexCreation: Boolean
        private set

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _indexedCount = MutableStateFlow(0)
    /** Number of items indexed so far in the current (or most recently completed) run. */
    val indexedCount: StateFlow<Int> = _indexedCount.asStateFlow()

    init {
        indexPath = baseDirectory.resolve(indexDirectoryName)
        stateFilePath = indexPath.resolve("index_state.txt")

        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }

        indexDirectory = FSDirectory.open(indexPath)
        isFirstIndexCreation = readStateFile()
        Logger.info("Is first index creation: $isFirstIndexCreation")
        Logger.info("Index path: $indexPath")
    }

    fun createOrUpdateIndex(forceIndexCreation: Boolean = false, roots: List<File> = File.listRoots().toList()) {
        if (!_isIndexing.compareAndSet(false, true)) {
            Logger.info("Indexing already in progress. Skipping this request.")
            return
        }

        if (!forceIndexCreation && !isFirstIndexCreation && indexExists()) {
            Logger.info("Index already exists. Skipping indexing.")
            _isIndexing.value = false
            return
        }

        thread(name = "fastfinder-indexer", start = true) {
            lock.lock()
            var tempDirectory: Path? = null
            try {
                Logger.info("Starting indexing process...")
                totalIndexed.set(0)
                _indexedCount.value = 0
                skippedPaths.clear()
                _lastError.value = null

                tempDirectory = Files.createTempDirectory("fastfinder_index_")
                val newIndexPath = tempDirectory.toAbsolutePath()

                FSDirectory.open(newIndexPath).use { newIndexDir ->
                    IndexWriter(newIndexDir, IndexWriterConfig(analyzer)).use { writer ->
                        indexFilesAndDirectories(writer, roots)
                        writer.commit()
                    }
                }

                Logger.info("Indexing completed. Total items indexed: ${totalIndexed.get()}")
                _indexedCount.value = totalIndexed.get()
                replaceOldIndexWithNew(newIndexPath)
                isFirstIndexCreation = false
                writeStateFile()
            } catch (e: Exception) {
                Logger.error("Error during indexing", e)
                _lastError.value = "Indexing failed: ${e.message ?: e::class.simpleName}"
                tempDirectory?.let(::cleanUpTemporaryDirectory)
            } finally {
                _isIndexing.value = false
                lock.unlock()
            }
        }
        Logger.info("Indexing process started in the background.")
    }

    fun clearLastError() {
        _lastError.value = null
    }

    private fun readStateFile(): Boolean {
        if (!Files.exists(stateFilePath)) return true
        return try {
            Files.readAllLines(stateFilePath).getOrNull(0)?.toBoolean() ?: true
        } catch (e: IOException) {
            Logger.error("Error reading state file", e)
            true
        }
    }

    private fun writeStateFile() {
        try {
            Files.write(
                stateFilePath,
                listOf(isFirstIndexCreation.toString()),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            )
        } catch (e: IOException) {
            Logger.error("Error writing state file", e)
        }
    }

    private fun indexExists(): Boolean = try {
        DirectoryReader.open(indexDirectory).use { true }
    } catch (e: IOException) {
        Logger.info("No existing index to open: ${e.message}")
        false
    }

    /**
     * Walks every filesystem root in parallel using a fork-join, work-stealing pool:
     * each directory is its own [RecursiveTask] that forks one child task per
     * subdirectory. This keeps every worker thread busy even when there's only a
     * single drive, unlike one-task-per-root, which leaves every thread but one idle
     * on a typical single-drive machine.
     *
     * Each task returns the total size of its subtree so parent directories can sum
     * their children's sizes directly, instead of every file walking back up through
     * all of its ancestors to update a shared size map.
     */
    internal fun indexFilesAndDirectories(indexWriter: IndexWriter, roots: List<File> = File.listRoots().toList()) {
        val pool = ForkJoinPool(Runtime.getRuntime().availableProcessors())
        try {
            val rootTasks = roots.map { root ->
                Logger.info("Walking directory tree from: ${root.absolutePath}")
                IndexDirectoryTask(root.toPath(), indexWriter).also(pool::execute)
            }
            rootTasks.forEach { task ->
                try {
                    task.join()
                } catch (e: Exception) {
                    Logger.error("Error walking through root directory", e)
                }
            }
        } finally {
            pool.shutdown()
            try {
                if (!pool.awaitTermination(1, TimeUnit.HOURS)) {
                    Logger.warn("Timeout waiting for indexing tasks to complete.")
                }
            } catch (e: InterruptedException) {
                Logger.warn("Indexing was interrupted: ${e.message}")
            }
        }
    }

    /** Indexes [directory] and everything under it, returning the subtree's total size in bytes. */
    private inner class IndexDirectoryTask(
        private val directory: Path,
        private val indexWriter: IndexWriter,
    ) : RecursiveTask<Long>() {

        override fun compute(): Long {
            if (isRestrictedDirectory(directory)) {
                skippedPaths.add("Directory: $directory (Restricted)")
                return 0L
            }

            val entries = try {
                Files.newDirectoryStream(directory).use { it.toList() }
            } catch (e: AccessDeniedException) {
                skippedPaths.add("Directory: $directory (Access Denied: ${e.message})")
                return 0L
            } catch (e: DirectoryIteratorException) {
                skippedPaths.add("Directory: $directory (${e.cause?.message})")
                return 0L
            } catch (e: IOException) {
                skippedPaths.add("Directory: $directory (${e.message})")
                return 0L
            }

            var ownFilesSize = 0L
            val subDirectoryTasks = mutableListOf<IndexDirectoryTask>()

            for (entry in entries) {
                val attrs = try {
                    Files.readAttributes(entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                } catch (e: AccessDeniedException) {
                    skippedPaths.add("File: $entry (Access Denied: ${e.message})")
                    continue
                } catch (e: IOException) {
                    skippedPaths.add("Failed to access: $entry (${e.message})")
                    continue
                }

                if (attrs.isDirectory) {
                    subDirectoryTasks.add(IndexDirectoryTask(entry, indexWriter).also { it.fork() })
                } else {
                    addToIndex(entry, indexWriter, isFile = true, size = attrs.size())
                    ownFilesSize += attrs.size()
                }
            }

            val subtreeSize = ownFilesSize + subDirectoryTasks.sumOf { it.join() }
            addToIndex(directory, indexWriter, isFile = false, size = subtreeSize)
            return subtreeSize
        }
    }

    private fun addToIndex(path: Path, indexWriter: IndexWriter, isFile: Boolean, size: Long) {
        val fullFileName = path.fileName?.toString() ?: return
        val document = Document().apply {
            add(TextField("nameOriginal", fullFileName, Field.Store.YES))
            add(TextField("name", fullFileName.lowercase(), Field.Store.YES))
            add(StringField("parent", path.parent?.toString() ?: "", Field.Store.YES))
            add(StringField("path", path.toAbsolutePath().toString(), Field.Store.YES))
            add(StringField("isFile", isFile.toString(), Field.Store.YES))
            add(LongPoint("size", size))
            add(TextField("sizeDisplay", size.toString(), Field.Store.YES))
            if (isFile) {
                // Stored=NO: only ever queried as an exact-match filter, never displayed.
                add(StringField("type", getFileType(path.toFile()).name.lowercase(), Field.Store.NO))
            }
        }
        indexWriter.addDocument(document)

        val count = totalIndexed.incrementAndGet()
        if (count % progressUpdateInterval == 0) {
            Logger.info("Indexed $count items...")
            _indexedCount.value = count
        }
    }

    /**
     * System directories to skip. The Windows and Program Files paths are resolved
     * from environment variables rather than hardcoded, since they can be relocated
     * (unattended installs, corporate imaging) and Windows-on-ARM adds a third
     * "Program Files (Arm)" folder that a fixed literal list would miss.
     *
     * Matching is by exact path prefix ([Path.startsWith], which NIO's Windows
     * provider already compares case-insensitively) or exact folder name for the
     * per-drive reserved folders, not a substring search over the whole path -
     * a substring check would also (wrongly) skip an unrelated folder that merely
     * contains one of these words, e.g. "D:\ProgramFilesBackup".
     */
    private val restrictedRoots: List<Path> = listOfNotNull(
        System.getenv("SystemRoot"),
        System.getenv("windir"),
        System.getenv("ProgramFiles"),
        System.getenv("ProgramFiles(x86)"),
        System.getenv("ProgramFiles(Arm)"),
    ).mapNotNull { runCatching { Paths.get(it) }.getOrNull() }.distinct()

    private val restrictedNames = setOf("\$recycle.bin", "system volume information")

    private fun isRestrictedDirectory(path: Path): Boolean {
        val name = path.fileName?.toString()?.lowercase()
        if (name != null && name in restrictedNames) return true
        return restrictedRoots.any { path.startsWith(it) }
    }

    /**
     * Swaps [newIndexPath] into [indexPath], moving the previous index aside first rather
     * than deleting it in place - [deleteDirectory] deletes file-by-file and only reports
     * success once every file is gone, so a single locked/undeletable file (e.g. another
     * process briefly holding a segment file open) used to leave the old index partially
     * deleted instead of intact. Moving it aside is a single directory operation with no
     * partial-failure window, and is restored if anything after that fails.
     */
    private fun replaceOldIndexWithNew(newIndexPath: Path) {
        val backupPath = indexPath.resolveSibling("${indexPath.fileName}_old")
        var movedOldAside = false
        try {
            Logger.info("Finalizing index creation...")
            indexDirectory.close()

            if (Files.exists(backupPath)) {
                check(deleteDirectory(backupPath.toFile())) { "Failed to clear stale backup directory $backupPath" }
            }

            if (Files.exists(indexPath)) {
                Logger.info("Moving previous index aside: ${indexPath.toAbsolutePath()}")
                Files.move(indexPath, backupPath)
                movedOldAside = true
            }

            Logger.info("Moving new index into ${indexPath.toAbsolutePath()}")
            moveDirectory(newIndexPath, indexPath)
            runCatching { deleteDirectory(backupPath.toFile()) }
                .onFailure { Logger.warn("Could not delete backup index directory: ${it.message}") }
            Logger.info("Index replacement completed.")
        } catch (e: Exception) {
            Logger.error("Error finalizing index creation", e)
            _lastError.value = "Failed to finalize the new index: ${e.message ?: e::class.simpleName}"

            if (movedOldAside) {
                Logger.warn("Restoring the previous index after a failed replacement.")
                runCatching {
                    if (Files.exists(indexPath)) deleteDirectory(indexPath.toFile())
                    Files.move(backupPath, indexPath)
                }.onFailure { restoreError -> Logger.error("Failed to restore the previous index", restoreError) }
            }
        } finally {
            indexDirectory = runCatching { FSDirectory.open(indexPath) }.getOrDefault(indexDirectory)
            cleanUpTemporaryDirectory(newIndexPath)
        }
    }

    /**
     * Renames [source] to [target] in one filesystem operation when possible - both paths
     * are already on the same volume in the common case (temp dir and index dir are both
     * under [AppPaths.root]), so this avoids a full byte-for-byte copy of the index.
     * Falls back to a file-by-file copy for the rare case where they're on different
     * filesystems, where a directory rename isn't possible.
     */
    internal fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target)
            Logger.info("Renamed index directory in place.")
        } catch (e: IOException) {
            Logger.info("Directory rename unavailable (${e::class.simpleName}); copying files individually.")
            copyDirectory(source, target)
        }
    }

    private fun cleanUpTemporaryDirectory(tempDir: Path) {
        try {
            // Lucene's directory implementation can keep file handles open on Windows
            // until any mapped buffers are garbage-collected, so nudge the GC first.
            System.gc()
            Thread.sleep(100)

            if (deleteDirectory(tempDir.toFile())) {
                Logger.info("Temporary directory cleaned up: $tempDir")
            } else {
                Logger.warn("Could not delete temporary directory immediately: $tempDir")
                tempDir.toFile().deleteOnExit()
            }
        } catch (e: Exception) {
            Logger.warn("Error cleaning up temporary directory: ${e.message}")
            tempDir.toFile().deleteOnExit()
        }
    }

    private fun copyDirectory(source: Path, target: Path) {
        try {
            Files.walk(source).use { paths ->
                paths.forEach { file ->
                    val destination = target.resolve(source.relativize(file))
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(destination)
                    } else {
                        Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to copy directory from $source to $target: ${e.message}", e)
        }
    }

    private fun deleteDirectory(directory: File): Boolean =
        directory.walkBottomUp().map { it.delete() }.toList().all { it }
}
