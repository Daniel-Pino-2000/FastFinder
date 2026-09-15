package org.example.fastfinder.search

import org.apache.lucene.document.LongPoint
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.WildcardQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.example.fastfinder.index.DBManager
import org.example.fastfinder.model.SearchFilter
import org.example.fastfinder.model.SearchMode
import org.example.fastfinder.model.SizeFilter
import org.example.fastfinder.model.SystemItem
import org.example.fastfinder.util.Logger
import org.example.fastfinder.util.getFileType
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
    // Guards openDirectory/searcherManager themselves (not the SearcherManager's own use, which
    // is already thread-safe per its own contract) - search() runs on Dispatchers.IO while
    // FastFinderApp calls close() from a separate coroutine right before a rebuild starts.
    // Without this, close() nulling the fields mid-way through acquireSearcherManager() building
    // a new pair could silently leak the new FSDirectory/SearcherManager instead of using it.
    private val lock = Any()
    private var openDirectory: Directory? = null
    private var searcherManager: SearcherManager? = null

    fun search(
        query: String,
        customSearchDirectory: File? = null,
        searchMode: SearchMode = SearchMode.ALL,
        resultFilter: SearchFilter = SearchFilter.ALL,
        sizeFilter: SizeFilter = SizeFilter.ANY,
    ): List<SystemItem> {
        return if (customSearchDirectory != null) {
            searchInDirectory(query, customSearchDirectory, searchMode, resultFilter, sizeFilter)
        } else {
            searchIndex(query, searchMode, resultFilter, sizeFilter)
        }
    }

    override fun close() {
        synchronized(lock) {
            searcherManager?.close()
            openDirectory?.close()
            searcherManager = null
            openDirectory = null
        }
    }

    private fun searchIndex(query: String, searchMode: SearchMode, resultFilter: SearchFilter, sizeFilter: SizeFilter): List<SystemItem> {
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
                    when (searchMode) {
                        SearchMode.FILES -> add(TermQuery(Term("isFile", "true")), BooleanClause.Occur.MUST)
                        SearchMode.DIRECTORIES -> add(TermQuery(Term("isFile", "false")), BooleanClause.Occur.MUST)
                        SearchMode.ALL -> {}
                    }
                    if (searchMode == SearchMode.FILES && resultFilter != SearchFilter.ALL) {
                        add(TermQuery(Term("type", resultFilter.name.lowercase())), BooleanClause.Occur.MUST)
                    }
                    if (searchMode == SearchMode.FILES && sizeFilter != SizeFilter.ANY) {
                        add(LongPoint.newRangeQuery("size", sizeFilter.minBytes, sizeFilter.maxBytes), BooleanClause.Occur.MUST)
                    }
                }.build()

                val topDocs = searcher.search(booleanQuery, MAX_RESULTS)
                // distinctBy: an unclean shutdown mid-commit (e.g. the live watcher's delete+add
                // pair for one path straddling a forced kill) can leave more than one document for
                // the same path until the next full rebuild. The UI list is keyed by path, so a
                // duplicate here would crash it rather than just look odd - collapse to one per path.
                topDocs.scoreDocs.mapNotNull { scoreDoc ->
                    val doc = searcher.doc(scoreDoc.doc)
                    val path = doc.get("path") ?: return@mapNotNull null
                    SystemItem(
                        itemPath = path,
                        isFile = doc.get("isFile")?.toBoolean() ?: false,
                        itemSize = doc.get("sizeDisplay")?.toLongOrNull(),
                        itemDate = doc.get("modified")?.toLongOrNull()
                    )
                }.distinctBy { it.itemPath }
            } finally {
                manager.release(searcher)
            }
        } catch (e: Exception) {
            Logger.error("Error during search", e)
            emptyList()
        }
    }

    private fun acquireSearcherManager(): SearcherManager? = synchronized(lock) {
        searcherManager?.let { return@synchronized it }
        try {
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

    private fun searchInDirectory(
        query: String,
        targetDirectory: File,
        searchMode: SearchMode,
        resultFilter: SearchFilter,
        sizeFilter: SizeFilter,
    ): List<SystemItem> {
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
                        if (searchMode != SearchMode.DIRECTORIES &&
                            matchesAllTerms(file.fileName.toString(), terms) &&
                            matchesFileFilters(file, attrs, searchMode, resultFilter, sizeFilter)
                        ) {
                            matches.add(
                                SystemItem(
                                    file.toAbsolutePath().toString(),
                                    isFile = true,
                                    itemSize = attrs.size(),
                                    itemDate = attrs.lastModifiedTime().toMillis(),
                                )
                            )
                        }
                    } catch (e: AccessDeniedException) {
                        Logger.warn("Access denied to file: $file (${e.message})")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val name = dir.fileName?.toString().orEmpty()
                    if (searchMode != SearchMode.FILES && matchesAllTerms(name, terms)) {
                        matches.add(
                            SystemItem(
                                dir.toAbsolutePath().toString(),
                                isFile = false,
                                itemSize = null,
                                itemDate = attrs.lastModifiedTime().toMillis(),
                            )
                        )
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

    /**
     * resultFilter/sizeFilter only apply in FILES mode, matching searchIndex's own gating -
     * applying them unconditionally (as this used to) meant a stale non-ALL/non-ANY filter left
     * over from a previous FILES-mode search silently hid every non-matching file even in ALL
     * mode, where the filter controls are shown disabled and look inactive.
     */
    private fun matchesFileFilters(
        file: Path,
        attrs: BasicFileAttributes,
        searchMode: SearchMode,
        resultFilter: SearchFilter,
        sizeFilter: SizeFilter,
    ): Boolean {
        if (searchMode != SearchMode.FILES) return true
        val matchesType = resultFilter == SearchFilter.ALL || getFileType(file.toFile()) == resultFilter
        return matchesType && attrs.size() in sizeFilter.minBytes..sizeFilter.maxBytes
    }
}
