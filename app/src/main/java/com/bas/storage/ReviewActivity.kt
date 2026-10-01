package com.bas.storage

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Environment
import android.provider.MediaStore
import android.text.Html
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.max

class ReviewActivity : Activity() {

    private lateinit var titleView: TextView
    private lateinit var progressView: TextView
    private lateinit var statsView: TextView
    private lateinit var infoView: TextView
    private lateinit var previewScroll: ScrollView
    private lateinit var previewBox: LinearLayout
    private lateinit var previousButton: Button
    private lateinit var keepButton: Button
    private lateinit var deleteButton: Button

    private var index = 0
    private var deletedCount = 0
    private var keptCount = 0
    private var deletedBytes = 0L
    private var pendingDelete: StorageItem? = null

    private var mediaPlayer: MediaPlayer? = null
    private var videoView: VideoView? = null
    private var pdfPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ReviewSession.items.isEmpty()) {
            finish()
            return
        }

        buildUi()
        renderCurrent()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(246, 247, 248))
        }

        titleView = TextView(this).apply {
            text = ReviewSession.title
            textSize = 22f
            setTypeface(typeface, 1)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(13, 75, 53))
        }

        progressView = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(70, 80, 75))
            setPadding(0, dp(4), 0, dp(2))
        }

        statsView = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 100, 95))
            setPadding(0, 0, 0, dp(8))
        }

        infoView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(23, 33, 28))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.END
        }

        previewScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        }

        previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        previewScroll.addView(previewBox)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        previousButton = actionButton("السابق").apply {
            setOnClickListener {
                if (index > 0) {
                    index--
                    renderCurrent()
                }
            }
        }

        keepButton = actionButton("احتفاظ / التالي").apply {
            setOnClickListener { keepAndNext() }
        }

        deleteButton = actionButton("حذف").apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(179, 38, 30))
            setOnClickListener { deleteCurrent() }
        }

        buttons.addView(previousButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        })
        buttons.addView(keepButton, LinearLayout.LayoutParams(0, -2, 1.25f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        })
        buttons.addView(deleteButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        })

        root.addView(titleView)
        root.addView(progressView)
        root.addView(statsView)
        root.addView(infoView)
        root.addView(previewScroll)
        root.addView(buttons)

        setContentView(root)
    }

    private fun renderCurrent() {
        releasePlayers()
        previewBox.removeAllViews()
        pdfPageIndex = 0

        val items = ReviewSession.items
        if (items.isEmpty() || index >= items.size) {
            finishSession()
            return
        }

        val item = items[index]
        titleView.text = ReviewSession.title
        progressView.text = "${index + 1} / ${items.size}"
        statsView.text = "حذفت: $deletedCount  •  احتفظت: $keptCount  •  وفّرت: ${fmt(deletedBytes)}"
        previousButton.isEnabled = index > 0

        infoView.text = buildString {
            append(item.name)
            append("\n")
            append(fmt(item.size))
            append("  •  ")
            append(date(item.modifiedSeconds))
            append("\n")
            append(item.path ?: item.relativePath)
        }

        renderPreview(item)
    }

    private fun renderPreview(item: StorageItem) {
        val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = item.mime ?: guessMime(item.name)

        when {
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "heic", "gif") ->
                renderImage(item)

            mime.startsWith("video/") || ext in setOf("mp4", "mkv", "mov", "avi", "3gp", "webm") ->
                renderVideo(item)

            ext == "pdf" || mime == "application/pdf" ->
                renderPdf(item)

            mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "wav", "ogg", "opus", "aac") ->
                renderAudio(item)

            ext in setOf("txt", "log", "csv", "json", "xml", "md", "html", "htm") ->
                renderText(readPlainText(item))

            ext == "docx" ->
                renderText(extractZipXmlText(item) { it == "word/document.xml" })

            ext == "pptx" ->
                renderText(extractZipXmlText(item) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") })

            ext == "xlsx" ->
                renderText(extractZipXmlText(item) {
                    it == "xl/sharedStrings.xml" || (it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml"))
                })

            else -> renderUnsupported(item)
        }
    }

    private fun renderImage(item: StorageItem) {
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.rgb(245, 245, 245))
        }

        val bmp = decodeSampledBitmap(item, 1600, 1600)
        if (bmp != null) {
            image.setImageBitmap(bmp)
            previewBox.addView(image, LinearLayout.LayoutParams(-1, -2))
        } else {
            renderMessage("تعذر عرض هذه الصورة.")
        }
    }

    private fun renderVideo(item: StorageItem) {
        val video = VideoView(this)
        videoView = video
        val controller = MediaController(this)
        controller.setAnchorView(video)
        video.setMediaController(controller)

        if (item.path != null) {
            video.setVideoPath(item.path)
        } else if (item.uri != null) {
            video.setVideoURI(item.uri)
        }

        previewBox.addView(video, LinearLayout.LayoutParams(-1, dp(420)))
        previewBox.addView(TextView(this).apply {
            text = "اضغط ▶ لمشاهدة الفيديو هنا داخل BAS"
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun renderPdf(item: StorageItem) {
        renderPdfPage(item, pdfPageIndex)
    }

    private fun renderPdfPage(item: StorageItem, pageIndex: Int) {
        previewBox.removeAllViews()

        val rendered = try {
            openPdfDescriptor(item)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount
                    if (count == 0) return@use null
                    val safeIndex = pageIndex.coerceIn(0, count - 1)
                    renderer.openPage(safeIndex).use { page ->
                        val targetWidth = 1200
                        val ratio = targetWidth.toFloat() / page.width.toFloat()
                        val targetHeight = max(1, (page.height * ratio).toInt())
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Triple(bitmap, count, safeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }

        if (rendered == null) {
            renderMessage("تعذر عرض ملف PDF داخل التطبيق.")
            return
        }

        val (bitmap, count, safeIndex) = rendered
        pdfPageIndex = safeIndex

        previewBox.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(-1, -2))

        val label = TextView(this).apply {
            text = "الصفحة ${safeIndex + 1} من $count"
            gravity = Gravity.CENTER
            textSize = 15f
            setPadding(0, dp(8), 0, dp(6))
        }
        previewBox.addView(label)

        if (count > 1) {
            val nav = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val prev = actionButton("صفحة سابقة").apply {
                isEnabled = safeIndex > 0
                setOnClickListener {
                    pdfPageIndex--
                    renderPdfPage(item, pdfPageIndex)
                }
            }

            val next = actionButton("صفحة تالية").apply {
                isEnabled = safeIndex < count - 1
                setOnClickListener {
                    pdfPageIndex++
                    renderPdfPage(item, pdfPageIndex)
                }
            }

            nav.addView(prev, LinearLayout.LayoutParams(0, -2, 1f))
            nav.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
            previewBox.addView(nav)
        }
    }

    private fun renderAudio(item: StorageItem) {
        val label = TextView(this).apply {
            text = "ملف صوتي\n${item.name}\n${fmt(item.size)}"
            gravity = Gravity.CENTER
            textSize = 18f
            setPadding(0, dp(40), 0, dp(20))
        }
        previewBox.addView(label)

        val play = actionButton("تشغيل")
        play.setOnClickListener {
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer().apply {
                        if (item.path != null) {
                            setDataSource(item.path)
                        } else if (item.uri != null) {
                            setDataSource(this@ReviewActivity, item.uri)
                        }
                        prepare()
                        start()
                    }
                    play.text = "إيقاف"
                } catch (_: Exception) {
                    Toast.makeText(this, "تعذر تشغيل الملف الصوتي", Toast.LENGTH_LONG).show()
                }
            } else {
                releaseAudio()
                play.text = "تشغيل"
            }
        }
        previewBox.addView(play)
    }

    private fun renderText(text: String) {
        if (text.isBlank()) {
            renderMessage("لم أجد نصًا قابلًا للعرض داخل هذا الملف.")
            return
        }

        previewBox.addView(TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.rgb(25, 25, 25))
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        })
    }

    private fun renderUnsupported(item: StorageItem) {
        renderMessage(
            "هذا النوع لا يملك معاينة داخلية كاملة بعد.\n\n" +
            "${item.name}\n${fmt(item.size)}\n\n" +
            "يمكنك مع ذلك اختيار «حذف» أو «احتفاظ / التالي» من أسفل الشاشة."
        )
    }

    private fun renderMessage(message: String) {
        previewBox.addView(TextView(this).apply {
            text = message
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(60), dp(20), dp(60))
        })
    }

    private fun keepAndNext() {
        keptCount++
        if (index < ReviewSession.items.size - 1) {
            index++
            renderCurrent()
        } else {
            finishSession()
        }
    }

    private fun deleteCurrent() {
        val items = ReviewSession.items
        if (items.isEmpty() || index >= items.size) return

        val item = items[index]

        // الأفضل: إذا كانت صلاحية All files access مفعلة نحذف الملف الحقيقي مباشرة.
        // هذا يمنع نوافذ Android المتكررة ويحقق: حذف -> الملف التالي فورًا.
        val directFile = directFileFor(item)
        if (directFile != null && directFile.exists()) {
            val deleted = try { directFile.delete() } catch (_: Exception) { false }
            if (deleted) {
                onDeleted(item)
                return
            }
        }

        val uri = item.uri
        if (uri == null) {
            Toast.makeText(
                this,
                "تعذر تحديد الملف. فعّل «وصول كامل للملفات» ثم أعد فحص الجهاز.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (Build.VERSION.SDK_INT >= 30) {
            val mediaUri = typedMediaUri(item, uri)
            if (mediaUri != null) {
                try {
                    pendingDelete = item
                    val request: PendingIntent =
                        MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))
                    startIntentSenderForResult(request.intentSender, 901, null, 0, 0, 0)
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "تعذر طلب حذف الملف: ${e.message ?: ""}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                try {
                    if (contentResolver.delete(uri, null, null) > 0) {
                        onDeleted(item)
                    } else {
                        Toast.makeText(
                            this,
                            "هذا الملف يحتاج «وصول كامل للملفات» للحذف المباشر.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (_: SecurityException) {
                    Toast.makeText(
                        this,
                        "فعّل «وصول كامل للملفات» ثم أعد الفحص لحذف هذا النوع مباشرة.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "تعذر حذف الملف: ${e.message ?: ""}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            try {
                if (contentResolver.delete(uri, null, null) > 0) {
                    onDeleted(item)
                } else {
                    Toast.makeText(this, "لم يتم حذف الملف", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "تعذر حذف الملف: ${e.message ?: ""}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun directFileFor(item: StorageItem): File? {
        item.path?.let { return File(it) }

        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            val relative = item.relativePath.trimStart('/')
            if (relative.isNotBlank()) {
                return File(Environment.getExternalStorageDirectory(), relative + item.name)
            }
        }
        return null
    }

    private fun typedMediaUri(item: StorageItem, original: Uri): Uri? {
        val mime = item.mime ?: guessMime(item.name)
        val id = try { ContentUris.parseId(original) } catch (_: Exception) { return null }

        return when {
            mime.startsWith("image/") ->
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

            mime.startsWith("video/") ->
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

            mime.startsWith("audio/") ->
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

            else -> null
        }
    }

    private fun onDeleted(item: StorageItem) {
        deletedCount++
        deletedBytes += item.size

        val current = ReviewSession.items.indexOf(item)
        if (current >= 0) {
            ReviewSession.items.removeAt(current)
            if (index >= ReviewSession.items.size) {
                index = (ReviewSession.items.size - 1).coerceAtLeast(0)
            }
        }

        if (ReviewSession.items.isEmpty()) {
            finishSession()
        } else {
            renderCurrent()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 901) {
            val item = pendingDelete
            pendingDelete = null
            if (resultCode == RESULT_OK && item != null) {
                onDeleted(item)
            } else {
                renderCurrent()
            }
        }
    }

    private fun finishSession() {
        releasePlayers()
        AlertDialog.Builder(this)
            .setTitle("انتهت جلسة المراجعة")
            .setMessage(
                "تم حذف $deletedCount ملف\n" +
                "تم الاحتفاظ بـ $keptCount ملف\n" +
                "المساحة التي وفّرتها: ${fmt(deletedBytes)}"
            )
            .setCancelable(false)
            .setPositiveButton("إنهاء") { _, _ ->
                setResult(RESULT_OK)
                finish()
            }
            .show()
    }

    private fun decodeSampledBitmap(item: StorageItem, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInput(item)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            openInput(item)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: Exception) {
            null
        }
    }

    private fun readPlainText(item: StorageItem): String {
        return try {
            openInput(item)?.use { input ->
                val bytes = ByteArray(200_000)
                val count = input.read(bytes)
                if (count <= 0) "" else String(bytes, 0, count, Charsets.UTF_8)
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractZipXmlText(item: StorageItem, accept: (String) -> Boolean): String {
        return try {
            val out = StringBuilder()
            openInput(item)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (accept(entry.name)) {
                            val buffer = ByteArray(8192)
                            val entryText = StringBuilder()
                            var total = 0
                            while (total < 250_000) {
                                val n = zip.read(buffer)
                                if (n <= 0) break
                                entryText.append(String(buffer, 0, n, Charsets.UTF_8))
                                total += n
                            }
                            val cleaned = entryText.toString()
                                .replace(Regex("</(w:p|a:p|row|si)>"), "\n")
                                .replace(Regex("<[^>]+>"), " ")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&quot;", "\"")
                                .replace("&#39;", "'")
                                .replace(Regex("[ \\t]+"), " ")
                                .replace(Regex("\n{3,}"), "\n\n")
                                .trim()

                            if (cleaned.isNotBlank()) {
                                if (out.isNotEmpty()) out.append("\n\n")
                                out.append(cleaned)
                            }
                        }
                        zip.closeEntry()
                        if (out.length > 200_000) break
                    }
                }
            }
            out.take(200_000).toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun openInput(item: StorageItem): InputStream? {
        return when {
            item.path != null -> FileInputStream(File(item.path))
            item.uri != null -> contentResolver.openInputStream(item.uri)
            else -> null
        }
    }

    private fun openPdfDescriptor(item: StorageItem): ParcelFileDescriptor? {
        return when {
            item.path != null -> ParcelFileDescriptor.open(File(item.path), ParcelFileDescriptor.MODE_READ_ONLY)
            item.uri != null -> contentResolver.openFileDescriptor(item.uri, "r")
            else -> null
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun releaseAudio() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun releasePlayers() {
        releaseAudio()
        try { videoView?.stopPlayback() } catch (_: Exception) {}
        videoView = null
    }

    override fun onDestroy() {
        releasePlayers()
        super.onDestroy()
    }

    private fun actionButton(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun fmt(bytes: Long): String = Formatter.formatFileSize(this, bytes)

    private fun date(sec: Long): String =
        if (sec <= 0) "تاريخ غير معروف"
        else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sec * 1000))

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
