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
import org.example.fastfinder.util.Logger
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
class DBManager(indexDirectoryName: String = "database") {
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

    init {
        indexPath = resolveAppDataDirectory().resolve(indexDirectoryName)
        stateFilePath = indexPath.resolve("index_state.txt")

        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }

        indexDirectory = FSDirectory.open(indexPath)
        isFirstIndexCreation = readStateFile()
        Logger.info("Is first index creation: $isFirstIndexCreation")
        Logger.info("Index path: $indexPath")
    }

    fun createOrUpdateIndex(forceIndexCreation: Boolean = false) {
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
                skippedPaths.clear()

                tempDirectory = Files.createTempDirectory("fastfinder_index_")
                val newIndexPath = tempDirectory.toAbsolutePath()

                FSDirectory.open(newIndexPath).use { newIndexDir ->
                    IndexWriter(newIndexDir, IndexWriterConfig(analyzer)).use { writer ->
                        indexFilesAndDirectories(writer)
                        writer.commit()
                    }
                }

                Logger.info("Indexing completed. Total items indexed: ${totalIndexed.get()}")
                replaceOldIndexWithNew(newIndexPath)
                isFirstIndexCreation = false
                writeStateFile()
            } catch (e: Exception) {
                Logger.error("Error during indexing", e)
                tempDirectory?.let(::cleanUpTemporaryDirectory)
            } finally {
                _isIndexing.value = false
                lock.unlock()
            }
        }
        Logger.info("Indexing process started in the background.")
    }

    private fun resolveAppDataDirectory(): Path {
        val appData = System.getenv("APPDATA")
        val base = if (appData != null) Paths.get(appData) else Paths.get(System.getProperty("user.home"))
        return base.resolve("FastFinder")
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
    private fun indexFilesAndDirectories(indexWriter: IndexWriter) {
        val roots = File.listRoots().toList()
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
                skippedPaths.add("Directory: $directory (Access Denied)")
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
                    skippedPaths.add("File: $entry (Access Denied)")
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
        }
        indexWriter.addDocument(document)

        val count = totalIndexed.incrementAndGet()
        if (count % 1000 == 0) Logger.info("Indexed $count items...")
    }

    private fun isRestrictedDirectory(path: Path): Boolean {
        val restrictedDirs = setOf(
            "\$Recycle.Bin", "Windows", "Program Files", "Program Files (x86)", "System Volume Information"
        )
        val pathStr = path.toString().lowercase()
        return restrictedDirs.any { pathStr.contains(it.lowercase()) }
    }

    private fun replaceOldIndexWithNew(newIndexPath: Path) {
        try {
            Logger.info("Finalizing index creation...")
            indexDirectory.close()

            val oldIndexDir = indexPath.toFile()
            if (oldIndexDir.exists() && !isFirstIndexCreation) {
                Logger.info("Deleting old index directory: ${oldIndexDir.absolutePath}")
                check(deleteDirectory(oldIndexDir)) { "Failed to delete the old index directory." }
            }

            Logger.info("Copying new index into ${indexPath.toAbsolutePath()}")
            copyDirectory(newIndexPath, indexPath)

            indexDirectory = FSDirectory.open(indexPath)
            Logger.info("Index replacement completed.")
        } catch (e: Exception) {
            Logger.error("Error finalizing index creation", e)
        } finally {
            cleanUpTemporaryDirectory(newIndexPath)
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
