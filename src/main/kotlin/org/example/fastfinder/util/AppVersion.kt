package org.example.fastfinder.util

/**
 * The app's version, read from a resource generated at build time from build.gradle.kts's
 * `version` (see the `generateVersionResource` task) - the single source of truth, also used for
 * the installer's own version, so the two can never drift apart. Null only if that resource is
 * somehow missing (shouldn't happen in a real build), so callers get a graceful "don't show
 * anything" fallback instead of a crash.
 */
val appVersion: String? by lazy {
    runCatching {
        object {}.javaClass.getResourceAsStream("/version.txt")?.bufferedReader()?.readText()?.trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
