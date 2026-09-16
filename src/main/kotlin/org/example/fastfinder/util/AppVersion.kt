package org.example.fastfinder.util

import java.util.Properties

/**
 * The app's version, as shown in the UI (see StatusBar) - read from the `version.properties`
 * classpath resource that Gradle stamps with `project.version` at build time (see
 * `processResources` in build.gradle.kts), so it never drifts from the one place the version
 * itself is actually declared. Loaded once per process lifetime, the same best-effort pattern
 * Main.kt's window icon loading uses: a missing/unreadable resource just falls back to "dev"
 * rather than failing startup over a cosmetic detail.
 */
object AppVersion {
    val current: String by lazy {
        runCatching {
            object {}.javaClass.getResourceAsStream("/version.properties")?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }
        }.getOrNull() ?: "dev"
    }
}
