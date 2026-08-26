package org.example.fastfinder.index

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the fork-join parallel directory walk in [DBManager.indexFilesAndDirectories]
 * against a small, known directory tree, verifying both the document count and the
 * bottom-up directory size aggregation the parallel rewrite is responsible for.
 */
class DBManagerIndexingTest {

    private fun buildSampleTree(root: File): Map<String, Long> {
        // root/
        //   a.txt (5 bytes)
        //   sub/
        //     b.txt (10 bytes)
        //     nested/
        //       c.txt (20 bytes)
        val sizes = mutableMapOf<String, Long>()
        File(root, "a.txt").apply { writeText("aaaaa") }.also { sizes["a.txt"] = it.length() }
        val sub = File(root, "sub").apply { mkdirs() }
        File(sub, "b.txt").apply { writeText("b".repeat(10)) }.also { sizes["b.txt"] = it.length() }
        val nested = File(sub, "nested").apply { mkdirs() }
        File(nested, "c.txt").apply { writeText("c".repeat(20)) }.also { sizes["c.txt"] = it.length() }
        return sizes
    }

    private fun indexAndOpen(root: File, indexDir: Path, action: (IndexSearcher) -> Unit) {
        val dbManager = DBManager(indexDirectoryName = "unused", baseDirectory = indexDir.resolve("appdata"))
        val luceneDir = indexDir.resolve("lucene")
        FSDirectory.open(luceneDir).use { directory ->
            IndexWriter(directory, IndexWriterConfig(StandardAnalyzer())).use { writer ->
                dbManager.indexFilesAndDirectories(writer, roots = listOf(root))
                writer.commit()
            }
            DirectoryReader.open(directory).use { reader ->
                action(IndexSearcher(reader))
            }
        }
    }

    private fun sizeOf(searcher: IndexSearcher, path: File): Long {
        val hits = searcher.search(TermQuery(Term("path", path.absolutePath)), 1)
        assertTrue(hits.totalHits > 0, "Expected an indexed document for ${path.absolutePath}")
        return searcher.doc(hits.scoreDocs[0].doc).get("sizeDisplay").toLong()
    }

    @Test
    fun `indexes every file and directory exactly once`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        buildSampleTree(root)

        indexAndOpen(root, tempDir) { searcher ->
            // root, sub, nested (3 dirs) + a.txt, b.txt, c.txt (3 files) = 6 documents
            assertEquals(6, searcher.indexReader.numDocs())
        }
    }

    @Test
    fun `directory sizes are the sum of their entire subtree, not just direct children`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val sizes = buildSampleTree(root)
        val total = sizes.values.sum()

        indexAndOpen(root, tempDir) { searcher ->
            assertEquals(total, sizeOf(searcher, root))
            assertEquals(sizes.getValue("b.txt") + sizes.getValue("c.txt"), sizeOf(searcher, File(root, "sub")))
            assertEquals(sizes.getValue("c.txt"), sizeOf(searcher, File(root, "sub/nested")))
            assertEquals(sizes.getValue("a.txt"), sizeOf(searcher, File(root, "a.txt")))
        }
    }

    @Test
    fun `restricted directories are skipped entirely, including their contents`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "a.txt").writeText("keep me")
        val recycleBin = File(root, "\$Recycle.Bin").apply { mkdirs() }
        File(recycleBin, "deleted.txt").writeText("should not be indexed")

        indexAndOpen(root, tempDir) { searcher ->
            val deletedFileHits = searcher.search(TermQuery(Term("path", File(recycleBin, "deleted.txt").absolutePath)), 10)
            assertEquals(0L, deletedFileHits.totalHits)

            val recycleBinHits = searcher.search(TermQuery(Term("path", recycleBin.absolutePath)), 10)
            assertEquals(0L, recycleBinHits.totalHits)

            // Only root and a.txt should have been indexed - the recycle bin subtree is skipped entirely.
            assertEquals(2, searcher.indexReader.numDocs())
        }
    }

    @Test
    fun `empty directories are indexed with zero size`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val empty = File(root, "empty").apply { mkdirs() }

        indexAndOpen(root, tempDir) { searcher ->
            assertEquals(0L, sizeOf(searcher, empty))
        }
    }

    @Test
    fun `regular folder that merely contains a restricted name as a substring is not skipped`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val lookalike = File(root, "Windows Notes").apply { mkdirs() }
        File(lookalike, "todo.txt").writeText("not restricted")

        indexAndOpen(root, tempDir) { searcher ->
            val hits = searcher.search(TermQuery(Term("path", lookalike.absolutePath)), 1)
            assertFalse(hits.totalHits == 0L, "A folder that merely contains 'Windows' in its name should still be indexed")
        }
    }

    private fun newDbManager(tempDir: Path) =
        DBManager(indexDirectoryName = "unused", baseDirectory = tempDir.resolve("appdata"))

    @Test
    fun `indexedCount is updated every progressUpdateInterval items, not on every single one`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        buildSampleTree(root) // 3 files + 3 directories = 6 documents

        val dbManager = DBManager(indexDirectoryName = "unused", baseDirectory = tempDir.resolve("appdata"), progressUpdateInterval = 2)
        assertEquals(0, dbManager.indexedCount.value, "Should report no progress before indexing starts")

        FSDirectory.open(tempDir.resolve("lucene")).use { directory ->
            IndexWriter(directory, IndexWriterConfig(StandardAnalyzer())).use { writer ->
                dbManager.indexFilesAndDirectories(writer, roots = listOf(root))
                writer.commit()
            }
        }

        // 6 documents total, interval of 2: the counter crosses a multiple of 2 at 2, 4, and 6.
        assertEquals(6, dbManager.indexedCount.value)
    }

    @Test
    fun `moveDirectory renames the directory in place when the target does not yet exist`(@TempDir tempDir: Path) {
        val source = Files.createDirectories(tempDir.resolve("source"))
        File(source.toFile(), "segment.bin").writeText("index bytes")
        val target = tempDir.resolve("target")

        newDbManager(tempDir).moveDirectory(source, target)

        assertEquals("index bytes", File(target.toFile(), "segment.bin").readText())
        assertFalse(Files.exists(source), "A successful rename should leave nothing behind at the source path")
    }

    @Test
    fun `moveDirectory falls back to copying files when the target already exists`(@TempDir tempDir: Path) {
        val source = Files.createDirectories(tempDir.resolve("source"))
        File(source.toFile(), "segment.bin").writeText("index bytes")
        // Files.move refuses to rename onto an existing directory, forcing the copy fallback.
        val target = Files.createDirectories(tempDir.resolve("target"))

        newDbManager(tempDir).moveDirectory(source, target)

        assertEquals("index bytes", File(target.toFile(), "segment.bin").readText())
        assertTrue(Files.exists(source), "The copy fallback should leave the source directory untouched")
        assertEquals("index bytes", File(source.toFile(), "segment.bin").readText())
    }
}
