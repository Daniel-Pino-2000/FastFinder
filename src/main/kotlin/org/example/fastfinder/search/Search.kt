package org.example.fastfinder.search

import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.WildcardQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.model.SystemItem
import org.example.fastfinder.util.Logger
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.AccessDeniedException

private const val MAX_RESULTS = 5000

/**
 * Searches either the Lucene index maintained by [dbManager], or - when a
 * directory is supplied - walks that directory directly.
 *
 * Keeps its Lucene [Directory]/[SearcherManager] open across searches so
 * repeated queries don't reopen the index from disk each time; call [close]
 * when done with this instance (e.g. on app shutdown).
 */
class Search(private val dbManager: DBManager) : AutoCloseable {
    private var openDirectory: Directory? = null
    private var searcherManager: SearcherManager? = null

    fun search(query: String, customSearchDirectory: File? = null): List<SystemItem> {
        return if (customSearchDirectory != null) {
            searchInDirectory(query, customSearchDirectory)
        } else {
            searchIndex(query)
        }
    }

    override fun close() {
        searcherManager?.close()
        openDirectory?.close()
        searcherManager = null
        openDirectory = null
    }

    private fun searchIndex(query: String): List<SystemItem> {
        if (dbManager.isFirstIndexCreation || dbManager.isIndexing.value) {
            Logger.info("Index is not ready yet; skipping search.")
            return emptyList()
        }

        val terms = query.toSearchTerms()
        if (terms.isEmpty()) return emptyList()

        val manager = acquireSearcherManager() ?: return emptyList()

        return try {
            manager.maybeRefresh()
            val searcher = manager.acquire()
            try {
                val booleanQuery = BooleanQuery.Builder().apply {
                    terms.forEach { term -> add(WildcardQuery(Term("name", "*$term*")), BooleanClause.Occur.MUST) }
                }.build()

                val topDocs = searcher.search(booleanQuery, MAX_RESULTS)
                topDocs.scoreDocs.mapNotNull { scoreDoc ->
                    val doc = searcher.doc(scoreDoc.doc)
                    val path = doc.get("path") ?: return@mapNotNull null
                    SystemItem(
                        itemPath = path,
                        isFile = doc.get("isFile")?.toBoolean() ?: false,
                        itemSize = doc.get("sizeDisplay")?.toLongOrNull()
                    )
                }
            } finally {
                manager.release(searcher)
            }
        } catch (e: Exception) {
            Logger.error("Error during search", e)
            emptyList()
        }
    }

    private fun acquireSearcherManager(): SearcherManager? {
        searcherManager?.let { return it }
        return try {
            val defaultDirectory = dbManager.indexPath.toFile()
            require(defaultDirectory.exists() && defaultDirectory.isDirectory) {
                "Index path ${defaultDirectory.absolutePath} is invalid."
            }
            val opened = FSDirectory.open(dbManager.indexPath).also { openDirectory = it }
            SearcherManager(opened, null).also { searcherManager = it }
        } catch (e: Exception) {
            Logger.error("Failed to initialize SearcherManager", e)
            null
        }
    }

    private fun searchInDirectory(query: String, targetDirectory: File): List<SystemItem> {
        require(targetDirectory.exists() && targetDirectory.isDirectory) {
            "Provided path is not a valid directory: ${targetDirectory.absolutePath}"
        }

        val terms = query.toSearchTerms()
        if (terms.isEmpty()) return emptyList()
        val matches = mutableListOf<SystemItem>()

        try {
            Files.walkFileTree(targetDirectory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        if (matchesAllTerms(file.fileName.toString(), terms)) {
                            matches.add(SystemItem(file.toAbsolutePath().toString(), isFile = true, itemSize = attrs.size()))
                        }
                    } catch (e: AccessDeniedException) {
                        Logger.warn("Access denied to file: $file")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val name = dir.fileName?.toString().orEmpty()
                    if (matchesAllTerms(name, terms)) {
                        matches.add(SystemItem(dir.toAbsolutePath().toString(), isFile = false, itemSize = null))
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    Logger.warn("Failed to access: $file (${exc.message})")
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            Logger.error("Error walking through directory ${targetDirectory.absolutePath}", e)
        }

        return matches
    }

    private fun String.toSearchTerms(): List<String> =
        trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun matchesAllTerms(name: String, terms: List<String>): Boolean {
        val lower = name.lowercase()
        return terms.all { lower.contains(it) }
    }
}
