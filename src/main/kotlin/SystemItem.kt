// Class representing an item in the system (file or folder)
class SystemItem(
    // Name of the item (file or folder)

    // Path to the item in the system
    var itemPath: String,

    // Boolean flag indicating whether the item is a file (true) or a folder (false)
    var isFile: Boolean,

    // Size of the item in bytes
    var itemSize: Long?
)

/**
 * Enum defining the different modes of search operation
 * - FILES: Search only for files
 * - DIRECTORIES: Search only for directories
 * - ALL: Search for both files and directories
 */
enum class SearchMode {
    FILES,
    DIRECTORIES,
    ALL
}

/**
 * Enum defining the different filters for the search results.
 */
enum class SearchFilter {
    DOCUMENT,
    AUDIO,
    IMAGE,
    VIDEO,
    ALL,
    EXECUTABLE
}