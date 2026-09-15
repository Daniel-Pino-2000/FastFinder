package org.example.fastfinder.search

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.index.INDEX_SCHEMA_VERSION
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SystemItem
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
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

    @Test
    fun `custom directory search honors search mode and result filter`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = tempDir.toFile()
        File(root, "report.pdf").writeText("x")
        File(root, "report.png").writeText("x")
        File(root, "report_folder").mkdirs()

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))

        val filesOnly = search.search("report", customSearchDirectory = root, searchMode = SearchMode.FILES)
        assertEquals(setOf("report.pdf", "report.png"), filesOnly.map { it.itemPath.substringAfterLast(File.separatorChar) }.toSet())

        val foldersOnly = search.search("report", customSearchDirectory = root, searchMode = SearchMode.DIRECTORIES)
        assertEquals(listOf("report_folder"), foldersOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        val imagesOnly = search.search(
            "report", customSearchDirectory = root, searchMode = SearchMode.FILES, resultFilter = SearchFilter.IMAGE
        )
        assertEquals(listOf("report.png"), imagesOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        search.close()
    }

    @Test
    fun `custom directory search in ALL mode ignores a resultFilter and sizeFilter left over from FILES mode`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = tempDir.toFile()
        File(root, "report.pdf").writeText("x")
        File(root, "report.png").writeText("x")
        File(root, "report_folder").mkdirs()

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))

        // A resultFilter/sizeFilter combination that would hide every one of these files if
        // mistakenly applied outside FILES mode (EXECUTABLE matches none of them, and OVER_1GB
        // excludes every one of these tiny test files) - ALL mode must ignore both entirely,
        // exactly like the whole-index search path and the results list's own isVisible() do.
        val results = search.search(
            "report",
            customSearchDirectory = root,
            searchMode = SearchMode.ALL,
            resultFilter = SearchFilter.EXECUTABLE,
            sizeFilter = SizeFilter.OVER_1GB,
        )

        val names = results.map { it.itemPath.substringAfterLast(File.separatorChar) }.toSet()
        assertEquals(setOf("report.pdf", "report.png", "report_folder"), names)

        search.close()
    }

    @Test
    fun `custom directory search honors the size filter`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = tempDir.toFile()
        File(root, "item_small.txt").writeBytes(ByteArray(500)) // < 10 KB
        File(root, "item_large.bin").writeBytes(ByteArray(2 * 1024 * 1024)) // 1 MB - 100 MB

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))

        val smallOnly = search.search("item", customSearchDirectory = root, searchMode = SearchMode.FILES, sizeFilter = SizeFilter.UNDER_10KB)
        assertEquals(listOf("item_small.txt"), smallOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        val largeOnly = search.search("item", customSearchDirectory = root, searchMode = SearchMode.FILES, sizeFilter = SizeFilter.MB1_TO_MB100)
        assertEquals(listOf("item_large.bin"), largeOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        search.close()
    }

    /** Builds a real Lucene index (bypassing [DBManager.createOrUpdateIndex], which always walks real drives) so [Search]'s indexed-search query logic can be exercised directly. */
    private fun indexedSearchOver(baseDir: Path, root: File): Pair<DBManager, Search> {
        val indexDir = baseDir.resolve("test-index")
        Files.createDirectories(indexDir)
        Files.write(indexDir.resolve("index_state.txt"), listOf("false", INDEX_SCHEMA_VERSION.toString()))

        val dbManager = DBManager(indexDirectoryName = "test-index", baseDirectory = baseDir)
        FSDirectory.open(dbManager.indexPath).use { directory ->
            IndexWriter(directory, IndexWriterConfig(StandardAnalyzer())).use { writer ->
                dbManager.indexFilesAndDirectories(writer, roots = listOf(root))
                writer.commit()
            }
        }
        return dbManager to Search(dbManager)
    }

    @Test
    fun `indexed search finds files by their complete name, extension included`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        // A WildcardQuery matches against indexed *terms*, not the original string - if "name"
        // were analyzed (tokenized) rather than indexed as one literal value, a query spanning
        // what the analyzer treats as a word boundary would silently match nothing even though
        // the file is right there. Confirmed (by directly inspecting StandardAnalyzer's actual
        // token output) that this specifically bites a camera/phone photo's default name and any
        // hyphenated name - a plain "word.ext" name happens to survive analysis as one token, so
        // every other indexed-search test in this file (which only ever searches a short,
        // single-word fragment of a plain name) could never have caught this either way.
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "IMG_20240101_120000.jpg").writeText("x")
        File(root, "my-file-name.docx").writeText("x")

        val (_, search) = indexedSearchOver(appDataDir, root)
        val photoResult = search.search("IMG_20240101_120000.jpg")
        val hyphenatedResult = search.search("my-file-name.docx")
        search.close()

        fun names(items: List<SystemItem>) = items.map { it.itemPath.substringAfterLast(File.separatorChar) }
        assertEquals(listOf("IMG_20240101_120000.jpg"), names(photoResult))
        assertEquals(listOf("my-file-name.docx"), names(hyphenatedResult))
    }

    @Test
    fun `indexed search honors search mode and result filter`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "report.pdf").writeText("x")
        File(root, "report.png").writeText("x")
        File(root, "report_folder").mkdirs()

        val (_, search) = indexedSearchOver(appDataDir, root)

        val filesOnly = search.search("report", searchMode = SearchMode.FILES)
        assertEquals(setOf("report.pdf", "report.png"), filesOnly.map { it.itemPath.substringAfterLast(File.separatorChar) }.toSet())

        val foldersOnly = search.search("report", searchMode = SearchMode.DIRECTORIES)
        assertEquals(listOf("report_folder"), foldersOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        val imagesOnly = search.search("report", searchMode = SearchMode.FILES, resultFilter = SearchFilter.IMAGE)
        assertEquals(listOf("report.png"), imagesOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        search.close()
    }

    @Test
    fun `indexed search filters by type at the Lucene query level, not by post-filtering raw hits`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        // The wanted type is heavily outnumbered by a different type sharing the same
        // matched term, so this only passes if `type` is a query constraint (a term-level
        // filter) rather than something applied client-side after the hits come back -
        // the mechanism that used to let results silently drop past the raw hit cap.
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "item.png").writeText("x")
        repeat(50) { File(root, "item_$it.txt").writeText("x") }

        val (_, search) = indexedSearchOver(appDataDir, root)

        val imagesOnly = search.search("item", searchMode = SearchMode.FILES, resultFilter = SearchFilter.IMAGE)
        assertEquals(listOf("item.png"), imagesOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        search.close()
    }

    @Test
    fun `indexed search collapses duplicate documents for the same path`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        // An unclean shutdown mid-commit (the live watcher's delete+add pair for one path
        // straddling a forced kill) can leave more than one document for the same path in the
        // real index. Indexing the same file twice reproduces that here. Search must collapse
        // it to a single result - the UI list is keyed by path and would crash on a duplicate.
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "duplicate.txt").writeText("x")

        val indexDir = appDataDir.resolve("test-index")
        Files.createDirectories(indexDir)
        Files.write(indexDir.resolve("index_state.txt"), listOf("false", INDEX_SCHEMA_VERSION.toString()))
        val dbManager = DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir)
        FSDirectory.open(dbManager.indexPath).use { directory ->
            IndexWriter(directory, IndexWriterConfig(StandardAnalyzer())).use { writer ->
                dbManager.indexFilesAndDirectories(writer, roots = listOf(root))
                dbManager.indexFilesAndDirectories(writer, roots = listOf(root))
                writer.commit()
            }
        }

        val search = Search(dbManager)
        val results = search.search("duplicate")
        search.close()

        assertEquals(1, results.count { it.itemPath.endsWith("duplicate.txt") })
    }

    @Test
    fun `indexed search honors the size filter`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "item_small.txt").writeBytes(ByteArray(500)) // < 10 KB
        File(root, "item_large.bin").writeBytes(ByteArray(2 * 1024 * 1024)) // 1 MB - 100 MB

        val (_, search) = indexedSearchOver(appDataDir, root)

        val smallOnly = search.search("item", searchMode = SearchMode.FILES, sizeFilter = SizeFilter.UNDER_10KB)
        assertEquals(listOf("item_small.txt"), smallOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        val largeOnly = search.search("item", searchMode = SearchMode.FILES, sizeFilter = SizeFilter.MB1_TO_MB100)
        assertEquals(listOf("item_large.bin"), largeOnly.map { it.itemPath.substringAfterLast(File.separatorChar) })

        search.close()
    }

    @Test
    fun `custom directory search with exactMatch requires the full name, not just a substring`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = tempDir.toFile()
        File(root, "report.pdf").writeText("x")
        // Ends with "report.pdf" - a substring match, not an exact one.
        File(root, "quarterly_report.pdf").writeText("x")

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))

        val substringMatch = search.search("report.pdf", customSearchDirectory = root)
        assertEquals(
            setOf("report.pdf", "quarterly_report.pdf"),
            substringMatch.map { it.itemPath.substringAfterLast(File.separatorChar) }.toSet(),
        )

        val exact = search.search("report.pdf", customSearchDirectory = root, exactMatch = true)
        assertEquals(listOf("report.pdf"), exact.map { it.itemPath.substringAfterLast(File.separatorChar) })

        // Case-insensitive, matching every other name comparison in this file.
        val exactDifferentCase = search.search("REPORT.PDF", customSearchDirectory = root, exactMatch = true)
        assertEquals(
            listOf("report.pdf"),
            exactDifferentCase.map { it.itemPath.substringAfterLast(File.separatorChar) },
        )

        search.close()
    }

    @Test
    fun `indexed search with exactMatch requires the full name, not just a substring`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "report.pdf").writeText("x")
        File(root, "quarterly_report.pdf").writeText("x")

        val (_, search) = indexedSearchOver(appDataDir, root)

        val substringMatch = search.search("report.pdf")
        assertEquals(
            setOf("report.pdf", "quarterly_report.pdf"),
            substringMatch.map { it.itemPath.substringAfterLast(File.separatorChar) }.toSet(),
        )

        val exact = search.search("report.pdf", exactMatch = true)
        assertEquals(listOf("report.pdf"), exact.map { it.itemPath.substringAfterLast(File.separatorChar) })

        search.close()
    }

    @Test
    fun `exactMatch on a blank query still matches nothing`(
        @TempDir tempDir: Path,
        @TempDir appDataDir: Path,
    ) {
        val root = tempDir.toFile()
        File(root, "anything.txt").writeText("x")

        val search = Search(DBManager(indexDirectoryName = "test-index", baseDirectory = appDataDir))
        val results = search.search("   ", customSearchDirectory = root, exactMatch = true)
        search.close()

        assertTrue(results.isEmpty())
    }
}
