import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.io.AccessDeniedException

class DBManager(private val indexDirectoryName: String = "database") {
    private val analyzer = StandardAnalyzer()
    val indexDirectory: Directory
    private var totalIndexed = 0
    val skippedPaths = mutableListOf<String>()
    private val indexPath: Path

    init {
        // Get the current working directory (where the Kotlin files are)
        val currentDirectory = System.getProperty("user.dir")
        indexPath = Paths.get(currentDirectory, indexDirectoryName)

        // Create the "database" folder if it doesn't exist
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }

        // Set the index directory path to the "database" folder
        indexDirectory = FSDirectory.open(indexPath)
    }

    /**
     * Checks if the Lucene index already exists.
     */
    fun indexExists(): Boolean {
        return try {
            DirectoryReader.open(indexDirectory)
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Creates or updates the Lucene index by indexing files and directories.
     */
    fun createOrUpdateIndex() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val lastModifiedDate = getLastModifiedDate()

        // Check if the index exists and if it was modified today
        if (!indexExists() || lastModifiedDate != today) {
            // If the index was modified before today, create a new one
            val newIndexPath = indexPath.resolve("new_index_$today")
            val newIndexDir = FSDirectory.open(newIndexPath)

            val indexWriterConfig = IndexWriterConfig(analyzer)
            IndexWriter(newIndexDir, indexWriterConfig).use { writer ->
                // Silent output during indexing process, only showing progress
                indexFilesAndDirectories(writer)
                writer.commit()
            }
            println("\nIndexing completed. Total items indexed: $totalIndexed")

            // Once new index is created, delete old index and replace with new one
            replaceOldIndexWithNew(newIndexPath)
        } else {
            println("Index already exists and was created today, skipping indexing.")
        }
    }

    /**
     * Gets the last modified date of the current index directory
     */
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

    /**
     * Recursively walks through the directory tree and indexes files and directories.
     */
    private fun indexFilesAndDirectories(indexWriter: IndexWriter) {
        val roots = File.listRoots()

        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        roots.forEach { root ->
            executor.submit {
                val rootPath = root.toPath()
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

    /**
     * Adds a single file or directory to the Lucene index.
     */
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

    /**
     * Checks if a directory should be excluded from indexing.
     */
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

    /**
     * Replaces the old index with the new one.
     */
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
    }
}
