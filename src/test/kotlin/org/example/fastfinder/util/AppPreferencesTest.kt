package org.example.fastfinder.util

import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SortBy
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferencesTest {

    @Test
    fun `loading a missing file falls back to defaults`(@TempDir tempDir: Path) {
        val loaded = AppPreferencesStore.load(tempDir.resolve("does_not_exist.properties"))

        assertEquals(AppPreferences(), loaded)
    }

    @Test
    fun `round-trips every field through save and load`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("nested/preferences.properties")
        val saved = AppPreferences(
            darkTheme = true,
            searchMode = SearchMode.DIRECTORIES,
            resultFilter = SearchFilter.VIDEO,
            sizeFilter = SizeFilter.MB1_TO_MB100,
            sortBy = SortBy.SIZE,
            sortAscending = false,
            windowWidth = 1440,
            windowHeight = 900,
        )

        AppPreferencesStore.save(saved, file)
        val loaded = AppPreferencesStore.load(file)

        assertEquals(saved, loaded)
    }

    @Test
    fun `a corrupted file falls back to defaults instead of throwing`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("preferences.properties")
        file.toFile().writeText("darkTheme=not-a-boolean\nsortBy=NOT_A_REAL_SORT\n")

        val loaded = AppPreferencesStore.load(file)

        assertEquals(AppPreferences(), loaded)
    }
}
