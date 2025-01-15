import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.File
import javax.swing.JOptionPane

class Search(
    private val searchValue: String,
    private val searchMode: SearchMode,
    private val customRootDirectory: Any? = null, // Accept File or Directory
    private val dbManager: DBManager = DBManager()
) {
    private val searcherManager: SearcherManager?

    init {
        // Check if the index is being created
        if (dbManager.isFirstIndexCreation) {
            // Prevent search if index is still being created
            searcherManager = null
        } else {
            // Determine which directory to use
            val directory = when (customRootDirectory) {
                is Directory -> customRootDirectory
                is File -> {
                    if (!customRootDirectory.exists() || !customRootDirectory.isDirectory) {
                        throw IllegalArgumentException("${customRootDirectory.absolutePath} is not a valid directory")
                    }
                    FSDirectory.open(customRootDirectory.toPath())
                }
                null -> {
                    // Use the DBManager's current index directory
                    val currentIndexPath = dbManager.indexPath.toFile()
                    if (!currentIndexPath.exists() || !currentIndexPath.isDirectory) {
                        throw IllegalStateException("Default index path ${currentIndexPath.absolutePath} is invalid")
                    }
                    FSDirectory.open(currentIndexPath.toPath())
                }
                else -> throw IllegalArgumentException("customRootDirectory must be either a Directory, a File, or null")
            }

            // Initialize SearcherManager
            val directoryReader = DirectoryReader.open(directory)
            searcherManager = SearcherManager(directoryReader, null)
        }
    }

    /**
     * Perform the search if the index is ready, else show a message indicating the index is still being created.
     */
    fun search(): List<SystemItem> {
        return if (dbManager.isFirstIndexCreation) {
            // Show a message that the index is being created
            showIndexCreationMessage()
            emptyList()
        } else {
            try {
                // Ensure the index is up-to-date
                searcherManager?.maybeRefresh()

                val results = performSearch()
                println("Search completed. Found ${results.size} results")
                results
            } catch (e: Exception) {
                println("Error during search: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Show a message (pop-up dialog) informing that the index is currently being created.
     */
    private fun showIndexCreationMessage() {
        // Pop-up dialog to notify the user that the index is still being created
        JOptionPane.showMessageDialog(
            null,
            "The database is currently being created. Please wait until indexing is complete.\n" +
                    "You can perform a custom search instead.",
            "Index Creation In Progress",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    /**
     * Perform the actual search using the shared SearcherManager.
     */
    private fun performSearch(): List<SystemItem> {
        val searchTerms = searchValue.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        val foundItems = mutableListOf<SystemItem>()

        // Acquire the searcher
        val searcher: IndexSearcher = searcherManager!!.acquire()
        try {
            val booleanQuery = BooleanQuery.Builder()

            for (term in searchTerms) {
                val termQuery = BooleanQuery.Builder()
                val wildcardQueryName = WildcardQuery(Term("name", "*${term}*"))
                val wildcardQueryNameOriginal = WildcardQuery(Term("nameOriginal", "*${term}*"))

                termQuery.add(wildcardQueryName, BooleanClause.Occur.SHOULD)
                termQuery.add(wildcardQueryNameOriginal, BooleanClause.Occur.SHOULD)

                booleanQuery.add(termQuery.build(), BooleanClause.Occur.MUST)
            }

            // Use a reasonable limit for TopDocs instead of Integer.MAX_VALUE
            val topDocs = searcher.search(booleanQuery.build(), 100) // Limit to 100 results
            for (scoreDoc in topDocs.scoreDocs) {
                val doc = searcher.doc(scoreDoc.doc)
                val itemName = doc.get("nameOriginal")
                val itemPath = doc.get("path")
                val isFile = doc.get("isFile").toBoolean()

                if (itemName != null && itemPath != null) {
                    when (searchMode) {
                        SearchMode.FILES -> if (isFile) foundItems.add(SystemItem(itemName, itemPath, isFile))
                        SearchMode.DIRECTORIES -> if (!isFile) foundItems.add(SystemItem(itemName, itemPath, isFile))
                        SearchMode.ALL -> foundItems.add(SystemItem(itemName, itemPath, isFile))
                    }
                }
            }
        } finally {
            // Release the searcher after use
            searcherManager?.release(searcher)
        }
        return foundItems
    }
}
