
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.WildcardQuery
import java.io.File

class Search(
    val searchValue: String,
    val searchMode: SearchMode,
    val customRootDirectory: File? = null
) {
    private val dbManager = DBManager()
    private val indexDirectory = dbManager.indexDirectory

    /**
     * Main function that performs the search.
     */
    fun search(): List<SystemItem> {
        return try {

            // Ensure the index is created or updated
            dbManager.createOrUpdateIndex()

            // Perform the search
            val results = searchIndex()
            println("Search completed. Found ${results.size} results")
            results
        } catch (e: Exception) {
            println("Error during search: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Performs the actual search in the Lucene index.
     */
    private fun searchIndex(): List<SystemItem> {
        val searchTerms = searchValue.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        val foundItems = mutableListOf<SystemItem>()

        DirectoryReader.open(indexDirectory).use { reader ->
            val searcher = IndexSearcher(reader)
            val booleanQuery = BooleanQuery.Builder()

            for (term in searchTerms) {
                val termQuery = BooleanQuery.Builder()
                val wildcardQueryName = WildcardQuery(Term("name", "*${term}*"))
                val wildcardQueryNameOriginal = WildcardQuery(Term("nameOriginal", "*${term}*"))

                // Updated use of BooleanClause.Occur
                termQuery.add(wildcardQueryName, BooleanClause.Occur.SHOULD)
                termQuery.add(wildcardQueryNameOriginal, BooleanClause.Occur.SHOULD)

                booleanQuery.add(termQuery.build(), BooleanClause.Occur.MUST)
            }

            val topDocs = searcher.search(booleanQuery.build(), Integer.MAX_VALUE)
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
            return foundItems
        }
    }
}
