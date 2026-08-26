package org.example.fastfinder.model

data class SystemItem(
    val itemPath: String,
    val isFile: Boolean,
    val itemSize: Long?
)

enum class SearchMode {
    FILES,
    DIRECTORIES,
    ALL
}

enum class SearchFilter {
    DOCUMENT,
    AUDIO,
    IMAGE,
    VIDEO,
    ALL,
    EXECUTABLE
}

enum class SortBy {
    NAME,
    SIZE,
    TYPE
}

/** Bucketed size ranges (inclusive on both ends) for narrowing file search results. */
enum class SizeFilter(val minBytes: Long, val maxBytes: Long) {
    ANY(0, Long.MAX_VALUE),
    UNDER_10KB(0, 10 * 1024 - 1),
    KB10_TO_MB1(10 * 1024, 1024 * 1024 - 1),
    MB1_TO_MB100(1024 * 1024, 100 * 1024 * 1024 - 1),
    MB100_TO_GB1(100 * 1024 * 1024, 1024L * 1024 * 1024 - 1),
    OVER_1GB(1024L * 1024 * 1024, Long.MAX_VALUE),
}
