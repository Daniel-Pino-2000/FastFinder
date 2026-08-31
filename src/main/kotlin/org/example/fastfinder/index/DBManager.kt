package org.example.fastfinder.index

import com.sun.nio.file.ExtendedWatchEventModifier
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
import org.apache.lucene.index.Term
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.AlreadyClosedException
import org.apache.lucene.store.FSDirectory
import org.example.fastfinder.util.AppPaths
import org.example.fastfinder.util.Logger
import org.example.fastfinder.util.getFileType
import java.io.File
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.DirectoryIteratorException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.io.AccessDeniedException

/** How often the watcher commits batched filesystem changes to the live index. */
private const val WATCHER_COMMIT_INTERVAL_MS = 2000L

/** How long [DBManager.close] waits for the watcher's background thread to stop. */
private const val WATCHER_SHUTDOWN_TIMEOUT_MS = 2000L

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

    // Lazy: a DBManager that never actually runs createOrUpdateIndex() (e.g. most of the
    // indexing tests, which call indexFilesAndDirectories() directly) should never pay for a
    // WatchService or background thread it will never use.
    private val watcherLazy = lazy { IndexWatcher() }
    private val watcher: IndexWatcher get() = watcherLazy.value

    /** Releases the filesystem watcher's background thread and native watch handles, if started. */
    fun close() {
        if (watcherLazy.isInitialized()) watcher.close()
    }

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
            // A full rebuild is being skipped, but live updates still need to be watched -
            // this is what attaches the watcher on every normal app startup, since startup
            // almost always takes this skip-the-rebuild path rather than replaceOldIndexWithNew.
            watcher.attachTo(indexPath, roots)
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
                replaceOldIndexWithNew(newIndexPath, roots)
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
    internal fun indexFilesAndDirectories(
        indexWriter: IndexWriter,
        roots: List<File> = File.listRoots().toList(),
        parallelism: Int = Runtime.getRuntime().availableProcessors(),
    ) {
        val pool = ForkJoinPool(parallelism)
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
    private fun replaceOldIndexWithNew(newIndexPath: Path, roots: List<File>) {
        val backupPath = indexPath.resolveSibling("${indexPath.fileName}_old")
        var movedOldAside = false
        try {
            Logger.info("Finalizing index creation...")
            // The watcher's IndexWriter holds open file handles inside indexPath - detach it
            // before moving that directory aside, and any changes it queued are moot anyway
            // since the fresh index below already reflects current filesystem state.
            watcher.detach()
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
            // Re-attach against whichever index directory now lives at indexPath - the new
            // one on success, or the restored previous one if the replacement above failed.
            watcher.attachTo(indexPath, roots)
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

    /**
     * Keeps the live index in sync with the filesystem between full rebuilds, so a file
     * created/edited/deleted outside FastFinder shows up in search without waiting for the
     * next "Update Database".
     *
     * Uses a single [java.nio.file.WatchService] per registered root with Windows'
     * [ExtendedWatchEventModifier.FILE_TREE] modifier, which asks the OS to watch an entire
     * subtree through one native handle (`ReadDirectoryChangesW` with `bWatchSubtree=TRUE`)
     * instead of registering a handle per directory - indexing a whole drive can mean
     * hundreds of thousands of directories, and one handle each would be both slow to set up
     * and a large, unnecessary resource footprint.
     *
     * Changes are batched: events are applied to an in-memory [IndexWriter] as they arrive,
     * but committed at most once per [WATCHER_COMMIT_INTERVAL_MS], since a Lucene commit is
     * comparatively expensive and a burst of filesystem activity (e.g. extracting an archive)
     * would otherwise trigger one per file.
     *
     * Deliberately out of scope: an ancestor directory's aggregated subtree size (see
     * [indexFilesAndDirectories]) is not incrementally recomputed here - it stays stale until
     * the next full rebuild, the same documented trade-off already made for directory sizes
     * in general.
     */
    private inner class IndexWatcher : AutoCloseable {
        private val watchService = FileSystems.getDefault().newWatchService()
        private val registeredRoots = mutableMapOf<Path, WatchKey>()
        private val writerLock = Any()
        private val running = AtomicBoolean(true)

        @Volatile
        private var writer: IndexWriter? = null
        private var thread: Thread? = null

        /** (Re)attaches to the live index at [indexPath] and ensures [roots] are being watched. */
        fun attachTo(indexPath: Path, roots: List<File>) {
            synchronized(writerLock) {
                writer = try {
                    IndexWriter(FSDirectory.open(indexPath), IndexWriterConfig(analyzer))
                } catch (e: IOException) {
                    Logger.error("Could not attach the filesystem watcher to the index", e)
                    null
                }
            }

            roots.forEach { root ->
                val path = root.toPath()
                if (path in registeredRoots) return@forEach
                try {
                    val key = path.register(
                        watchService,
                        arrayOf(
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                        ),
                        ExtendedWatchEventModifier.FILE_TREE,
                    )
                    registeredRoots[path] = key
                } catch (e: IOException) {
                    Logger.warn("Could not watch $path for live updates: ${e.message}")
                }
            }

            if (thread == null) {
                thread = kotlin.concurrent.thread(name = "fastfinder-watcher", isDaemon = true) { processLoop() }
            }
        }

        /** Stops writing to the index (e.g. while it's about to be moved aside) without stopping the watch itself. */
        fun detach() {
            synchronized(writerLock) {
                writer?.let { w ->
                    runCatching { w.close() }
                        .onFailure { Logger.warn("Error closing watcher's index writer: ${it.message}") }
                }
                writer = null
            }
        }

        override fun close() {
            running.set(false)
            runCatching { watchService.close() }
            detach()
            registeredRoots.values.forEach { it.cancel() }
            registeredRoots.clear()
            thread?.let { t ->
                t.interrupt()
                t.join(WATCHER_SHUTDOWN_TIMEOUT_MS)
                if (t.isAlive) Logger.warn("Filesystem watcher thread did not stop within the shutdown timeout.")
            }
            thread = null
        }

        private fun processLoop() {
            // Local rather than a member function: it's only ever called from here, and
            // keeping it local avoids IndexWatcher's function count tipping over detekt's
            // per-class threshold for what is genuinely a one-line, single-use helper.
            fun commit() {
                try {
                    synchronized(writerLock) { writer }?.commit()
                } catch (e: IOException) {
                    Logger.warn("Error committing filesystem watcher changes: ${e.message}")
                }
            }

            var dirty = false
            var lastCommit = System.currentTimeMillis()
            while (running.get()) {
                // Either exception below is a signal to stop, not to retry - close()/interrupt
                // are the only things that ever cause one.
                val key = try {
                    watchService.poll(WATCHER_COMMIT_INTERVAL_MS, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Logger.info("Filesystem watcher thread interrupted; stopping: ${e.message}")
                    running.set(false)
                    null
                } catch (e: ClosedWatchServiceException) {
                    Logger.info("Filesystem watcher service closed; stopping: ${e.message}")
                    running.set(false)
                    null
                }
                if (key != null) dirty = applyPendingEvents(key) || dirty

                val now = System.currentTimeMillis()
                if (dirty && now - lastCommit >= WATCHER_COMMIT_INTERVAL_MS) {
                    commit()
                    dirty = false
                    lastCommit = now
                }
            }
            if (dirty) commit()
        }

        private fun applyPendingEvents(key: WatchKey): Boolean {
            var sawEvent = false
            for (event in key.pollEvents()) {
                if (applyEventSafely(key, event)) sawEvent = true
            }
            if (!key.reset()) registeredRoots.values.remove(key)
            return sawEvent
        }

        private fun applyEventSafely(key: WatchKey, event: WatchEvent<*>): Boolean {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                Logger.warn("Filesystem watcher overflowed; some changes may be missed until the next full rebuild.")
                return false
            }

            // Registration only ever happens against a Path (attachTo above), so the cast is safe.
            val watchedRoot = key.watchable() as Path
            @Suppress("UNCHECKED_CAST")
            val relativePath = (event as WatchEvent<Path>).context()

            return try {
                applyEvent(watchedRoot.resolve(relativePath), event.kind())
                true
            } catch (e: IOException) {
                Logger.warn("Error applying a filesystem watcher event: ${e.message}")
                false
            } catch (e: AlreadyClosedException) {
                Logger.warn("Filesystem watcher event skipped: index writer was closed (${e.message})")
                false
            }
        }

        private fun applyEvent(path: Path, kind: WatchEvent.Kind<*>) {
            val currentWriter = if (isExcludedFromWatching(path)) null else synchronized(writerLock) { writer }
            if (currentWriter == null) return

            // Vanished/inaccessible by the time we look, or an outright delete event: treat
            // both the same way so the index doesn't go stale either way.
            val attrs = if (kind == StandardWatchEventKinds.ENTRY_DELETE) null else readAttributesOrNull(path)
            if (attrs == null) {
                val pathString = path.toString()
                currentWriter.deleteDocuments(
                    TermQuery(Term("path", pathString)),
                    PrefixQuery(Term("path", pathString + File.separator)),
                )
                return
            }

            if (attrs.isDirectory) {
                // A directory's own MODIFY events (e.g. a child was renamed) carry nothing
                // this schema tracks - only a genuinely new directory needs handling, since
                // it may already contain files (a paste/move-in), none of which have events
                // of their own to indicate their pre-existing content.
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    currentWriter.deleteDocuments(Term("path", path.toString()))
                    indexNewDirectory(path, currentWriter)
                }
            } else {
                currentWriter.deleteDocuments(Term("path", path.toString()))
                addToIndex(path, currentWriter, isFile = true, size = attrs.size())
            }
        }

        private fun readAttributesOrNull(path: Path): BasicFileAttributes? = try {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (@Suppress("SwallowedException") e: IOException) {
            // Expected and routine, not logged: short-lived temp/lock files (build tools,
            // browsers, installers) constantly vanish between the watcher seeing an event
            // and looking the path up moments later. Logging every occurrence would flood
            // the log under ordinary background disk activity for no actionable benefit -
            // the caller already treats a null result the same as a delete event.
            null
        }

        /** Recursively indexes a directory that just appeared, which may already have contents. */
        private fun indexNewDirectory(directory: Path, writer: IndexWriter): Long {
            if (isRestrictedDirectory(directory)) return 0L

            val entries = try {
                Files.newDirectoryStream(directory).use { it.toList() }
            } catch (e: IOException) {
                Logger.warn("Could not list newly created directory $directory: ${e.message}")
                emptyList()
            }

            val subtreeSize = entries.sumOf { entry ->
                val attrs = readAttributesOrNull(entry)
                when {
                    attrs == null -> 0L
                    attrs.isDirectory -> indexNewDirectory(entry, writer)
                    else -> attrs.size().also { addToIndex(entry, writer, isFile = true, size = it) }
                }
            }
            addToIndex(directory, writer, isFile = false, size = subtreeSize)
            return subtreeSize
        }

        /**
         * [isRestrictedDirectory] only recognizes a restricted directory by its own exact
         * name/prefix, which is enough for the indexing walk above since it never descends
         * into one in the first place. FILE_TREE watching has no such luxury - it reports
         * events arbitrarily deep inside a restricted directory - so this also walks
         * ancestors, and additionally excludes FastFinder's own data directory to avoid
         * indexing (and endlessly re-triggering on) its own index/log/preferences files.
         */
        private fun isExcludedFromWatching(path: Path): Boolean {
            if (path.startsWith(AppPaths.root)) return true
            return generateSequence(path) { it.parent }.any(::isRestrictedDirectory)
        }
    }
}
