/**
 * Imports for Apache Lucene library components used for indexing and searching
 */
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.WildcardQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import kotlin.io.AccessDeniedException

/**
 * Enum defining the different modes of search operation
 * - FILES: Search only for files
 * - DIRECTORIES: Search only for directories
 * - ALL: Search for both files and directories
 */
enum class SearchMode {
    FILES,
    DIRECTORIES,
    ALL
}

/**
 * Main search class that handles both indexing and searching of files/directories
 * Uses Apache Lucene for creating and searching the index
 *
 * @property searchValue The search string provided by the user
 * @property searchMode The type of items to search for (FILES, DIRECTORIES, or ALL)
 * @property customRootDirectory Optional custom starting directory for the search
 * @property indexDirectoryName Name of the directory where the Lucene index will be stored
 */
class Search(
    val searchValue: String,
    val searchMode: SearchMode,
    val customRootDirectory: File?,
    val indexDirectoryName: String = "database" // Folder name for the index
) {
    private val analyzer = StandardAnalyzer()
    private val indexDirectory: Directory
    private var totalIndexed = 0
    val skippedPaths = mutableListOf<String>()

    /**
     * Initializes the search by setting up the index directory
     * Creates the database directory if it doesn't exist
     */
    init {
        // Get the current working directory (where the Kotlin files are)
        val currentDirectory = System.getProperty("user.dir")
        val indexPath = Paths.get(currentDirectory, indexDirectoryName)

        // Create the "database" folder if it doesn't exist
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }

        // Set the index directory path to the "database" folder
        indexDirectory = FSDirectory.open(indexPath)
    }

    /**
     * Main function that coordinates the indexing and searching process
     * Creates an index if one doesn't exist, then performs the search
     *
     * @return List of matching SystemItems based on the search criteria
     */
    fun indexAndSearch(): List<SystemItem> {
        return try {
            println("Starting search for '$searchValue' in mode: $searchMode")
            println("Root directory: ${customRootDirectory?.absolutePath ?: System.getProperty("user.dir")}")

            // Check if the index exists and create if necessary
            if (!indexExists()) {
                val indexWriterConfig = IndexWriterConfig(analyzer)
                IndexWriter(indexDirectory, indexWriterConfig).use { writer ->
                    indexFilesAndDirectories(writer)
                    writer.commit()
                    println("\nIndexing completed. Total items indexed: $totalIndexed")
                }
            } else {
                println("Index already exists, skipping indexing.")
            }

            // Perform the search
            val results = searchIndex()
            println("Search completed. Found ${results.size} results")
            results
        } catch (e: Exception) {
            println("Error during search: ${e.message}")
            e.printStackTrace()
            skippedPaths.add("Error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Checks if a Lucene index already exists in the specified directory
     *
     * @return true if index exists, false otherwise
     */
    private fun indexExists(): Boolean {
        return try {
            DirectoryReader.open(indexDirectory)
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Recursively walks through the directory tree and indexes files and directories
     * Uses Java's FileVisitor pattern for traversal
     *
     * @param indexWriter The Lucene IndexWriter instance used to add documents to the index
     */
    /**
     * Recursively walks through the directory tree and indexes files and directories
     * Uses Java's FileVisitor pattern for traversal
     *
     * @param indexWriter The Lucene IndexWriter instance used to add documents to the index
     */

    private fun indexFilesAndDirectories(indexWriter: IndexWriter) {
        // Get all available drives (e.g., C:, D:, etc.)
        val roots = File.listRoots()

        // Create a fixed thread pool for parallel processing
        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        // Submit each root directory indexing task to the thread pool
        roots.forEach { root ->
            executor.submit {
                val rootPath = root.toPath()
                println("Walking directory tree from: ${rootPath.toAbsolutePath()}")

                try {
                    // Use Files.walkFileTree to index files and directories on this drive
                    Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
                        // Handle regular files
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            if (searchMode == SearchMode.FILES || searchMode == SearchMode.ALL) {
                                try {
                                    addToIndex(file, indexWriter, true)
                                } catch (e: AccessDeniedException) {
                                    skippedPaths.add("File: ${file.toString()} (Access Denied)")
                                } catch (e: Exception) {
                                    skippedPaths.add("File: ${file.toString()} (${e.message})")
                                }
                            }
                            return FileVisitResult.CONTINUE
                        }

                        // Handle directories before entering them
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            if (isRestrictedDirectory(dir)) {
                                skippedPaths.add("Directory: ${dir.toString()} (Restricted)")
                                return FileVisitResult.SKIP_SUBTREE
                            }

                            if (searchMode == SearchMode.DIRECTORIES || searchMode == SearchMode.ALL) {
                                try {
                                    addToIndex(dir, indexWriter, false)
                                } catch (e: AccessDeniedException) {
                                    skippedPaths.add("Directory: ${dir.toString()} (Access Denied)")
                                    return FileVisitResult.SKIP_SUBTREE
                                } catch (e: Exception) {
                                    skippedPaths.add("Directory: ${dir.toString()} (${e.message})")
                                    return FileVisitResult.SKIP_SUBTREE
                                }
                            }
                            return FileVisitResult.CONTINUE
                        }

                        // Handle failures during traversal
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

        // Shutdown the executor and wait for all tasks to complete
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
     * Adds a single file or directory to the Lucene index
     *
     * @param path Path to the file or directory
     * @param indexWriter The Lucene IndexWriter instance
     * @param isFile Boolean indicating if the path is a file (true) or directory (false)
     */
    private fun addToIndex(path: Path, indexWriter: IndexWriter, isFile: Boolean) {
        val document = Document()

        val fullFileName = path.fileName?.toString() ?: return

        // Store both original and lowercase versions for case-insensitive search
        document.add(TextField("nameOriginal", fullFileName, Field.Store.YES))
        document.add(TextField("name", fullFileName.lowercase(), Field.Store.YES))

        // Store the parent path for context
        val parentPath = path.parent?.toString() ?: ""
        document.add(StringField("parent", parentPath, Field.Store.YES))

        // Store the full absolute path and item type
        document.add(StringField("path", path.toAbsolutePath().toString(), Field.Store.YES))
        document.add(StringField("isFile", isFile.toString(), Field.Store.YES))

        indexWriter.addDocument(document)
        totalIndexed++

        // Progress reporting
        if (totalIndexed % 1000 == 0) {
            println("Indexed $totalIndexed items...")
        }
    }

    /**
     * Checks if a directory should be excluded from indexing
     * Prevents indexing of system directories and other restricted locations
     *
     * @param path Path to check
     * @return true if the directory should be restricted, false otherwise
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
     * Performs the actual search in the Lucene index using Search Everything-style matching
     * Search behavior:
     * - Case-insensitive matching
     * - Each word in the search query must be present in the filename
     * - Words can appear in any order
     * - Words can be substrings of longer words
     *
     * @return List of matching SystemItems filtered by the current searchMode
     */
    private fun searchIndex(): List<SystemItem> {
        println("\nExecuting search with value: '$searchValue'")

        // Split search into terms and remove empty strings
        val searchTerms = searchValue.trim().lowercase().split(" ").filter { it.isNotEmpty() }

        val foundItems = mutableListOf<SystemItem>()

        DirectoryReader.open(indexDirectory).use { reader ->
            println("Index contains ${reader.numDocs()} documents")

            val searcher = IndexSearcher(reader)
            val booleanQuery = org.apache.lucene.search.BooleanQuery.Builder()

            // Process each search term
            for (term in searchTerms) {
                val termQuery = org.apache.lucene.search.BooleanQuery.Builder()

                // Create wildcard queries for substring matching
                val wildcardQueryName = WildcardQuery(Term("name", "*${term}*"))
                val wildcardQueryNameOriginal = WildcardQuery(Term("nameOriginal", "*${term}*"))

                // Term can match in either field (SHOULD = OR)
                termQuery.add(wildcardQueryName, org.apache.lucene.search.BooleanClause.Occur.SHOULD)
                termQuery.add(wildcardQueryNameOriginal, org.apache.lucene.search.BooleanClause.Occur.SHOULD)

                // All terms must be present (MUST = AND)
                booleanQuery.add(termQuery.build(), org.apache.lucene.search.BooleanClause.Occur.MUST)
            }

            // Execute search and process results
            val topDocs = searcher.search(booleanQuery.build(), Integer.MAX_VALUE)
            println("Found ${topDocs.totalHits} matching documents")

            for (scoreDoc in topDocs.scoreDocs) {
                val doc = searcher.doc(scoreDoc.doc)
                val itemName = doc.get("nameOriginal")
                val itemPath = doc.get("path")
                val isFile = doc.get("isFile").toBoolean()

                if (itemName != null && itemPath != null) {
                    // Filter results based on search mode
                    when (searchMode) {
                        SearchMode.FILES -> {
                            if (isFile) foundItems.add(SystemItem(itemName, itemPath, isFile))
                        }
                        SearchMode.DIRECTORIES -> {
                            if (!isFile) foundItems.add(SystemItem(itemName, itemPath, isFile))
                        }
                        SearchMode.ALL -> {
                            foundItems.add(SystemItem(itemName, itemPath, isFile))
                        }
                    }
                    println("Match found: $itemName at $itemPath")
                }
            }

            return foundItems
        }
    }
}