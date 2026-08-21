package org.example.fastfinder.search

import org.example.fastfinder.index.DBManager
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTest {

    @Test
    fun `finds files and directories matching all terms, case-insensitively`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = tempDir.toFile()
        File(root, "Budget Report 2024.pdf").writeText("x")
        File(root, "vacation_photo.png").writeText("x")
        File(root, "budget_folder").mkdirs()
        File(root, "unrelated.txt").writeText("x")

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))
        val results = search.search("BUDGET", customSearchDirectory = root)
        search.close()

        val names = results.map { it.itemPath.substringAfterLast(File.separatorChar) }
        assertTrue("Budget Report 2024.pdf" in names)
        assertTrue("budget_folder" in names)
        assertTrue("vacation_photo.png" !in names)
        assertTrue("unrelated.txt" !in names)
    }

    @Test
    fun `requires every term to match (AND semantics)`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = tempDir.toFile()
        File(root, "quarterly budget report.pdf").writeText("x")
        File(root, "budget.pdf").writeText("x")

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))
        val results = search.search("quarterly budget", customSearchDirectory = root)
        search.close()

        assertEquals(1, results.size)
        assertTrue(results.single().itemPath.endsWith("quarterly budget report.pdf"))
    }

    @Test
    fun `file results report their size, directories do not`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = tempDir.toFile()
        File(root, "data.csv").writeText("12345")
        File(root, "data_folder").mkdirs()

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))
        val results = search.search("data", customSearchDirectory = root)
        search.close()

        assertEquals(5L, results.single { it.isFile }.itemSize)
        assertEquals(null, results.single { !it.isFile }.itemSize)
    }

    @Test
    fun `blank query matches nothing`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        File(tempDir.toFile(), "anything.txt").writeText("x")

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))
        val results = search.search("   ", customSearchDirectory = tempDir.toFile())
        search.close()

        assertEquals(0, results.size)
    }
}
