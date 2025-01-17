
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.File
import javax.swing.JOptionPane

class Search(
    private val searchValue: String,
    private val searchMode: SearchMode,
    customRootDirectory: Any? = null, // Accepts File or Directory
    private val dbManager: DBManager = DBManager()
) {
    private val searcherManager: SearcherManager?

    init {
        if (dbManager.isFirstIndexCreation) {
            println("Index creation is in progress. Search is not allowed.")
            searcherManager = null
        } else {
            println("Initializing SearcherManager...")
            val directory: Directory = resolveDirectory(customRootDirectory)
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
    }

    /**
     * Resolves the directory to be used for the searcher.
     */
    private fun resolveDirectory(customRootDirectory: Any?): Directory {
        return when (customRootDirectory) {
            is Directory -> {
                println("Custom root directory provided as Directory: $customRootDirectory")
                customRootDirectory
            }
            is File -> {
                println("Custom root directory provided as File: ${customRootDirectory.absolutePath}")
                require(customRootDirectory.exists() && customRootDirectory.isDirectory) {
                    "${customRootDirectory.absolutePath} is not a valid directory."
                }
                println("Directory is valid: ${customRootDirectory.absolutePath}")
                FSDirectory.open(customRootDirectory.toPath())
            }
            null -> {
                val defaultDirectory = dbManager.indexPath.toFile()
                println("Using default index path: ${defaultDirectory.absolutePath}")
                require(defaultDirectory.exists() && defaultDirectory.isDirectory) {
                    "Default index path ${defaultDirectory.absolutePath} is invalid."
                }
                println("Default directory is valid: ${defaultDirectory.absolutePath}")
                FSDirectory.open(defaultDirectory.toPath())
            }
            else -> throw IllegalArgumentException("Invalid customRootDirectory: must be Directory, File, or null.")
        }
    }

    /**
     * Perform the search if the index is ready, else show a message indicating the index is still being created.
     */
    fun search(): List<SystemItem> {
        if (dbManager.isFirstIndexCreation) {
            showIndexCreationMessage()
            return emptyList()
        }

        return try {
            searcherManager?.maybeRefresh()
            performSearch()
        } catch (e: Exception) {
            println("Error during search: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Show a message (pop-up dialog) informing that the index is currently being created.
     */
    private fun showIndexCreationMessage() {
        JOptionPane.showMessageDialog(
            null,
            "The database is currently being created. Please wait until indexing is complete.",
            "Index Creation In Progress",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    /**
     * Perform the actual search using the SearcherManager.
     */
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

            // Process each search term
            for (term in searchTerms) {
                // Ensure wildcard queries are correctly created for matching parts of the name
                val wildcardQueryName = WildcardQuery(Term("name", "*${term}*"))
                val wildcardQueryNameOriginal = WildcardQuery(Term("nameOriginal", "*${term}*"))

                // Handle name without extensions: this logic assumes a single extension
                val termWithoutExtension = term.substringBeforeLast(".")
                val wildcardQueryNameNoExtension = WildcardQuery(Term("name", "*$termWithoutExtension*"))
                val wildcardQueryNameOriginalNoExtension = WildcardQuery(Term("nameOriginal", "*$termWithoutExtension*"))

                // Log the terms and queries to verify construction
                println("Adding wildcard queries for term '$term'")

                // Add wildcard queries for both full name and name without extension
                booleanQuery.add(wildcardQueryName, BooleanClause.Occur.SHOULD)
                booleanQuery.add(wildcardQueryNameOriginal, BooleanClause.Occur.SHOULD)
                booleanQuery.add(wildcardQueryNameNoExtension, BooleanClause.Occur.SHOULD)
                booleanQuery.add(wildcardQueryNameOriginalNoExtension, BooleanClause.Occur.SHOULD)
            }

            // Execute the search with the constructed boolean query
            println("Executing search with query: $booleanQuery")
            val topDocs = searcher.search(booleanQuery.build(), Integer.MAX_VALUE) // Limit to 100 results
            println("Found ${topDocs.totalHits} matching documents")

            // Process search results
            for (scoreDoc in topDocs.scoreDocs) {
                val doc = searcher.doc(scoreDoc.doc)
                val itemName = doc.get("nameOriginal")
                val itemPath = doc.get("path")
                val isFile = doc.get("isFile")?.toBoolean() ?: false

                // Log the document values to check for correctness
                println("Document: name=$itemName, path=$itemPath, isFile=$isFile")

                // Check the document against the search terms
                if (itemName != null && itemPath != null) {
                    // Check if itemName contains any of the search terms (individual check)
                    println("Checking itemName='$itemName' for term match: ${searchTerms.any { term -> itemName.lowercase().contains(term) }}")

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
                }
            }
        } catch (e: Exception) {
            println("Error during search: ${e.message}")
            e.printStackTrace()
        } finally {
            searcherManager?.release(searcher) // Ensure the searcher is released
        }

        return foundItems
    }

}
