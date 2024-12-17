import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
