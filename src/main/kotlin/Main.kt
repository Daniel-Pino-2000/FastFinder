
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// Main entry point of the application
fun main() = application {
    // Remember the state of the window (size and position)
    val windowState = rememberWindowState(width = 1030.dp, height = 700.dp)

    // Create and configure a window
    Window(
        onCloseRequest = ::exitApplication,
        title = "FastFinder",
        state = windowState // Bind the window state (size, position) to the window
    ) {
        // Check if the window is minimized
        if (!windowState.isMinimized) {
            // Dynamically adjust size if needed
            windowState.size = androidx.compose.ui.unit.DpSize(1500.dp, 1500.dp)
        }


        // Call the FastFinderApp composable to display the main content
        FastFinderApp()
    }
}


