import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import javax.swing.JOptionPane

class Search(
    private val searchValue: String,
    private val customSearchDirectory: File? = null, // Accepts File for custom search directory
    private val dbManager: DBManager = DBManager()
) {
    private val searcherManager: SearcherManager?

    init {
        if (customSearchDirectory == null) {
            // Only initialize Lucene if we're searching in the index
            if (dbManager.isFirstIndexCreation || dbManager.isDeletingFile) {
                println("Index creation is in progress. Search is not allowed.")
                searcherManager = null
            } else {
                println("Initializing SearcherManager...")
                val directory: Directory = resolveDirectory()
                println("Resolved directory: $directory")

                searcherManager = try {
                    SearcherManager(directory, null).also {
                        println("SearcherManager initialized successfully.")
                    }
                } catch (e: Exception) {
                    println("Failed to initialize SearcherManager: ${e.message}")
                    e.printStackTrace()
                    throw IllegalStateException("Failed to initialize SearcherManager: ${e.message}", e)
                }
            }
        } else {
            // If customSearchDirectory is provided, we don't need Lucene
            searcherManager = null
        }
    }

    /**
     * Resolves the directory to be used for the searcher.
     */
    private fun resolveDirectory(): Directory {
        val defaultDirectory = dbManager.indexPath.toFile()
        println("Using default index path: ${defaultDirectory.absolutePath}")
        require(defaultDirectory.exists() && defaultDirectory.isDirectory) {
            "Default index path ${defaultDirectory.absolutePath} is invalid."
        }
        println("Default directory is valid: ${defaultDirectory.absolutePath}")
        return FSDirectory.open(defaultDirectory.toPath())
    }

    /**
     * Perform the search if the index is ready, else show a message indicating the index is still being created.
     */
    fun search(): List<SystemItem> {
        return if (customSearchDirectory != null) {
            // Perform search in the custom directory
            searchInDirectory(customSearchDirectory)
        } else {
            // Perform search in the database
            if (dbManager.isFirstIndexCreation) {
                showIndexCreationMessage()
                emptyList()
            } else {
                try {
                    searcherManager?.maybeRefresh()
                    performSearch()
                } catch (e: Exception) {
                    println("Error during search: ${e.message}")
                    e.printStackTrace()
                    emptyList()
                }
            }
        }
    }

    /**
     * Show a message (pop-up dialog) informing that the index is currently being created.
     */
    private fun showIndexCreationMessage() {
        JOptionPane.showMessageDialog(
            null,
            "The database is currently being created. Please wait until indexing is complete or perform a custom search for small directories.",
            "Index Creation In Progress",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    // Performs the search in the database.
    private fun performSearch(): List<SystemItem> {
        val searchTerms = searchValue.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        println("Search initiated with terms: $searchTerms")

        val foundItems = mutableListOf<SystemItem>()

        val searcher: IndexSearcher? = searcherManager?.acquire()
        if (searcher == null) {
            println("Failed to acquire IndexSearcher.")
            return emptyList()
        }

        try {
            val booleanQuery = BooleanQuery.Builder()
            println("Building query...")

            // Create a query for each search term and add it to the boolean query
            for (term in searchTerms) {
                // Use a WildcardQuery to allow partial matches within each term
                val wildcardQuery = WildcardQuery(Term("name", "*$term*"))
                booleanQuery.add(wildcardQuery, BooleanClause.Occur.SHOULD)
            }

            val finalQuery = booleanQuery.build()
            println("Final query: $finalQuery")

            val topDocs = searcher.search(finalQuery, Integer.MAX_VALUE)
            println("Found ${topDocs.totalHits} matching documents")

            for (scoreDoc in topDocs.scoreDocs) {
                val doc = searcher.doc(scoreDoc.doc)
                val itemName = doc.get("nameOriginal")?.lowercase() // Ensure case-insensitive comparison
                val itemPath = doc.get("path")
                val isFile = doc.get("isFile")?.toBoolean() ?: false
                val size = doc.get("sizeDisplay")?.toLongOrNull() ?: 0L // Handle missing or null size


                if (itemName != null && itemPath != null) {
                    // Check if all search terms are present in the item name
                    val allTermsMatch = searchTerms.all { term ->
                        itemName.contains(term)
                    }

                    if (allTermsMatch) {
                            if (isFile) foundItems.add(SystemItem(itemPath, isFile, size))
                            if (!isFile) foundItems.add(SystemItem(itemPath, isFile, size))
                    }
                }
            }
        } catch (e: Exception) {
            println("Error during search: ${e.message}")
            e.printStackTrace()
        } finally {
            searcherManager?.release(searcher)
        }

        return foundItems
    }

    /**
     * Searches within a specified directory and returns elements that match the search query.
     *
     * @param directory The directory to search in (provided by the user).
     * @return A list of matching files and/or directories as `SystemItem` objects.
     */
    private fun searchInDirectory(directory: File): List<SystemItem> {
        require(directory.exists() && directory.isDirectory) {
            "Provided path is not a valid directory: ${directory.absolutePath}"
        }

        val searchTerms = searchValue.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        val matchingItems = mutableListOf<SystemItem>()

        try {
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        val fileName = file.fileName.toString().lowercase()
                        if (matchesSearchCriteria(fileName, searchTerms)) {
                            val item = createSystemItem(file, isFile = true)
                            matchingItems.add(item)

                        }
                    } catch (e: AccessDeniedException) {
                        println("Access denied to file: $file")
                    } catch (e: Exception) {
                        println("Error accessing file: $file (${e.message})")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        val dirName = dir.fileName.toString().lowercase()
                        if (matchesSearchCriteria(dirName, searchTerms)) {
                            val item = createSystemItem(dir, isFile = false)
                            matchingItems.add(item)

                        }
                    } catch (e: AccessDeniedException) {
                        println("Access denied to directory: $dir")
                        return FileVisitResult.SKIP_SUBTREE
                    } catch (e: Exception) {
                        println("Error accessing directory: $dir (${e.message})")
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    println("Failed to access: $file (${exc.message})")
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            println("Error walking through the directory ${directory.absolutePath}: ${e.message}")
        }

        return matchingItems
    }

    /**
     * Checks if a file or directory name matches all search terms.
     */
    private fun matchesSearchCriteria(name: String, searchTerms: List<String>): Boolean {
        return searchTerms.all { term ->
            name.contains(term)
        }
    }

    /**
     * Creates a `SystemItem` object from a file or directory path.
     */
    private fun createSystemItem(path: Path, isFile: Boolean): SystemItem {
        return SystemItem(
            itemPath = path.toAbsolutePath().toString(),
            isFile = isFile,
            // itemSize = if (isFile) attrs.size() else 0L // Directories have size 0
            itemSize = null
        )
    }
}