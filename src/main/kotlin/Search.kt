import java.io.File

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
        val foundItems = mutableListOf<SystemItem>() // List to store the found files and directories

        // Ensure we only proceed if rootDirectories is not null or empty
        val validRootDirs = rootDirectories?.takeIf { it.isNotEmpty() } ?: return emptyList()

        // Walk through each root directory and filter based on the search mode
        for (rootDir in validRootDirs) {
            rootDir.walkTopDown() // Traverse the directory and its subdirectories
                .filter { file ->
                    when (searchMode) {
                        SearchMode.FILES -> file.isFile && file.name.equals(searchValue, ignoreCase = true) // Match files by name
                        SearchMode.DIRECTORIES -> file.isDirectory && file.name.equals(searchValue, ignoreCase = true) // Match directories by name
                        SearchMode.ALL -> file.name.equals(searchValue, ignoreCase = true) // Match both files and directories by name
                    }
                }
                .forEach {
                    // Add matching files or directories to the list
                    foundItems.add(SystemItem(itemName = it.name, itemPath = it.path, isFile = it.isFile))
                }
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

