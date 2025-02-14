import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import javax.swing.JFrame

// Main entry point of the application
fun main() = application {
    // Remember the state of the window (size and position)
    val windowState = rememberWindowState(width = 1030.dp, height = 700.dp)

    // Create and configure a window
    Window(
        onCloseRequest = ::exitApplication,
        title = "FastFinder",
        state = windowState, // Bind the window state (size, position) to the window
    ) {
        // Access the underlying Swing JFrame and set its minimum size
        val jFrame = (java.awt.Window.getWindows().firstOrNull() as? JFrame)
        jFrame?.minimumSize = Dimension(
            800.dp.value.toInt(), // Minimum width
            600.dp.value.toInt()  // Minimum height
        )

        // Start index creation in a background thread
        val dbManager = DBManager()
        Thread {
            synchronized(dbManager) {
                dbManager.createOrUpdateIndex(forceIndexCreation = false) // Create or update the index in the background
            }
        }.start()

        // Call the FastFinderApp composable to display the main content
        FastFinderApp(dbManager)
    }
}