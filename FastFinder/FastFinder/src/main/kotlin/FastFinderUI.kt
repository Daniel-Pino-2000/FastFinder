import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.Key.Companion.C
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Toolkit
import androidx.compose.ui.window.Window
import kotlin.math.round


@Composable
@Preview
fun FastFinderAppPreview() {
    FastFinderApp()
}


@Composable
fun FastFinderApp() {
    var searchValue by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    var testList by remember { mutableStateOf(listOf<String>()) }

    // FocusRequester ensures that the view can handle key events
    val focusRequester = FocusRequester()




    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally

        ) {

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start) {

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = searchValue,
                    onValueChange = { searchValue = it },
                    singleLine = true,
                    modifier = Modifier
                        .width(670.dp) // Set width dynamically based on screen size
                        .onKeyEvent { event ->
                            // Handle Enter key
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                testList = testList + searchValue
                                searchValue = ""
                                true // Indicate event was handled
                            } else {
                                false
                            }
                        }
                )


                Box {

                    Button(
                        onClick = {
                            testList = testList + searchValue
                            searchValue = ""
                        },
                        shape = RoundedCornerShape(
                            topStart = 0.dp, // Straight top-left corner
                            topEnd = 20.dp,   // Curved top-right corner
                            bottomStart = 0.dp, // Straight bottom-left corner
                            bottomEnd = 20.dp  // Curved bottom-right corner
                        ),
                        modifier = Modifier.height(56.dp)
                        ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box {

                    Button(
                        onClick = { isExpanded = true },
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(text = "Filter")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }


                    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false}) {

                        DropdownMenuItem(
                            onClick = {}
                        ) {
                            Text("Files")
                        }

                        DropdownMenuItem(
                            onClick = {}
                        ) {
                            Text("Folders")
                        }

                        DropdownMenuItem(
                            onClick = {}
                        ) {
                            Text("All")
                        }

                    }
                }



            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                items(testList) {
                    item ->
                    TextElements(item)

                }
            }
        }
    }
}


@Composable
fun TextElements(text: String) {
    Text(text = text, modifier = Modifier.padding(8.dp))
}

