package org.example.fastfinder.index

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Files

/**
 * One-off, manually-run benchmark comparing the fork-join parallel directory walk
 * ([DBManager.indexFilesAndDirectories]) against a single-threaded run over the same
 * synthetic tree. This is a `main` function, not a `@Test` - it doesn't run as part of the
 * regular suite. Run it explicitly with `./gradlew benchmarkIndexing`.
 *
 * Results depend heavily on the machine's core count and storage speed; treat the numbers
 * as a demonstration of the parallel walk's scaling, not an absolute figure.
 */
fun main() {
    val cores = Runtime.getRuntime().availableProcessors()
    println("Available processors: $cores")

    val topLevelDirs = 30
    val subDirsPerTop = 10
    val filesPerSubDir = 100
    val totalFiles = topLevelDirs * subDirsPerTop * filesPerSubDir
    println("Building a synthetic tree of $totalFiles files across ${topLevelDirs * subDirsPerTop} directories...")
    val treeRoot = buildSyntheticTree(topLevelDirs, subDirsPerTop, filesPerSubDir)

    // One throwaway warm-up run so JIT compilation doesn't skew the timed runs below.
    timeIndexingRun(treeRoot, parallelism = cores)

    val singleThreaded = timeIndexingRun(treeRoot, parallelism = 1)
    val parallel = timeIndexingRun(treeRoot, parallelism = cores)

    treeRoot.deleteRecursively()

    println()
    println("=== Results ($totalFiles files, $cores cores) ===")
    println("Single-threaded (parallelism = 1): ${singleThreaded}ms")
    println("Fork-join (parallelism = $cores):  ${parallel}ms")
    println("Speedup: %.2fx".format(singleThreaded.toDouble() / parallel))
}

private fun buildSyntheticTree(topLevelDirs: Int, subDirsPerTop: Int, filesPerSubDir: Int): File {
    val treeRoot = Files.createTempDirectory("fastfinder_benchmark_tree_").toFile()
    repeat(topLevelDirs) { topIndex ->
        val topDir = File(treeRoot, "top_$topIndex").apply { mkdirs() }
        repeat(subDirsPerTop) { subIndex ->
            val subDir = File(topDir, "sub_$subIndex").apply { mkdirs() }
            repeat(filesPerSubDir) { fileIndex ->
                File(subDir, "file_$fileIndex.txt").writeText("benchmark")
            }
        }
    }
    return treeRoot
}

private fun timeIndexingRun(root: File, parallelism: Int): Long {
    val appDataDir = Files.createTempDirectory("fastfinder_benchmark_appdata_")
    val indexDir = Files.createTempDirectory("fastfinder_benchmark_index_")
    val dbManager = DBManager(indexDirectoryName = "unused", baseDirectory = appDataDir)
    try {
        FSDirectory.open(indexDir).use { directory ->
            IndexWriter(directory, IndexWriterConfig(StandardAnalyzer())).use { writer ->
                val start = System.nanoTime()
                dbManager.indexFilesAndDirectories(writer, roots = listOf(root), parallelism = parallelism)
                writer.commit()
                return (System.nanoTime() - start) / 1_000_000
            }
        }
    } finally {
        indexDir.toFile().deleteRecursively()
        appDataDir.toFile().deleteRecursively()
    }
}
