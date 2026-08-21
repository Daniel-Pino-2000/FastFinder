package org.example.fastfinder.util

object Logger {
    fun info(message: String) = println("[INFO] $message")
    fun warn(message: String) = println("[WARN] $message")
    fun error(message: String, throwable: Throwable? = null) {
        println("[ERROR] $message")
        throwable?.printStackTrace()
    }
}
