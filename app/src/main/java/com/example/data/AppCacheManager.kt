package com.example.data

import android.content.Context
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object AppCacheManager {

    /**
     * Calculates the total size in bytes of the application's real cache directories.
     */
    suspend fun getAppCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        try {
            totalSize += getDirSize(context.cacheDir)
            totalSize += getDirSize(context.codeCacheDir)
            context.externalCacheDir?.let { totalSize += getDirSize(it) }
        } catch (_: Exception) {}
        totalSize
    }

    /**
     * Clears all real cache from internal storage, external cache, and Coil image caches.
     * Returns the total number of bytes freed.
     */
    suspend fun clearRealAppCache(context: Context): Long = withContext(Dispatchers.IO) {
        var totalCleared = 0L
        try {
            // 1. Clear Coil memory & disk caches
            try {
                Coil.imageLoader(context).memoryCache?.clear()
                Coil.imageLoader(context).diskCache?.clear()
            } catch (_: Exception) {}

            // 2. Clear Internal Cache directory
            totalCleared += deleteDirectoryContents(context.cacheDir)

            // 3. Clear Code Cache directory
            totalCleared += deleteDirectoryContents(context.codeCacheDir)

            // 4. Clear External Cache directory (if present)
            context.externalCacheDir?.let {
                totalCleared += deleteDirectoryContents(it)
            }
        } catch (_: Exception) {}
        totalCleared
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        try {
            val children = dir.listFiles() ?: return 0L
            for (child in children) {
                size += if (child.isDirectory) {
                    getDirSize(child)
                } else {
                    child.length()
                }
            }
        } catch (_: Exception) {}
        return size
    }

    private fun deleteDirectoryContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytesFreed = 0L
        try {
            val files = dir.listFiles() ?: return 0L
            for (file in files) {
                if (file.isDirectory) {
                    bytesFreed += deleteDirectoryContents(file)
                    try { file.delete() } catch (_: Exception) {}
                } else {
                    bytesFreed += file.length()
                    try { file.delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return bytesFreed
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0.0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 0.1 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
