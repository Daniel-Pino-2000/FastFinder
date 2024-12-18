import java.io.File
import kotlinx.coroutines.*
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
     * Starts the search based on the provided search mode.
     * It iterates over all root directories and filters the items (files or directories) based on the search mode.
     *
     * @return A list of SystemItem objects that match the search criteria.
     */
    fun startSearch(): List<SystemItem> {
        val foundItems = Collections.synchronizedList(mutableListOf<SystemItem>()) // Thread-safe list

        // Ensure we only proceed if rootDirectories is not null or empty
        val validRootDirs = rootDirectories?.takeIf { it.isNotEmpty() } ?: return emptyList()

        // Precompute the filter logic based on the search mode
        val filter: (File) -> Boolean = when (searchMode) {
            SearchMode.FILES -> { file -> file.isFile && file.name.equals(searchValue, ignoreCase = true) }
            SearchMode.DIRECTORIES -> { file -> file.isDirectory && file.name.equals(searchValue, ignoreCase = true) }
            SearchMode.ALL -> { file -> file.name.equals(searchValue, ignoreCase = true) }
        }

        // Use coroutines for parallel traversal of root directories
        runBlocking {
            validRootDirs.map { rootDir ->
                async(Dispatchers.IO) { // Launch each directory traversal in its own coroutine
                    rootDir.walkTopDown() // Traverse the rootDir and its subdirectories
                        .filter(filter) // Use the precomputed filter
                        .forEach { file ->
                            foundItems.add(
                                SystemItem(itemName = file.name, itemPath = file.path, isFile = file.isFile)
                            )
                        }
                }
            }.awaitAll() // Wait for all coroutines to complete
        }

        return foundItems // Return the list of found items
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

