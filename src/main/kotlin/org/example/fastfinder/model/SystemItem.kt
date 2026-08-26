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
