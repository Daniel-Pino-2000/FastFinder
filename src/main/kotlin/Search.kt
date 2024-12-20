import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.Collections
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.util.concurrent.Callable

// Enum class representing the different search modes: FILES, DIRECTORIES, and ALL
enum class SearchMode {
    FILES,          // Search for files only
    DIRECTORIES,    // Search for directories only
    ALL             // Search for both files and directories
}

// Class responsible for searching through the internal storage to find the desired files and folders
class Search(
    var searchValue: String,     // The name of the file or directory to search for
    var searchMode: SearchMode,   // The mode of the search (FILES, DIRECTORIES, ALL)
    var customRootDirectory: File? = null  // Optional parameter for custom root directory
) {
    val rootDirectories: Array<File>? = customRootDirectory?.let { arrayOf(it) } ?: fetchRootDirectories()

    // Start the search
    fun startSearch(): List<SystemItem> {
        // Thread-safe list to store found items
        val foundItems = Collections.synchronizedList(mutableListOf<SystemItem>())
        val validRootDirs = rootDirectories?.takeIf { it.isNotEmpty() } ?: return emptyList()

        // ExecutorService to parallelize search
        val executor = Executors.newFixedThreadPool(4) // Set the thread pool size as needed
        val tasks = mutableListOf<Future<List<SystemItem>>>()

        validRootDirs.map { rootDir ->
            tasks.add(executor.submit<List<SystemItem>>(Callable {
                val localFoundItems = mutableListOf<SystemItem>()

                // Start walking the file tree for each root directory
                Files.walkFileTree(rootDir.toPath(), object : SimpleFileVisitor<Path>() {

                    val checkForFiles = searchMode == SearchMode.FILES || searchMode == SearchMode.ALL
                    val checkForDirs = searchMode == SearchMode.DIRECTORIES || searchMode == SearchMode.ALL

                    // Visit each file encountered during traversal
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        try {
                            val fileName = file.fileName?.toString()
                            if (fileName != null && checkForFiles && fileName.contains(searchValue, ignoreCase = true)) {
                                localFoundItems.add(SystemItem(fileName, file.toString(), isFile = true))
                            }
                        } catch (e: AccessDeniedException) {
                            println("Access denied to file: ${file.toString()}")
                        }
                        return FileVisitResult.CONTINUE
                    }

                    // Visit each directory encountered during traversal
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        try {
                            val dirName = dir.fileName?.toString()
                            if (dirName != null && checkForDirs && dirName.contains(searchValue, ignoreCase = true)) {
                                localFoundItems.add(SystemItem(dirName, dir.toString(), isFile = false))
                            }

                            /*
                            // Skip protected system directories
                            if (dirName == "\$RECYCLE.BIN" || dirName.equals("Windows", ignoreCase = true) ||
                                dirName.equals("Program Files", ignoreCase = true) || dirName.equals("Program Files (x86)", ignoreCase = true)) {
                                return FileVisitResult.SKIP_SUBTREE
                            }

                             */

                        } catch (e: AccessDeniedException) {
                            println("Access denied to directory: ${dir.toString()}")
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    // Handle failed visits (e.g., AccessDeniedException)
                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        if (exc is AccessDeniedException) {
                            println("Access denied to: ${file.toString()}")
                            return FileVisitResult.CONTINUE
                        }
                        return super.visitFileFailed(file, exc)
                    }
                })

                return@Callable localFoundItems
            }))
        }

        // Wait for all tasks to finish and collect results
        tasks.forEach {
            foundItems.addAll(it.get())
        }

        executor.shutdown() // Shut down the executor
        return foundItems
    }

    // Fetch root directories from the system
    fun fetchRootDirectories(): Array<File>? {
        val roots = File.listRoots()
        return if (roots.isNotEmpty()) {
            roots
        } else {
            null
        }
    }
}

