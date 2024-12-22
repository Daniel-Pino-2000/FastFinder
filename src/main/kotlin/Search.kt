import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.*

enum class SearchMode {
    FILES,
    DIRECTORIES,
    ALL
}

class Search(
    private val searchValue: String,
    private val searchMode: SearchMode,
    private val customRootDirectory: File? = null
) {
    private val rootDirectories: Array<File>? = customRootDirectory?.let { arrayOf(it) } ?: fetchRootDirectories()
    private val analyzer = StandardAnalyzer()
    private val indexDirectory = RAMDirectory()

    fun startSearch(): List<SystemItem> {
        // Index the files
        indexFiles()

        // Search the index
        return searchIndex()
    }

    private fun indexFiles() {
        val indexWriterConfig = IndexWriterConfig(analyzer)
        val indexWriter = IndexWriter(indexDirectory, indexWriterConfig)

        rootDirectories?.forEach { rootDir ->
            println("Indexing root directory: ${rootDir.absolutePath}")
            indexDirectory(rootDir.toPath(), indexWriter)
        }

        indexWriter.close()
    }

    private fun indexDirectory(path: Path, indexWriter: IndexWriter) {
        try {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (searchMode == SearchMode.FILES || searchMode == SearchMode.ALL) {
                        addToIndex(file, indexWriter, isFile = true)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (searchMode == SearchMode.DIRECTORIES || searchMode == SearchMode.ALL) {
                        if (Files.isReadable(dir)) {
                            addToIndex(dir, indexWriter, isFile = false)
                        } else {
                            println("Access denied to directory: ${dir.toString()}")
                        }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: AccessDeniedException) {
            println("Access denied to path: ${path.toString()} - You may need administrator rights.")
        } catch (e: IOException) {
            println("Error accessing path: ${path.toString()}")
        }
    }

    private fun addToIndex(path: Path, indexWriter: IndexWriter, isFile: Boolean) {
        val document = Document()

        // Ensure the fileName is not null, use full path if needed
        val fileName = path.fileName?.toString() ?: path.toString()
        document.add(TextField("name", fileName.lowercase(), Field.Store.YES)) // Lowercase for case-insensitive search

        document.add(StringField("path", path.toString(), Field.Store.YES))
        document.add(StringField("isFile", isFile.toString(), Field.Store.YES))

        indexWriter.addDocument(document)
        println("Indexed: $fileName")
    }

    private fun searchIndex(): List<SystemItem> {
        val queryParser = QueryParser("name", analyzer)
        val escapedSearchValue = QueryParser.escape(searchValue) // Escape special characters
        val query = queryParser.parse("$escapedSearchValue*") // Adding wildcard for partial match

        val indexReader = DirectoryReader.open(indexDirectory)
        val indexSearcher = IndexSearcher(indexReader)

        // Execute search
        val topDocs = indexSearcher.search(query, 100) // Limit to top 100 results
        println("Found ${topDocs.totalHits} results for '$searchValue'") // Debug log

        val foundItems = mutableListOf<SystemItem>()

        // Collect search results
        for (scoreDoc in topDocs.scoreDocs) {
            val doc = indexSearcher.doc(scoreDoc.doc)
            val name = doc.get("name")
            val path = doc.get("path")
            val isFile = doc.get("isFile").toBoolean()
            foundItems.add(SystemItem(name, path, isFile))
            println("Found item: $name at $path") // Debug log for each found item
        }

        indexReader.close()
        return foundItems
    }

    private fun fetchRootDirectories(): Array<File>? {
        val roots = File.listRoots()
        return if (roots.isNotEmpty()) {
            roots
        } else {
            null
        }
    }
}
