import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.io.AccessDeniedException

class DBManager(indexDirectoryName: String = "database") {
    private val analyzer = StandardAnalyzer()
    private var totalIndexed = 0
    val skippedPaths = mutableListOf<String>()
    val indexPath: Path
    private var indexDirectory: FSDirectory
    private var indexWriter: IndexWriter? = null
    private val lock = ReentrantLock()
    private val stateFilePath: Path

    // Flag to track if it's the first index creation
    var isFirstIndexCreation: Boolean
    var isDeletingFile: Boolean = false

    val isIndexing = AtomicBoolean(false)

    init {
        val currentDirectory = System.getProperty("user.dir")
        indexPath = Paths.get(currentDirectory, indexDirectoryName)
        stateFilePath = indexPath.resolve("index_state.txt")

        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }

        indexDirectory = FSDirectory.open(indexPath)
        isFirstIndexCreation = readStateFile()
        println("Is first index creation: $isFirstIndexCreation")
        println("IndexPath: $indexPath")
    }

    // Loads the configurations of the search.
    private fun readStateFile(): Boolean {
        return if (Files.exists(stateFilePath)) {
            try {
                val lines = Files.readAllLines(stateFilePath)
                lines.getOrNull(0)?.toBoolean() ?: true
            } catch (e: IOException) {
                println("Error reading state file: ${e.message}")
                true
            }
        } else {
            true
        }
    }

    // Saves the configurations of the search.
    private fun writeStateFile() {
        try {
            Files.write(stateFilePath, listOf(isFirstIndexCreation.toString(), indexPath.toString()),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        } catch (e: IOException) {
            println("Error writing state file: ${e.message}")
        }
    }


    private fun getIndexWriter(): IndexWriter {
        lock.lock()
        try {
            if (indexWriter == null) {
                val config = IndexWriterConfig(analyzer)
                config.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
                indexWriter = IndexWriter(indexDirectory, config)
            }
            return indexWriter!!
        } finally {
            lock.unlock()
        }
    }

    private fun closeWriter() {
        lock.lock()
        try {
            indexWriter?.close()
            indexWriter = null
        } finally {
            lock.unlock()
        }
    }

    private fun indexExists(): Boolean {
        return try {
            DirectoryReader.open(indexDirectory).use { true }
        } catch (e: IOException) {
            false
        }
    }

    // Creates or updates the index, depending on the current state
    fun createOrUpdateIndex(forceIndexCreation: Boolean = false) {
        println("Checking if index exists before updating or creating: ${indexExists()}")

        // Prevent multiple indexing processes from running simultaneously
        if (isIndexing.get()) {
            println("Indexing is already in progress. Skipping this request.")
            return
        }

        // Decide whether to proceed with creating or updating the index
        if (forceIndexCreation || isFirstIndexCreation || !indexExists()) {
            isIndexing.set(true)
            var tempDirectory: Path? = null
            thread(start = true) {
                lock.lock()
                try {
                    println("Starting indexing process...")

                    // Create a temporary directory for the new index
                    tempDirectory = Files.createTempDirectory("new_index_")
                    val newIndexPath = tempDirectory!!.toAbsolutePath()
                    println("Temporary new index directory: $newIndexPath")

                    // Open the temporary directory as a new Lucene index
                    FSDirectory.open(newIndexPath).use { newIndexDir ->
                        val indexWriterConfig = IndexWriterConfig(analyzer)
                        IndexWriter(newIndexDir, indexWriterConfig).use { writer ->
                            // Index files and directories
                            indexFilesAndDirectories(writer)
                            writer.commit()
                        }
                    }

                    println("\nIndexing completed. Total items indexed: $totalIndexed")
                    // Replace the old index with the new one
                    replaceOldIndexWithNew(newIndexPath)
                    isDeletingFile = false

                    // Update the state to indicate that the first index creation is complete
                    if (isFirstIndexCreation) {
                        println("Setting isFirstIndexCreation to false.")
                        isFirstIndexCreation = false
                        isIndexing.set(false)
                        writeStateFile()
                    }
                } catch (e: Exception) {
                    println("Error during indexing: ${e.message}")
                    tempDirectory?.let { tempDir ->
                        try {
                            System.gc()
                            Thread.sleep(100)
                            Files.walk(tempDir)
                                .sorted(Comparator.reverseOrder())
                                .forEach { Files.delete(it) }
                            println("Cleaned up temporary directory after failure")
                        } catch (cleanupEx: Exception) {
                            println("Warning: Could not clean up temporary directory: ${cleanupEx.message}")
                            tempDir.toFile().deleteOnExit()
                        }
                    }
                } finally {
                    writeStateFile()
                    isIndexing.set(false)
                    lock.unlock()
                }
            }
            println("Indexing process started in the background.")
        } else {
            println("Index already exists. Skipping indexing.")
        }
    }

    private fun indexFilesAndDirectories(indexWriter: IndexWriter) {
        val roots = File.listRoots().toList()
        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        // Use a thread-safe map to store directory sizes
        val directorySizes = ConcurrentHashMap<Path, AtomicLong>()

        roots.forEach { root ->
            executor.submit {
                val rootPath = root.toPath()
                println("Walking directory tree from: ${rootPath.toAbsolutePath()}")

                try {
                    Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            try {
                                val fileSize = attrs.size()
                                addToIndex(file, indexWriter, true, fileSize)

                                // Update parent directory sizes atomically
                                var currentDir = file.parent
                                while (currentDir != null) {
                                    directorySizes.computeIfAbsent(currentDir) { AtomicLong(0) }.addAndGet(fileSize)
                                    currentDir = currentDir.parent
                                }
                            } catch (e: AccessDeniedException) {
                                skippedPaths.add("File: $file (Access Denied)")
                            } catch (e: Exception) {
                                skippedPaths.add("File: $file (${e.message})")
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            if (isRestrictedDirectory(dir)) {
                                skippedPaths.add("Directory: $dir (Restricted)")
                                return FileVisitResult.SKIP_SUBTREE
                            }

                            try {
                                // Initialize directory size to 0 atomically
                                directorySizes.putIfAbsent(dir, AtomicLong(0))
                            } catch (e: AccessDeniedException) {
                                skippedPaths.add("Directory: $dir (Access Denied)")
                                return FileVisitResult.SKIP_SUBTREE
                            } catch (e: Exception) {
                                skippedPaths.add("Directory: $dir (${e.message})")
                                return FileVisitResult.SKIP_SUBTREE
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                            if (exc != null) {
                                skippedPaths.add("Directory: $dir (${exc.message})")
                                return FileVisitResult.CONTINUE
                            }

                            // Add the directory to the index with its accumulated size
                            val dirSize = directorySizes[dir]?.get() ?: 0L
                            addToIndex(dir, indexWriter, false, dirSize)
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                            skippedPaths.add("Failed to access: $file (${exc.message})")
                            return FileVisitResult.CONTINUE
                        }
                    })
                } catch (e: Exception) {
                    println("Error walking through the root directory $root: ${e.message}")
                }
            }
        }

        executor.shutdown()
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                println("Timeout waiting for indexing tasks to complete.")
            }
        } catch (e: InterruptedException) {
            println("Thread interrupted: ${e.message}")
        }
    }


    private fun addToIndex(path: Path, indexWriter: IndexWriter, isFile: Boolean, size: Long) {
        val document = Document()
        val fullFileName = path.fileName?.toString() ?: return
        document.add(TextField("nameOriginal", fullFileName, Field.Store.YES))
        document.add(TextField("name", fullFileName.lowercase(), Field.Store.YES))
        val parentPath = path.parent?.toString() ?: ""
        document.add(StringField("parent", parentPath, Field.Store.YES))
        document.add(StringField("path", path.toAbsolutePath().toString(), Field.Store.YES))
        document.add(StringField("isFile", isFile.toString(), Field.Store.YES))
        document.add(LongPoint("size", size))
        document.add(TextField("sizeDisplay", size.toString(), Field.Store.YES))

        indexWriter.addDocument(document)
        totalIndexed++

        if (totalIndexed % 1000 == 0) {
            println("Indexed $totalIndexed items...")
        }
    }


    private fun isRestrictedDirectory(path: Path): Boolean {
        val restrictedDirs = setOf(
            "\$Recycle.Bin",
            "Windows",
            "Program Files",
            "Program Files (x86)",
            "System Volume Information"
        )

        val pathStr = path.toString().lowercase()
        return restrictedDirs.any { restricted -> pathStr.contains(restricted.lowercase()) }
    }


    private fun replaceOldIndexWithNew(newIndexPath: Path) {
        isDeletingFile = true
        lock.lock()
        try {
            closeWriter()
            println("Finalizing index creation...")

            val oldIndexDir = indexPath.toFile()
            val tempDir = newIndexPath.toFile()

            try {
                if (!oldIndexDir.exists() || isFirstIndexCreation) {
                    println("No existing index found or first-time index creation. Copying new index to the main directory.")
                    copyDirectory(newIndexPath, indexPath)
                } else {
                    println("Deleting old index directory: ${oldIndexDir.absolutePath}")
                    if (!deleteDirectory(oldIndexDir)) {
                        throw IOException("Failed to delete the old index directory.")
                    }

                    println("Copying new index directory: ${newIndexPath.toAbsolutePath()} to ${indexPath.toAbsolutePath()}")
                    copyDirectory(newIndexPath, indexPath)
                }

                indexDirectory.close()
                indexWriter = null
                indexDirectory = FSDirectory.open(indexPath)
                getIndexWriter()

                println("Index replacement completed.")
            } finally {
                println("Cleaning up temporary directory: ${tempDir.absolutePath}")
                try {
                    System.gc()
                    Thread.sleep(100)

                    if (deleteDirectory(tempDir)) {
                        println("Temporary directory successfully deleted")
                    } else {
                        println("Warning: Could not delete temporary directory immediately")
                        tempDir.deleteOnExit()
                    }
                } catch (e: Exception) {
                    println("Warning: Error while cleaning up temporary directory: ${e.message}")
                    tempDir.deleteOnExit()
                }
            }
        } catch (e: Exception) {
            println("Error finalizing index creation: ${e.message}")
        } finally {
            lock.unlock()
        }
    }

    private fun copyDirectory(source: Path, target: Path) {
        try {
            Files.walk(source).forEach { file ->
                val destination = target.resolve(source.relativize(file))
                if (Files.isDirectory(file)) {
                    if (!Files.exists(destination)) Files.createDirectories(destination)
                } else {
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            println("Directory successfully copied from $source to $target.")
        } catch (e: IOException) {
            throw IOException("Failed to copy directory from $source to $target: ${e.message}")
        }
    }

    private fun deleteDirectory(directory: File): Boolean {
        return directory.walkBottomUp().all { it.delete() }
    }
}