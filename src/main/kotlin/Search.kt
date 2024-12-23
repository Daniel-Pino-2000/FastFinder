
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
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
    val customRootDirectory: File?,
    val indexDirectoryName: String = "database" // Folder name for the index
) {
    private val analyzer = StandardAnalyzer()
    private val indexDirectory: Directory
    private var totalIndexed = 0
    val skippedPaths = mutableListOf<String>()

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

    fun indexAndSearch(): List<SystemItem> {
        return try {
            println("Starting search for '$searchValue' in mode: $searchMode")
            println("Root directory: ${customRootDirectory?.absolutePath ?: System.getProperty("user.dir")}")

            // Check if the index exists
            if (!indexExists()) {
                // If the index doesn't exist, create it
                val indexWriterConfig = IndexWriterConfig(analyzer)
                IndexWriter(indexDirectory, indexWriterConfig).use { writer ->
                    indexFilesAndDirectories(writer)
                    writer.commit() // Commit to the index after indexing
                    println("\nIndexing completed. Total items indexed: $totalIndexed")
                }
            } else {
                println("Index already exists, skipping indexing.")
            }

            // Now perform the search
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
        // Check if the index exists by trying to open it
        return try {
            DirectoryReader.open(indexDirectory)
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun indexFilesAndDirectories(indexWriter: IndexWriter) {
        val rootPath = customRootDirectory?.toPath() ?: Paths.get(System.getProperty("user.dir"))
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

    private fun addToIndex(path: Path, indexWriter: IndexWriter, isFile: Boolean) {
        val document = Document()

        val fullFileName = path.fileName?.toString() ?: return

        // Store both original and lowercase versions
        document.add(TextField("nameOriginal", fullFileName, Field.Store.YES))
        document.add(TextField("name", fullFileName.lowercase(), Field.Store.YES))

        // Store the parent path for context
        val parentPath = path.parent?.toString() ?: ""
        document.add(StringField("parent", parentPath, Field.Store.YES))

        // Store the full absolute path
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

        // Trim and escape the search value for safety
        val searchTerm = searchValue.trim().lowercase()

        val foundItems = mutableListOf<SystemItem>()

        DirectoryReader.open(indexDirectory).use { reader ->
            println("Index contains ${reader.numDocs()} documents")

            val searcher = IndexSearcher(reader)

            // If the search term contains spaces, use PhraseQuery for an exact phrase match
            if (searchTerm.contains(" ")) {
                // Split the term into individual words for phrase query
                val words = searchTerm.split(" ").map { it.lowercase() }

                // Create two PhraseQueries (one for "name" and one for "nameOriginal")
                val phraseQueryName = org.apache.lucene.search.PhraseQuery.Builder()
                val phraseQueryNameOriginal = org.apache.lucene.search.PhraseQuery.Builder()

                // Add the words to both PhraseQueries (for "name" and "nameOriginal")
                for (i in words.indices) {
                    phraseQueryName.add(org.apache.lucene.index.Term("name", words[i]), i)
                    phraseQueryNameOriginal.add(org.apache.lucene.index.Term("nameOriginal", words[i]), i)
                }

                // Combine both PhraseQueries using BooleanQuery
                val booleanQuery = org.apache.lucene.search.BooleanQuery.Builder()
                booleanQuery.add(phraseQueryName.build(), org.apache.lucene.search.BooleanClause.Occur.SHOULD)
                booleanQuery.add(phraseQueryNameOriginal.build(), org.apache.lucene.search.BooleanClause.Occur.SHOULD)

                val topDocs = searcher.search(booleanQuery.build(), Integer.MAX_VALUE)  // No limit on results
                println("Found ${topDocs.totalHits} matching documents (phrase)")

                for (scoreDoc in topDocs.scoreDocs) {
                    val doc = searcher.doc(scoreDoc.doc)
                    val itemName = doc.get("nameOriginal") // Use original name for display
                    val itemPath = doc.get("path")
                    val isFile = doc.get("isFile").toBoolean()

                    if (itemName != null && itemPath != null) {
                        // Filter based on search mode
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
            } else {
                // Single word search (wildcard search)
                val queryStr = """name:$searchTerm* OR nameOriginal:$searchTerm*""".trimIndent().replace("\n", " ")
                println("Query: $queryStr")

                val queryParser = QueryParser("name", analyzer)
                queryParser.allowLeadingWildcard = true  // Allow leading wildcard for partial matches
                val query = queryParser.parse(queryStr)

                val topDocs = searcher.search(query, Integer.MAX_VALUE)  // No limit on results
                println("Found ${topDocs.totalHits} matching documents")

                for (scoreDoc in topDocs.scoreDocs) {
                    val doc = searcher.doc(scoreDoc.doc)
                    val itemName = doc.get("nameOriginal") // Use original name for display
                    val itemPath = doc.get("path")
                    val isFile = doc.get("isFile").toBoolean()

                    if (itemName != null && itemPath != null) {
                        // Filter based on search mode
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
            }

            return foundItems
        }
    }

}
