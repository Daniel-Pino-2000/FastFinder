import java.io.File
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import java.nio.file.AccessDeniedException

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
        // Thread-safe list to store the found items
        val foundItems = Collections.synchronizedList(mutableListOf<SystemItem>())

        // Ensure we only proceed if rootDirectories is not null or empty
        val validRootDirs = rootDirectories?.takeIf { it.isNotEmpty() } ?: return emptyList()

        // Start a coroutine scope using runBlocking
        runBlocking {
            validRootDirs.map { rootDir ->
                async(Dispatchers.IO) {
                    // Start walking the file tree for each root directory
                    Files.walkFileTree(rootDir.toPath(), object : SimpleFileVisitor<Path>() {

                        // Determine the search criteria once before traversal
                        val checkForFiles = searchMode == SearchMode.FILES || searchMode == SearchMode.ALL
                        val checkForDirs = searchMode == SearchMode.DIRECTORIES || searchMode == SearchMode.ALL

                        // Override visitFile to handle each file encountered during traversal
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            try {
                                val fileName = file.fileName?.toString() // Safe call to prevent NPE
                                // Only check files if searchMode is FILES or ALL, and file name contains search value
                                if (fileName != null && checkForFiles && fileName.contains(searchValue, ignoreCase = true)) {
                                    foundItems.add(
                                        SystemItem(itemName = fileName, itemPath = file.toString(), isFile = true)
                                    )
                                } else if (fileName == null) {
                                    // Log any file with a null name (for debugging purposes)
                                    println("Skipping file with null name: ${file.toString()}")
                                }
                            } catch (e: AccessDeniedException) {
                                println("Access denied to file: ${file.toString()}")
                            }
                            return FileVisitResult.CONTINUE // Continue to the next file
                        }

                        // Override preVisitDirectory to handle each directory encountered during traversal
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            try {
                                val dirName = dir.fileName?.toString() // Safe call to prevent NPE
                                // Only check directories if searchMode is DIRECTORIES or ALL, and dir name contains search value
                                if (dirName != null && checkForDirs && dirName.contains(searchValue, ignoreCase = true)) {
                                    foundItems.add(
                                        SystemItem(itemName = dirName, itemPath = dir.toString(), isFile = false)
                                    )
                                } else if (dirName == null) {
                                    // Log any directory with a null name (for debugging purposes)
                                    println("Skipping directory with null name: ${dir.toString()}")
                                }
                                // Skip protected system directories like Windows, Program Files, and others
                                if (dirName == "\$RECYCLE.BIN" || dirName.equals("Windows", ignoreCase = true) ||
                                    dirName.equals("Program Files", ignoreCase = true) || dirName.equals("Program Files (x86)", ignoreCase = true)) {
                                    println("Skipping protected system directory: ${dir.toString()}")
                                    return FileVisitResult.SKIP_SUBTREE // Skip this directory and its contents
                                }
                            } catch (e: AccessDeniedException) {
                                println("Access denied to directory: ${dir.toString()}")
                                return FileVisitResult.SKIP_SUBTREE // Skip this directory and its contents
                            }
                            return FileVisitResult.CONTINUE // Continue to the next directory
                        }

                        // Catch and handle AccessDeniedException to prevent crash on protected directories
                        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                            if (exc is AccessDeniedException) {
                                println("Access denied to: ${file.toString()}")
                                return FileVisitResult.CONTINUE // Skip this file and continue
                            }
                            return super.visitFileFailed(file, exc)
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
            println("Root directories found.")
            roots // Return the list of root directories
        } else {
            println("No root directories found.")
            null // Return null if no root directories are found
        }
    }
}

