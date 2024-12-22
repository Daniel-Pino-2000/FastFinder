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
import org.apache.lucene.store.RAMDirectory
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

// Define the SearchMode enum for file/directory search modes
enum class SearchMode {
    FILES, DIRECTORIES, ALL
}


// Search class that will handle both indexing and searching
class Search(
    private val searchValue: String, // Value to search in the index
    private val searchMode: SearchMode, // Mode to determine whether to search files, directories, or both
    private val customRootDirectory: File? // The root directory to start indexing
) {
    private val analyzer = StandardAnalyzer()
    private val indexDirectory: Directory = RAMDirectory() // In-memory index
    private val skippedPaths = mutableListOf<String>() // Track skipped paths

    // Method to index files and directories and then perform the search
    fun indexAndSearch(): List<SystemItem> {
        return try {
            // Explicitly define the type to ensure it returns List<SystemItem>
            val items: List<SystemItem> = indexFilesAndDirectories()
            items  // Return the list of SystemItem objects directly
        } catch (e: Exception) {
            // In case of an error, add to skippedPaths and return an empty list
            skippedPaths.add("Error: ${e.message}")
            emptyList()  // Return an empty list when there is an exception
        }
    }

    // Index files and directories by walking through the file tree
    private fun indexFilesAndDirectories(): List<SystemItem> {
        val indexWriterConfig = IndexWriterConfig(analyzer)
        val indexWriter = IndexWriter(indexDirectory, indexWriterConfig)

        val rootPath = customRootDirectory?.toPath() ?: Paths.get(System.getProperty("user.dir"))

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

        // Ensure we return the list of items from searchIndex
        return searchIndex()  // This method should return a list of SystemItem
    }

    // Add a file or directory to the Lucene index
    private fun addToIndex(path: Path, indexWriter: IndexWriter, isFile: Boolean) {
        val document = Document()

        // Extract file name and add to index
        val fileName = path.fileName?.toString()?.lowercase() ?: return
        document.add(TextField("name", fileName, Field.Store.YES)) // Store file name
        document.add(StringField("path", path.toString(), Field.Store.YES)) // Store path
        document.add(StringField("isFile", isFile.toString(), Field.Store.YES)) // Store if it's a file

        indexWriter.addDocument(document) // Add document to index
        println("Indexed: $fileName")
    }

    // Check if a directory is restricted (e.g., Windows system directories)
    private fun isRestrictedDirectory(path: Path): Boolean {
        val restrictedDirs = setOf(
            "\$Recycle.Bin",
            "Windows",
            "Program Files",
            "Archivos de programa", // Spanish Windows
            "Program Files (x86)",
            "Archivos de programa (x86)", // Spanish Windows
            "System Volume Information",
            "Documents and Settings",
            "ProgramData",
            "\$Windows.~WS"
        )

        val pathStr = path.toString().lowercase()
        return restrictedDirs.any { restricted ->
            pathStr.contains(restricted.lowercase())
        }
    }

    // Search the index for files or directories matching the search value
    private fun searchIndex(): List<SystemItem> {
        val queryParser = QueryParser("name", analyzer)  // Parse query based on "name" field
        val query = queryParser.parse("$searchValue*")  // Use wildcard for partial matching

        val indexReader = DirectoryReader.open(indexDirectory)
        val indexSearcher = IndexSearcher(indexReader)

        val topDocs = indexSearcher.search(query, 100)
        val foundItems = mutableListOf<SystemItem>()

        for (scoreDoc in topDocs.scoreDocs) {
            val doc = indexSearcher.doc(scoreDoc.doc)
            val itemName = doc.get("name") ?: ""
            val itemPath = doc.get("path") ?: ""
            val isFile = doc.get("isFile").toBoolean()

            if (itemName.contains(searchValue, ignoreCase = true)) {
                foundItems.add(SystemItem(itemName, itemPath, isFile))
            }
        }

        indexReader.close()
        return foundItems
    }
}
