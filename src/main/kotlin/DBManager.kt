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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    var isFirstIndexCreation = true  // Flag to track if it's the first index creation

    init {
        val currentDirectory = System.getProperty("user.dir")
        indexPath = Paths.get(currentDirectory, indexDirectoryName)

        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }
        indexDirectory = FSDirectory.open(indexPath)
    }

    /**
     * Get the shared IndexWriter instance (thread-safe).
     */
    fun getIndexWriter(): IndexWriter {
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

    /**
     * Commit and close the IndexWriter (thread-safe).
     */
    fun closeWriter() {
        lock.lock()
        try {
            indexWriter?.close()
            indexWriter = null
        } finally {
            lock.unlock()
        }
    }

    fun indexExists(): Boolean {
        return try {
            DirectoryReader.open(indexDirectory).use { true }
        } catch (e: IOException) {
            false
        }
    }

    fun createOrUpdateIndex(forceIndexCreation: Boolean = false) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val lastModifiedDate = getLastModifiedDate()

        if (forceIndexCreation || !indexExists() || lastModifiedDate != today) {
            thread(start = true) {
                lock.lock()
                try {
                    val newIndexPath = indexPath.resolve("new_index_$today")
                    FSDirectory.open(newIndexPath).use { newIndexDir ->
                        val indexWriterConfig = IndexWriterConfig(analyzer)
                        IndexWriter(newIndexDir, indexWriterConfig).use { writer ->
                            indexFilesAndDirectories(writer)
                            writer.commit()
                        }
                    }
                    println("\nIndexing completed. Total items indexed: $totalIndexed")
                    replaceOldIndexWithNew(newIndexPath)

                    // After the first index creation, set the flag to false
                    if (isFirstIndexCreation) {
                        isFirstIndexCreation = false
                    }
                } catch (e: Exception) {
                    println("Error during indexing: ${e.message}")
                } finally {
                    lock.unlock()
                }
            }
            println("Indexing process started in the background.")
        } else {
            println("Index already exists and was created today, skipping indexing.")
        }
    }

    private fun getLastModifiedDate(): String {
        val directoryFile = indexPath.toFile()
        return if (directoryFile.exists()) {
            val lastModifiedMillis = directoryFile.lastModified()
            val lastModifiedDate = LocalDate.ofEpochDay(lastModifiedMillis / (24 * 60 * 60 * 1000))
            lastModifiedDate.format(DateTimeFormatter.ISO_DATE)
        } else {
            ""
        }
    }

    private fun indexFilesAndDirectories(indexWriter: IndexWriter) {
        val roots = File.listRoots()
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
        lock.lock()
        try {
            // Close the current writer and release resources
            closeWriter()

            // Delete the existing index directory
            val oldIndexDir = indexPath.toFile()
            if (oldIndexDir.exists()) {
                oldIndexDir.deleteRecursively()
            }

            // Rename the new index directory to the default index directory
            val newIndexDir = newIndexPath.toFile()
            if (newIndexDir.renameTo(indexPath.toFile())) {
                println("Old index successfully replaced with the new one.")
            } else {
                throw IOException("Failed to replace the old index with the new one.")
            }

            // Reinitialize the FSDirectory with the new index path
            indexDirectory.close()
            indexWriter = null
            FSDirectory.open(indexPath).use { updatedDir ->
                indexDirectory = updatedDir
            }

            // Reset the IndexWriter to point to the new database
            getIndexWriter()
        } catch (e: Exception) {
            println("Error replacing old index with new: ${e.message}")
            throw e
        } finally {
            lock.unlock()
        }
    }
}
