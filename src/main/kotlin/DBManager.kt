import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.io.AccessDeniedException

class DBManager(private val indexDirectoryName: String = "database") {
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

    private fun writeStateFile() {
        try {
            // Save the state of index creation along with the index path
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

    // You might also want to update the createOrUpdateIndex method to ensure cleanup in case of early failure
    fun createOrUpdateIndex(forceIndexCreation: Boolean = false) {
        println("Checking if index exists before updating or creating: ${indexExists()}")

        // Skip if indexing is already in progress
        if (isIndexing.get()) {
            println("Indexing is already in progress. Skipping this request.")
            return
        }

        if (forceIndexCreation || isFirstIndexCreation || !indexExists()) {
            isIndexing.set(true)
            var tempDirectory: Path? = null
            thread(start = true) {
                lock.lock()
                try {
                    println("Starting indexing process...")

                    tempDirectory = Files.createTempDirectory("new_index_")
                    val newIndexPath = tempDirectory!!.toAbsolutePath()
                    println("Temporary new index directory: $newIndexPath")

                    FSDirectory.open(newIndexPath).use { newIndexDir ->
                        val indexWriterConfig = IndexWriterConfig(analyzer)
                        IndexWriter(newIndexDir, indexWriterConfig).use { writer ->
                            indexFilesAndDirectories(writer)
                            writer.commit()
                        }
                    }

                    println("\nIndexing completed. Total items indexed: $totalIndexed")
                    replaceOldIndexWithNew(newIndexPath)
                    isDeletingFile = false

                    if (isFirstIndexCreation) {
                        println("Setting isFirstIndexCreation to false.")
                        isFirstIndexCreation = false
                        writeStateFile()
                    }
                } catch (e: Exception) {
                    println("Error during indexing: ${e.message}")
                    // Clean up temporary directory in case of failure
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
        //val roots = File.listRoots()
        val roots = listOf(File("C:\\"))

        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        roots.forEach { root ->
            executor.submit {
                val rootPath = root.toPath()
                println("Walking directory tree from: ${rootPath.toAbsolutePath()}")

                try {
                    Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            try {
                                addToIndex(file, indexWriter, true)
                            } catch (e: AccessDeniedException) {
                                skippedPaths.add("File: ${file.toString()} (Access Denied)")
                            } catch (e: Exception) {
                                skippedPaths.add("File: ${file.toString()} (${e.message})")
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            if (isRestrictedDirectory(dir)) {
                                skippedPaths.add("Directory: ${dir.toString()} (Restricted)")
                                return FileVisitResult.SKIP_SUBTREE
                            }

                            try {
                                addToIndex(dir, indexWriter, false)
                            } catch (e: AccessDeniedException) {
                                skippedPaths.add("Directory: ${dir.toString()} (Access Denied)")
                                return FileVisitResult.SKIP_SUBTREE
                            } catch (e: Exception) {
                                skippedPaths.add("Directory: ${dir.toString()} (${e.message})")
                                return FileVisitResult.SKIP_SUBTREE
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                            skippedPaths.add("Failed to access: ${file.toString()} (${exc.message})")
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

    private fun addToIndex(path: Path, indexWriter: IndexWriter, isFile: Boolean) {
        val document = Document()
        val fullFileName = path.fileName?.toString() ?: return
        document.add(TextField("nameOriginal", fullFileName, Field.Store.YES))
        document.add(TextField("name", fullFileName.lowercase(), Field.Store.YES))
        val parentPath = path.parent?.toString() ?: ""
        document.add(StringField("parent", parentPath, Field.Store.YES))
        document.add(StringField("path", path.toAbsolutePath().toString(), Field.Store.YES))
        document.add(StringField("isFile", isFile.toString(), Field.Store.YES))

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
            closeWriter() // Ensure no writer is active
            println("Finalizing index creation...")

            val oldIndexDir = indexPath.toFile()
            val tempDir = newIndexPath.toFile()

            try {
                if (!oldIndexDir.exists() || isFirstIndexCreation) {
                    // No existing database or first-time creation
                    println("No existing index found or first-time index creation. Copying new index to the main directory.")
                    copyDirectory(newIndexPath, indexPath)
                } else {
                    // Handle replacing old index
                    println("Deleting old index directory: ${oldIndexDir.absolutePath}")
                    if (!deleteDirectory(oldIndexDir)) {
                        throw IOException("Failed to delete the old index directory.")
                    }

                    println("Copying new index directory: ${newIndexPath.toAbsolutePath()} to ${indexPath.toAbsolutePath()}")
                    copyDirectory(newIndexPath, indexPath)
                }

                // Reinitialize the index directory for future use
                indexDirectory.close()
                indexWriter = null
                indexDirectory = FSDirectory.open(indexPath)
                getIndexWriter()

                println("Index replacement completed.")
            } finally {
                // Clean up temporary directory
                println("Cleaning up temporary directory: ${tempDir.absolutePath}")
                try {
                    // First ensure all file handles are closed by forcing a GC
                    System.gc()
                    Thread.sleep(100) // Give the system a moment to release resources

                    if (deleteDirectory(tempDir)) {
                        println("Temporary directory successfully deleted")
                    } else {
                        println("Warning: Could not delete temporary directory immediately")
                        // Schedule deletion for JVM exit as a fallback
                        tempDir.deleteOnExit()
                    }
                } catch (e: Exception) {
                    println("Warning: Error while cleaning up temporary directory: ${e.message}")
                    // Schedule deletion for JVM exit as a fallback
                    tempDir.deleteOnExit()
                }
            }
        } catch (e: Exception) {
            println("Error finalizing index creation: ${e.message}")
        } finally {
            lock.unlock()
        }
    }




    /**
     * Copies the contents of a directory recursively.
     */
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

    /**
     * Deletes a directory and its contents recursively.
     */
    private fun deleteDirectory(directory: File): Boolean {
        return directory.walkBottomUp().all { it.delete() }
    }
}
