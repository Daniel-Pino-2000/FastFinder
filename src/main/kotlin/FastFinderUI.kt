// Importing necessary Compose libraries for UI components and functionality
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView


// Function to show a directory picker and return the selected directory path
fun showDirectoryPicker(): File? {
    val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory) // Start at the user's home directory
    fileChooser.dialogTitle = "Select Directory" // Set the dialog title
    fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY // Allow only directories to be selected
    fileChooser.isAcceptAllFileFilterUsed = false // Disable the "All files" filter

    // Show the dialog and check if the user selected a directory
    val result = fileChooser.showOpenDialog(null)

    // Return the selected directory if the user clicked "Open"
    return if (result == JFileChooser.APPROVE_OPTION) {
        fileChooser.selectedFile
    } else {
        null // Return null if the user canceled the dialog
    }
}

// The main app composable
@Composable
fun FastFinderApp(dbManager: DBManager) {
    // State for search value and other UI elements
    var searchValue by remember { mutableStateOf("") } // State for search input value
    var isExpanded by remember { mutableStateOf(false) } // State to control dropdown menu expansion of the search mode
    var filterExpanded by remember { mutableStateOf(false)} // State to control the dropdown menu of the file filter
    var testList by remember { mutableStateOf(listOf<SystemItem>()) } // List of items to display
    var searchFilter by remember { mutableStateOf("Search \nMode ") } // Filter text (Files, Folders, All)
    var resultFilter by remember { mutableStateOf("Filter\nResults") } // Filter results
    val themeElements = ThemeElements() // Instance of the data class where the UI theme elements are saved
    val lazyListState = rememberLazyListState() // LazyListState to manage the scroll state of LazyColumn
    var searchMode = SearchMode.ALL
    var resultMode = SearchFilter.ALL
    var startTime: Long // Variable used to record the start time
    var endTime: Long // Variable used to record the end time
    var elapsedTime by remember { mutableStateOf(0.0) } // Calculate elapsed time in seconds

    // State for custom search dialog
    var showCustomSearchDialog by remember { mutableStateOf(false) }
    var selectedDirectory by remember { mutableStateOf<File?>(null) }


    // Wrap the AtomicBoolean in a MutableState
    var isIndexing by remember { mutableStateOf(dbManager.isIndexing.get()) }

    // Use LaunchedEffect to observe changes to the AtomicBoolean
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // Check every 100ms
            isIndexing = dbManager.isIndexing.get() // Update the MutableState
        }
    }

    // Function to perform a search with a custom directory
    fun performSearch(customDir: File? = null) {
        startTime = System.nanoTime()
        testList = emptyList()
        testList = testList + Search(
            searchValue = searchValue,
            customSearchDirectory = customDir,
            dbManager
        ).search()
        searchValue = ""
        endTime = System.nanoTime()
        elapsedTime = (endTime - startTime) / 1_000_000_000.0
    }

    // Custom Search Dialog
    if (showCustomSearchDialog) {
        AlertDialog(
            onDismissRequest = {
                showCustomSearchDialog = false // Close the dialog
            },
            title = {
                Text(text = "Enter Search Query")
            },
            text = {
                Column {
                    TextField(
                        value = searchValue,
                        onValueChange = { searchValue = it },
                        singleLine = true,
                        label = { Text("Search Query") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCustomSearchDialog = false // Close the dialog
                        selectedDirectory?.let { dir ->
                            performSearch(dir) // Perform the search with the selected directory
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showCustomSearchDialog = false // Close the dialog
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }


    // Material theme for styling UI components
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().background(color = themeElements.backgroundColor),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Row where the search bar and the buttons are located.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = searchValue,
                    onValueChange = { searchValue = it },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF0A5EB0)
                    ),
                    singleLine = true,
                    modifier = Modifier
                        //.width(670.dp) // Set width dynamically based on screen size
                        .weight(1f) // Takes up remaining space in the Row
                        .onKeyEvent { event -> // Starts the search if the "Enter" button is pressed
                            // Handle Enter key
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                performSearch() // Perform the search
                                true // Indicate event was handled
                            } else {
                                false
                            }
                        }
                )

                // Search button that starts the search
                Box {
                    Button(
                        onClick = {
                            performSearch() // Perform the search
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = themeElements.buttonColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(
                            topStart = 0.dp, // Straight top-left corner
                            topEnd = 20.dp, // Curved top-right corner
                            bottomStart = 0.dp, // Straight bottom-left corner
                            bottomEnd = 20.dp // Curved bottom-right corner
                        ),
                        modifier = Modifier.height(57.dp).offset(x = (-2).dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Filter button with dropdown
                Box {
                    Button(
                        onClick = { isExpanded = true },
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = themeElements.buttonColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(56.dp).padding(end = 8.dp)
                    ) {
                        Text(text = searchFilter)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                        DropdownMenuItem(
                            onClick = {
                                searchFilter = "Files"
                                searchMode = SearchMode.FILES
                                isExpanded = false
                            }
                        ) {
                            Text("Files")
                        }
                        DropdownMenuItem(
                            onClick = {
                                searchFilter = "Folders"
                                searchMode = SearchMode.DIRECTORIES
                                isExpanded = false
                            }
                        ) {
                            Text("Folders")
                        }
                        DropdownMenuItem(
                            onClick = {
                                searchFilter = "All"
                                searchMode = SearchMode.ALL
                                isExpanded = false
                            }
                        ) {
                            Text("All")
                        }
                    }
                }



                // Search results filter button with dropdown
                Box {
                    Button(
                        onClick = { filterExpanded = true },
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = themeElements.buttonColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(56.dp).padding(end = 12.dp),
                        enabled = searchFilter != "Folders"
                    ) {
                        Text(text = resultFilter)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {

                        DropdownMenuItem(
                            onClick = {
                                resultFilter = "All"
                                resultMode = SearchFilter.ALL
                                filterExpanded = false
                            }
                        ) {
                            Text("All")
                        }

                        DropdownMenuItem(
                            onClick = {
                                resultFilter = "Images"
                                resultMode = SearchFilter.IMAGE
                                filterExpanded = false
                            }
                        ) {
                            Text("Images")
                        }
                        DropdownMenuItem(
                            onClick = {
                                resultFilter = "Documents"
                                resultMode = SearchFilter.DOCUMENT
                                filterExpanded = false
                            }
                        ) {
                            Text("Documents")
                        }
                        DropdownMenuItem(
                            onClick = {
                                resultFilter = "Videos"
                                resultMode = SearchFilter.VIDEO
                                filterExpanded = false
                            }
                        ) {
                            Text("Videos")
                        }

                        DropdownMenuItem(
                            onClick = {
                                resultFilter = "Audios"
                                resultMode = SearchFilter.AUDIO
                                filterExpanded = false
                            }
                        ) {
                            Text("Audios")
                        }

                        DropdownMenuItem(
                            onClick = {
                                resultFilter = "Executables"
                                resultMode = SearchFilter.EXECUTABLE
                                filterExpanded = false
                            }
                        ) {
                            Text("Executables")
                        }
                    }
                }

            }

            // LazyColumn for displaying items and the Scrollbar
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f)
                    .fillMaxHeight()
                    //.height(550.dp) // Fixed height
                    .background(color = themeElements.lazyColumnColor, shape = RoundedCornerShape(5.dp))
            ) {
                // LazyColumn for displaying items
                LazyColumn(
                    state = lazyListState, // Attach LazyListState
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier
                        .fillMaxWidth() // Fill the width with the Box
                        .fillMaxHeight() // Match the height of the Box
                ) {
                    items(testList) { item ->
                        LazyListItem(item, searchMode, resultMode) // Composable for individual items
                    }
                }

                // Vertical scrollbar aligned to the right edge of the Box
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(lazyListState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd) // Align to the right edge
                        .fillMaxHeight() // Match the height of the Box
                        .padding(end = 8.dp, top = 24.dp, bottom = 24.dp) // Padding for positioning
                )
            }

            // Update Database button and Indexing Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom, // Align children to the bottom
                horizontalArrangement = Arrangement.SpaceBetween // Space out children
            ) {
                // Indexing Status with Loading Animation (Bottom-Left Corner)
                Row(
                    modifier = Modifier
                        .weight(1f) // Take up remaining space
                        .padding(start = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (isIndexing) {
                        // Display the "Indexing Database" text
                        Text(
                            text = "Indexing Database",
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                            )
                        )

                        // Add a CircularProgressIndicator as a loading animation
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }

                // Buttons (Far Right Side)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Custom Search Button
                    Button(
                        onClick = {
                            // Open the directory picker
                            selectedDirectory = showDirectoryPicker()
                            if (selectedDirectory != null) {
                                showCustomSearchDialog = true // Show the custom search dialog
                            } else {
                                println("No directory selected or invalid directory.")
                            }
                        },
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            backgroundColor = themeElements.buttonColor
                        ),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(text = "Custom Search")
                    }
                }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Update Database Button
                    Button(
                        onClick = {
                            if (!dbManager.isIndexing.get()) {
                                Thread {
                                    synchronized(dbManager) {
                                        dbManager.createOrUpdateIndex(forceIndexCreation = true)
                                    }
                                }.start()
                            }
                        },
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            backgroundColor = themeElements.buttonColor
                        ),
                        modifier = Modifier.height(56.dp)
                    ) {

                        Text(text = "Update\nDatabase")
                        Icon(Icons.Default.Refresh, contentDescription = null)

                    }
                }
            }
        }
}