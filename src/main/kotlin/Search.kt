import java.io.File
import kotlinx.coroutines.*
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections

// Enum class representing the different search modes: FILES, DIRECTORIES, and ALL
enum class SearchMode {
    FILES,          // Search for files only
    DIRECTORIES,    // Search for directories only
    ALL             // Search for both files and directories
}

// Class responsible for searching through the internal storage to find the desired files and folders
// that match the searchValue. The search behavior is controlled by the searchMode value.
class Search(
    var searchValue: String,     // The name of the file or directory to search for
    var searchMode: SearchMode,   // The mode of the search (FILES, DIRECTORIES, ALL)
    var customRootDirectory: File? = null  // Optional parameter for custom root directory

) {
    // Use the custom root directory if provided, otherwise fall back to the default root directories
    val rootDirectories: Array<File>? = customRootDirectory?.let { arrayOf(it) } ?: fetchRootDirectories()

    /**
     * Starts the search based on the provided search mode (FILES, DIRECTORIES, or ALL).
     * It iterates over all root directories and filters the items (files or directories)
     * based on the search mode, and then returns a list of `SystemItem` objects that match the search criteria.
     *
     * This method utilizes coroutines for parallel traversal of root directories,
     * and it performs a depth-first search using `Files.walkFileTree` to handle directories and files efficiently.
     *
     * Depending on the search mode, the following operations occur:
     * - **FILES**: Only files matching the `searchValue` will be included in the results.
     * - **DIRECTORIES**: Only directories matching the `searchValue` will be included in the results.
     * - **ALL**: Both files and directories matching the `searchValue` will be included in the results.
     *
     * The traversal uses a `SimpleFileVisitor` to visit each file and directory in the specified root directories.
     * For each file or directory that matches the `searchValue`, a `SystemItem` object is created and added to the results list.
     *
     * The search results are returned as a list of `SystemItem` objects, which contain the name, path, and type (file or directory)
     * of each matched item.
     *
     * @return A list of `SystemItem` objects that match the search criteria based on the search mode.
     *         If no items match the criteria or if no root directories are found, an empty list is returned.
     */
    fun startSearch(): List<SystemItem> {
        // Create a thread-safe list to hold the found items
        val foundItems = Collections.synchronizedList(mutableListOf<SystemItem>())

        // Ensure we only proceed if rootDirectories is not null or empty
        // If rootDirectories is null or empty, return an empty list
        val validRootDirs = rootDirectories?.takeIf { it.isNotEmpty() } ?: return emptyList()

        // Start a coroutine scope using runBlocking
        // runBlocking is used to wait for all asynchronous tasks to finish
        runBlocking {
            // Iterate over each valid root directory asynchronously
            validRootDirs.map { rootDir ->
                // Launch a new coroutine for each directory traversal task
                async(Dispatchers.IO) {
                    // Start walking the file tree for each root directory
                    Files.walkFileTree(rootDir.toPath(), object : SimpleFileVisitor<Path>() {

                        // Override visitFile to handle each file encountered during traversal
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            // Extract the file name from the Path object
                            val fileName = file.fileName.toString()

                            // Check if searchMode is FILES and the file name matches searchValue
                            if (searchMode == SearchMode.FILES && fileName.equals(searchValue, ignoreCase = true)) {
                                // Add the file to foundItems if it matches
                                foundItems.add(
                                    SystemItem(itemName = fileName, itemPath = file.toString(), isFile = true)
                                )
                            }

                            // Check if searchMode is ALL and the file name matches searchValue
                            if (searchMode == SearchMode.ALL && fileName.equals(searchValue, ignoreCase = true)) {
                                // Add the file to foundItems if it matches in ALL mode
                                foundItems.add(
                                    SystemItem(itemName = fileName, itemPath = file.toString(), isFile = true)
                                )
                            }

                            // Continue to the next file in the directory
                            return FileVisitResult.CONTINUE
                        }

                        // Override preVisitDirectory to handle each directory encountered during traversal
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            // Extract the directory name from the Path object
                            val dirName = dir.fileName.toString()

                            // Check if searchMode is DIRECTORIES and the directory name matches searchValue
                            if (searchMode == SearchMode.DIRECTORIES && dirName.equals(searchValue, ignoreCase = true)) {
                                // Add the directory to foundItems if it matches
                                foundItems.add(
                                    SystemItem(itemName = dirName, itemPath = dir.toString(), isFile = false)
                                )
                            }

                            // Check if searchMode is ALL and the directory name matches searchValue
                            if (searchMode == SearchMode.ALL && dirName.equals(searchValue, ignoreCase = true)) {
                                // Add the directory to foundItems if it matches in ALL mode
                                foundItems.add(
                                    SystemItem(itemName = dirName, itemPath = dir.toString(), isFile = false)
                                )
                            }

                            // Continue to the contents of the directory
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
            }.awaitAll() // Wait for all asynchronous tasks (coroutines) to complete
        }

        // Return the list of found items (files or directories) that match the search criteria
        return foundItems
    }




    /**
     * Returns the list of root directories available in the system's file storage.
     *
     * @return An array of root directories (File objects), or null if no root directories are found.
     */
    fun fetchRootDirectories(): Array<File>? {
        val roots = File.listRoots() // Get all root directories
        return if (roots.isNotEmpty()) {
            roots // Return the list of root directories
        } else {
            null // Return null if no root directories are found
        }
    }
}

