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

    init {
        val currentDirectory = System.getProperty("user.dir")
        indexPath = Paths.get(currentDirectory, indexDirectoryName)
        stateFilePath = indexPath.resolve("index_state.txt")

        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }

        indexDirectory = FSDirectory.open(indexPath)
        isFirstIndexCreation = readIsFirstIndexCreation()
        println("Is first index creation: $isFirstIndexCreation")
    }

    private fun readIsFirstIndexCreation(): Boolean {
        return if (Files.exists(stateFilePath)) {
            Files.readAllLines(stateFilePath).firstOrNull()?.toBoolean() ?: true
        } else {
            true
        }
    }

    private fun saveIsFirstIndexCreation() {
        Files.write(stateFilePath, listOf(isFirstIndexCreation.toString()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
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

    fun createOrUpdateIndex(forceIndexCreation: Boolean = false) {
        println("Checking if index exists before updating or creating in the background: ${indexExists()}")
        if (forceIndexCreation || isFirstIndexCreation || !indexExists()) {
            thread(start = true) {
                lock.lock()
                try {
                    val newIndexPath = indexPath.resolve("new_index")
                    FSDirectory.open(newIndexPath).use { newIndexDir ->
                        val indexWriterConfig = IndexWriterConfig(analyzer)
                        IndexWriter(newIndexDir, indexWriterConfig).use { writer ->
                            indexFilesAndDirectories(writer)
                            writer.commit()
                        }
                    }
                    println("\nIndexing completed. Total items indexed: $totalIndexed")
                    replaceOldIndexWithNew(newIndexPath)

                    if (isFirstIndexCreation) {
                        println("Setting isFirstIndexCreation to false.")
                        isFirstIndexCreation = false
                        saveIsFirstIndexCreation()
                    }
                } catch (e: Exception) {
                    println("Error during indexing: ${e.message}")
                } finally {
                    lock.unlock()
                }
            }
            println("Indexing process started in the background.")
        } else {
            println("Index already exists. Skipping indexing.")
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
            closeWriter()
            val oldIndexDir = indexPath.toFile()
            if (oldIndexDir.exists()) {
                oldIndexDir.deleteRecursively()
            }

            val newIndexDir = newIndexPath.toFile()
            if (newIndexDir.renameTo(indexPath.toFile())) {
                println("Old index successfully replaced with the new one.")
            } else {
                throw IOException("Failed to replace the old index with the new one.")
            }

            indexDirectory.close()
            indexWriter = null
            FSDirectory.open(indexPath).use { updatedDir ->
                indexDirectory = updatedDir
            }
            getIndexWriter()
        } catch (e: Exception) {
            println("Error replacing old index with new: ${e.message}")
            throw e
        } finally {
            lock.unlock()
        }
    }
}
