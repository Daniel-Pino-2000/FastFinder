package org.example.fastfinder.util

import java.nio.file.Path
import java.nio.file.Paths

/** Where FastFinder stores its own data: the index, state file, and logs. */
object AppPaths {
    val root: Path by lazy {
        val appData = System.getenv("APPDATA")
        val base = if (appData != null) Paths.get(appData) else Paths.get(System.getProperty("user.home"))
        base.resolve("FastFinder")
    }
}
