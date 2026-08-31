package org.example.fastfinder.search

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.model.SearchMode
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Reproduces a real bug: [Search] deliberately keeps its own [org.apache.lucene.store.Directory]
 * / [org.apache.lucene.search.SearcherManager] open across queries so repeated searches don't
 * reopen the index from disk every time. On Windows, that still-open (possibly memory-mapped)
 * reader can make [DBManager] unable to rename the live index directory aside when a rebuild
 * finishes, since the OS won't allow renaming a directory containing a file that's still mapped
 * - surfacing to the user as "Failed to finalize the new index". The fix (in FastFinderApp,
 * where the two are wired together) is to call [Search.close] before a rebuild starts, which
 * this test proves resolves it.
 */
class SearchRebuildInteractionTest {

    private fun newDbManager(tempDir: Path) =
        DBManager(indexDirectoryName = "unused", baseDirectory = tempDir.resolve("appdata"))

    private fun awaitIndexingDone(dbManager: DBManager) = runBlocking {
        withTimeout(15_000) { dbManager.isIndexing.first { indexing -> !indexing } }
    }

    @Test
    fun `closing Search before a rebuild lets the index finalize without error`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "a.txt").writeText("hello")
        val dbManager = newDbManager(tempDir)
        try {
            dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
            awaitIndexingDone(dbManager)

            val search = Search(dbManager)
            try {
                // Forces Search's SearcherManager/Directory open against the live index, exactly
                // as a real search from the UI would.
                search.search("a", searchMode = SearchMode.ALL)

                // This is the fix under test: without this close(), the rebuild below fails with
                // an AccessDeniedException trying to move the live index directory aside.
                search.close()

                dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
                awaitIndexingDone(dbManager)

                assertNull(dbManager.lastError.value, "Rebuild should succeed once Search has released its handle")
            } finally {
                search.close()
            }
        } finally {
            dbManager.close()
        }
    }

    // Proves the failure mode is real (not a hypothetical) before trusting the passing test
    // above: without closing Search first, the exact same rebuild fails.
    @Test
    fun `without closing Search first, the rebuild fails to finalize`(@TempDir tempDir: Path) {
        val root = File(tempDir.toFile(), "root").apply { mkdirs() }
        File(root, "a.txt").writeText("hello")
        val dbManager = newDbManager(tempDir)
        try {
            dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
            awaitIndexingDone(dbManager)

            val search = Search(dbManager)
            try {
                search.search("a", searchMode = SearchMode.ALL)

                dbManager.createOrUpdateIndex(forceIndexCreation = true, roots = listOf(root))
                awaitIndexingDone(dbManager)

                assertNotNull(
                    dbManager.lastError.value,
                    "Expected the rebuild to fail while Search still holds the index open",
                )
            } finally {
                search.close()
            }
        } finally {
            dbManager.close()
        }
    }
}
