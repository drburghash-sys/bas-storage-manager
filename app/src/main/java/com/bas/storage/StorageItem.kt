package com.bas.storage

import android.net.Uri

data class StorageItem(
    val uri: Uri?,
    val path: String?,
    val name: String,
    val size: Long,
    val modifiedSeconds: Long,
    val mime: String?,
    val relativePath: String,
    val category: Category
) {
    enum class Category { IMAGE, VIDEO, WHATSAPP, DOWNLOAD, DOCUMENT, AUDIO, OTHER }
}
