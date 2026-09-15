package org.example.fastfinder.index

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.lucene.index.DirectoryReader
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
import kotlin.test.assertTrue

/**
 * Exercises [DBManager]'s live filesystem watcher end-to-end: create/modify/delete a file or
 * directory on disk and confirm the change is reflected in the live index without a full
 * rebuild. Polls the index (rather than asserting immediately) since the watcher batches
 * commits on a multi-second interval rather than committing per event.
 */
class DBManagerWatcherTest {

    private fun newDbManager(tempDir: Path) =
        DBManager(indexDirectoryName = "unused", baseDirectory = tempDir.resolve("appdata"))

    private fun awaitIndexingDone(dbManager: DBManager) = runBlocking {
        withTimeout(10_000) { dbManager.isIndexing.first { indexing -> !indexing } }
    }

    private fun awaitCondition(timeoutMs: Long = 8_000, intervalMs: Long = 100, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(intervalMs)
        }
        assertTrue(condition(), "Condition was not met within ${timeoutMs}ms")
    }

    private fun hitCountFor(indexPath: Path, path: File): Long =
        FSDirectory.open(indexPath).use { directory ->
            DirectoryReader.open(directory).use { reader ->
                IndexSearcher(reader).search(TermQuery(Term("path", path.absolutePath)), 1).totalHits
            }
        }

    private fun sizeDisplayFor(indexPath: Path, path: File): Long? =
        FSDirectory.open(indexPath).use { directory ->
            DirectoryReader.open(directory).use { reader ->
                val searcher = IndexSearcher(reader)
                val hits = searcher.search(TermQuery(Term("path", path.absolutePath)), 1)
                if (hits.totalHits == 0L) null else searcher.doc(hits.scoreDocs[0].doc).get("sizeDisplay")?.toLong()
            }
        }

    @Test
    fun `a file created after indexing is added to the live index without a rebuild`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val dbManager = newDbManager(tempDir)
        try {
            dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
            awaitIndexingDone(dbManager)

            val created = File(root, "created.txt").apply { writeText("hello") }

            awaitCondition { hitCountFor(dbManager.indexPath, created) > 0 }
        } finally {
            dbManager.close()
        }
    }

    @Test
    fun `a file modified after indexing has its size updated in the live index`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val target = File(root, "grows.txt").apply { writeText("a") }
        val dbManager = newDbManager(tempDir)
        try {
            dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
            awaitIndexingDone(dbManager)
            awaitCondition { sizeDisplayFor(dbManager.indexPath, target) == 1L }

            target.writeText("a".repeat(50))

            awaitCondition { sizeDisplayFor(dbManager.indexPath, target) == 50L }
        } finally {
            dbManager.close()
        }
    }

    @Test
    fun `a file deleted after indexing is removed from the live index`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val target = File(root, "removed.txt").apply { writeText("bye") }
        val dbManager = newDbManager(tempDir)
        try {
            dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
            awaitIndexingDone(dbManager)
            awaitCondition { hitCountFor(dbManager.indexPath, target) > 0 }

            target.delete()

            awaitCondition { hitCountFor(dbManager.indexPath, target) == 0L }
        } finally {
            dbManager.close()
        }
    }

    @Test
    fun `a file renamed after indexing appears only under its new name`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val original = File(root, "before.txt").apply { writeText("content") }
        val dbManager = newDbManager(tempDir)
        try {
            dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
            awaitIndexingDone(dbManager)
            awaitCondition { hitCountFor(dbManager.indexPath, original) > 0 }

            val renamed = File(root, "after.txt")
            assertTrue(original.renameTo(renamed), "Test setup: rename must succeed")

            awaitCondition { hitCountFor(dbManager.indexPath, renamed) > 0 }
            assertEquals(
                0L, hitCountFor(dbManager.indexPath, original),
                "Renaming should remove the old path's document, not leave it alongside the new one"
            )
        } finally {
            dbManager.close()
        }
    }

    @Test
    fun `createOrUpdateIndex rebuilds when the on-disk index predates the current schema version`(
        @TempDir tempDir: Path,
    ) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val appDataDir = tempDir.resolve("appdata")

        val firstManager = DBManager(indexDirectoryName = "unused", baseDirectory = appDataDir)
        try {
            firstManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
            awaitIndexingDone(firstManager)
        } finally {
            firstManager.close()
        }

        // Simulate a state file written before schema versioning existed - just the
        // isFirstIndexCreation line, no schema version line.
        val stateFile = appDataDir.resolve("unused").resolve("index_state.txt")
        Files.write(stateFile, listOf("false"))

        // Only a full rebuild - not the live watcher, which only reports events from the
        // moment it attaches - would ever pick this up.
        val addedBeforeRestart = File(root, "added_before_restart.txt").apply { writeText("new") }

        val secondManager = DBManager(indexDirectoryName = "unused", baseDirectory = appDataDir)
        try {
            secondManager.createOrUpdateIndex(roots = listOf(root))
            awaitIndexingDone(secondManager)

            assertTrue(
                hitCountFor(secondManager.indexPath, addedBeforeRestart) > 0,
                "A schema-version mismatch should force a full rebuild, not the normal skip-if-exists startup path",
            )
        } finally {
            secondManager.close()
        }
    }

    @Test
    fun `a directory pasted in with existing contents is indexed recursively`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        val dbManager = newDbManager(tempDir)
        try {
            dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
            awaitIndexingDone(dbManager)

            // Build the new subtree fully before it becomes visible under `root`, simulating
            // a folder moved/pasted in with pre-existing content rather than created empty.
            val staging = File(tempDir.toFile(), "staging").apply { mkdirs() }
            File(staging, "inner.txt").writeText("already here")
            val pastedInto = File(root, "pasted")
            assertTrue(staging.renameTo(pastedInto), "Test setup: rename into the watched root must succeed")
            val innerFile = File(pastedInto, "inner.txt")

            awaitCondition { hitCountFor(dbManager.indexPath, pastedInto) > 0 }
            assertEquals(1L, hitCountFor(dbManager.indexPath, innerFile))
        } finally {
            dbManager.close()
        }
    }
}
