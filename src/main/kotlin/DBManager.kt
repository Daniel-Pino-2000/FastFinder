
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
import kotlin.concurrent.thread
import kotlin.io.AccessDeniedException

class DBManager(private val indexDirectoryName: String = "database") {
    private val analyzer = StandardAnalyzer()
    private var totalIndexed = 0
    val skippedPaths = mutableListOf<String>()
    private val indexPath: Path
    private val indexDirectoryPath: File = File("database/new_index_2025-01-08")
    private val indexDirectory = FSDirectory.open(indexDirectoryPath.toPath())
    private var indexWriter: IndexWriter? = null

    init {
        val currentDirectory = System.getProperty("user.dir")
        indexPath = Paths.get(currentDirectory, indexDirectoryName)

        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }
    }

    /**
     * Get the shared IndexWriter instance.
     */
    fun getIndexWriter(): IndexWriter {
        if (indexWriter == null) {
            val config = IndexWriterConfig()
            indexWriter = IndexWriter(indexDirectory, config)
        }
        return indexWriter!!
    }

    /**
     * Commit and close the IndexWriter (if needed).
     */
    fun closeWriter() {
        indexWriter?.close()
        indexWriter = null
    }

    fun indexExists(): Boolean {
        return try {
            DirectoryReader.open(indexDirectory)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun createOrUpdateIndex(forceIndexCreation: Boolean = false) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val lastModifiedDate = getLastModifiedDate()

        // If forceIndexCreation is true, or the index doesn't exist or needs an update
        if (forceIndexCreation || !indexExists() || lastModifiedDate != today) {
            // Perform indexing in a separate thread to allow searching in the old index.
            thread(start = true) {
                val newIndexPath = indexPath.resolve("new_index_$today")
                val newIndexDir = FSDirectory.open(newIndexPath)

                val indexWriterConfig = IndexWriterConfig(analyzer)
                IndexWriter(newIndexDir, indexWriterConfig).use { writer ->
                    indexFilesAndDirectories(writer)
                    writer.commit()
                }

                println("\nIndexing completed. Total items indexed: $totalIndexed")
                replaceOldIndexWithNew(newIndexPath)
            }

            println("Indexing process started in the background.")
            println("You can search in the old index while the new one is being created.")

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
            if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.HOURS)) {
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
        // Rename the old index if exists
        val oldIndexPath = indexPath.resolve("old_index")
        val oldIndexDir = oldIndexPath.toFile()
        if (oldIndexDir.exists()) {
            oldIndexDir.deleteRecursively()
        }

        // Rename new index to default index
        val newIndexDir = newIndexPath.toFile()
        newIndexDir.renameTo(indexPath.toFile())
        println("Old index replaced with the new one.")
    }
}

