package com.bas.storage

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class StorageScanner(private val context: Context) {

    data class Result(
        val items: List<StorageItem>,
        val totalBytes: Long,
        val usedBytes: Long,
        val freeBytes: Long
    )

    fun scan(): Result {
        val stat = android.os.StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val items = if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            scanFilesDirectly(Environment.getExternalStorageDirectory())
        } else {
            scanMediaStore()
        }
        return Result(items, total, total - free, free)
    }

    private fun scanFilesDirectly(root: File): List<StorageItem> {
        val out = ArrayList<StorageItem>(4096)
        val blocked = listOf("/Android/data/", "/Android/obb/")
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val p = dir.absolutePath.replace('\\', '/') + "/"
            if (blocked.any { p.contains(it, ignoreCase = true) }) continue
            val children = try { dir.listFiles() } catch (_: Exception) { null } ?: continue
            for (f in children) {
                try {
                    if (f.isDirectory) {
                        if (!f.isHidden) stack.add(f)
                    } else if (f.isFile) {
                        val path = f.absolutePath.replace('\\', '/')
                        val category = classify(path, null)
                        out.add(
                            StorageItem(
                                uri = null,
                                path = f.absolutePath,
                                name = f.name,
                                size = f.length(),
                                modifiedSeconds = f.lastModified() / 1000,
                                mime = null,
                                relativePath = path.substringBeforeLast('/', ""),
                                category = category
                            )
                        )
                    }
                } catch (_: SecurityException) { }
            }
        }
        return out
    }

    private fun scanMediaStore(): List<StorageItem> {
        val result = ArrayList<StorageItem>(2048)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )
        val uri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val idIx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateIx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeIx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val relIx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            while (c.moveToNext()) {
                val id = c.getLong(idIx)
                val name = c.getString(nameIx) ?: "بدون اسم"
                val size = c.getLong(sizeIx)
                val modified = c.getLong(dateIx)
                val mime = c.getString(mimeIx)
                val rel = c.getString(relIx) ?: ""
                result.add(
                    StorageItem(
                        uri = ContentUris.withAppendedId(uri, id),
                        path = null,
                        name = name,
                        size = size,
                        modifiedSeconds = modified,
                        mime = mime,
                        relativePath = rel,
                        category = classify("$rel$name", mime)
                    )
                )
            }
        }
        return result
    }

    private fun classify(path: String, mime: String?): StorageItem.Category {
        val p = path.lowercase(Locale.ROOT)
        if (p.contains("/android/media/com.whatsapp/") || p.contains("/whatsapp/media/") ||
            p.contains("/android/media/com.whatsapp.w4b/")) return StorageItem.Category.WHATSAPP
        if (p.contains("/download/") || p.contains("/downloads/")) return StorageItem.Category.DOWNLOAD
        if (mime?.startsWith("image/") == true || p.matches(Regex(".*\\.(jpg|jpeg|png|webp|heic|gif)$"))) return StorageItem.Category.IMAGE
        if (mime?.startsWith("video/") == true || p.matches(Regex(".*\\.(mp4|mkv|mov|avi|3gp|webm)$"))) return StorageItem.Category.VIDEO
        if (mime?.startsWith("audio/") == true || p.matches(Regex(".*\\.(mp3|m4a|wav|ogg|opus|aac)$"))) return StorageItem.Category.AUDIO
        if (p.matches(Regex(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|zip|rar|7z|apk)$"))) return StorageItem.Category.DOCUMENT
        return StorageItem.Category.OTHER
    }

    fun hash(item: StorageItem): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val stream = when {
                item.uri != null -> context.contentResolver.openInputStream(item.uri)
                item.path != null -> File(item.path).inputStream()
                else -> null
            } ?: return null
            stream.use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    digest.update(buffer, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }
}
