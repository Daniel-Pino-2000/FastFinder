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
import kotlin.io.AccessDeniedException

enum class SearchMode {
    FILES,
    DIRECTORIES,
    ALL
}

class Search(
    val searchValue: String,
    val searchMode: SearchMode,
    val customRootDirectory: File? = null,
    val indexDirectoryName: String = "database"
) {
    private val analyzer = StandardAnalyzer()
    private val indexDirectory: Directory
    private var totalIndexed = 0
    val skippedPaths = mutableListOf<String>()
    private val indexPath: Path

    init {
        val currentDirectory = System.getProperty("user.dir")
        indexPath = Paths.get(currentDirectory, indexDirectoryName)

        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath)
        }

        indexDirectory = FSDirectory.open(indexPath)
    }

    // Log the directory path when needed
    private fun getIndexDirectoryPath(): String {
        return indexPath.toString()
    }

    fun indexAndSearch(): List<SystemItem> {
        return try {
            println("Starting search for '$searchValue' in mode: $searchMode")
            println("Root directory: ${customRootDirectory?.absolutePath ?: System.getProperty("user.dir")}")

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

    private fun indexExists(): Boolean {
        return try {
            DirectoryReader.open(indexDirectory)
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun indexFilesAndDirectories(indexWriter: IndexWriter) {
        val rootPaths = getRootDirectories()
        for (rootPath in rootPaths) {
            println("Walking directory tree from: ${rootPath.toAbsolutePath()}")
            Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
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

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    skippedPaths.add("Failed to access: ${file.toString()} (${exc.message})")
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }

    private fun getRootDirectories(): List<Path> {
        return FileSystems.getDefault().rootDirectories.toList()
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

    private fun searchIndex(): List<SystemItem> {
        println("\nExecuting search with value: '$searchValue'")
        val searchTerms = searchValue.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        val foundItems = mutableListOf<SystemItem>()

        // Use a single BooleanQuery to combine all search terms
        val booleanQuery = org.apache.lucene.search.BooleanQuery.Builder()

        for (term in searchTerms) {
            val wildcardQueryName = WildcardQuery(Term("name", "*${term}*"))
            val wildcardQueryNameOriginal = WildcardQuery(Term("nameOriginal", "*${term}*"))

            // Use "SHOULD" to allow matching either "name" or "nameOriginal"
            booleanQuery.add(wildcardQueryName, org.apache.lucene.search.BooleanClause.Occur.SHOULD)
            booleanQuery.add(wildcardQueryNameOriginal, org.apache.lucene.search.BooleanClause.Occur.SHOULD)
        }

        // Open the reader and search the index (reuse searcher)
        val reader = DirectoryReader.open(indexDirectory)
        val searcher = IndexSearcher(reader)

        try {
            println("Index contains ${reader.numDocs()} documents")

            // Execute the query
            val topDocs = searcher.search(booleanQuery.build(), Integer.MAX_VALUE)
            println("Found ${topDocs.totalHits} matching documents")

            // Collect the results
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
                    println("Match found: $itemName at $itemPath")
                }
            }
        } finally {
            reader.close() // Ensure the reader is always closed
        }

        return foundItems
    }

}
