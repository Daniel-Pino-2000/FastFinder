// Importing necessary Compose libraries for UI components and functionality
import androidx.compose.desktop.ui.tooling.preview.Preview
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import java.io.File

// Preview function to preview the app's UI in the IDE
@Composable
@Preview
fun FastFinderAppPreview() {
    FastFinderApp()
}

// The main app composable
@Composable
fun FastFinderApp() {
    // State for search value and other UI elements
    var searchValue by remember { mutableStateOf("") } // State for search input value
    var isExpanded by remember { mutableStateOf(false) }// State to control dropdown menu expansion
    var testList by remember { mutableStateOf(listOf<SystemItem>()) }// List of items to display
    var filterText by remember { mutableStateOf("Filter") } // Filter text (Files, Folders, All)
    val themeElements =  ThemeElements() // Instance of the data class where the UI theme elements are saved
    val lazyListState = rememberLazyListState() // LazyListState to manage the scroll state of LazyColumn
    var searchMode = SearchMode.ALL
    val customDir = File("C:\\")  // The directory to search in when testing
    var startTime : Long // Variable used to record the start time
    var endTime : Long // Variable used to record the end time
    var elapsedTime = 0.0 // Calculate elapsed time in seconds


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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start) {

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = searchValue,
                    onValueChange = { searchValue = it },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF0A5EB0)
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .width(670.dp) // Set width dynamically based on screen size
                        .onKeyEvent { event -> // Starts the search if the "Enter" button is pressed
                            // Handle Enter key
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                startTime = System.nanoTime()
                                testList = emptyList()
                                testList = testList + (Search(searchValue = searchValue, searchMode = searchMode, customRootDirectory = customDir).indexAndSearch())
                                searchValue = "" // Clear search after adding
                                endTime = System.nanoTime()
                                // Calculate elapsed time in seconds
                                elapsedTime = (endTime - startTime) / 1_000_000_000.0
                                println("\nResults summary:")
                                testList.forEach { item ->
                                    println("${if (item.isFile) "File" else "Dir"}: ${item.itemName}")
                                    println("  Path: ${item.itemPath}")
                                }
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
                            startTime = System.nanoTime()
                            testList = emptyList()
                            testList = testList + (Search(searchValue = searchValue, searchMode = searchMode, customRootDirectory = customDir).indexAndSearch())
                            searchValue = ""
                            endTime = System.nanoTime()
                            // Calculate elapsed time in seconds
                            elapsedTime = (endTime - startTime) / 1_000_000_000.0
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = themeElements.buttonColor,
                            contentColor = Color.White

                        ),
                        shape = RoundedCornerShape(
                            topStart = 0.dp, // Straight top-left corner
                            topEnd = 20.dp,   // Curved top-right corner
                            bottomStart = 0.dp, // Straight bottom-left corner
                            bottomEnd = 20.dp  // Curved bottom-right corner
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
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(text = filterText)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }


                    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false}) {

                        DropdownMenuItem(
                            onClick = {
                                filterText = "Files"
                                searchMode = SearchMode.FILES
                                isExpanded = false
                            }
                        ) {
                            Text("Files")
                        }

                        DropdownMenuItem(
                            onClick = {
                                filterText = "Folders"
                                searchMode = SearchMode.DIRECTORIES
                                isExpanded = false
                            }
                        ) {
                            Text("Folders")
                        }

                        DropdownMenuItem(
                            onClick = {
                                filterText = "All"
                                searchMode = SearchMode.ALL
                                isExpanded = false

                            }
                        ) {
                            Text("All")
                        }

                    }
                }

                Text("$elapsedTime")



            }

            // LazyColumn for displaying items and the Scrollbar
            Column(

            ) {
                Box(
                    modifier = Modifier.wrapContentWidth().padding(start = 8.dp)
                    ) {


                    LazyColumn(
                        state = lazyListState, // Attach LazyListState to LazyColumn
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .background(color = themeElements.lazyColumnColor, shape = RoundedCornerShape(5.dp))
                            .width(1000.dp)
                            .height(600.dp),
                        contentPadding = PaddingValues(8.dp),

                        ) {
                        items(testList) {
                            item ->
                            LazyListItem(item)


                        }

                    }
                    // Scrollbar that will be located inside the LazyColumn when there is a large number of elements
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp, top = 24.dp, bottom = 30.dp),
                        adapter = rememberScrollbarAdapter(lazyListState) // Use LazyListState here
                    )

                }
            }


        }
    }
}


