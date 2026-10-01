package com.bas.storage

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var scanner: StorageScanner
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var listBox: LinearLayout
    private var allItems: List<StorageItem> = emptyList()
    private val selected = LinkedHashSet<StorageItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = StorageScanner(this)
        buildUi()
        requestBasicPermissions()
    }

    private fun buildUi() {
        val bg = Color.rgb(246,247,248)
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(40))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "BAS Storage Manager"
            textSize = 27f
            setTextColor(Color.rgb(23,33,28))
            setTypeface(typeface, 1)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        root.addView(TextView(this).apply {
            text = "تنظيف ذكي — شاهد الملف ثم قرر"
            textSize = 15f
            setTextColor(Color.rgb(102,115,109))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(16))
        })

        status = cardText("لم يبدأ الفحص بعد")
        root.addView(status)

        root.addView(sectionTitle("التخزين المحلي"))
        root.addView(button("منح وصول كامل للملفات") { openAllFilesAccess() })
        root.addView(button("فحص الجهاز الآن") { runScan() })
        root.addView(button("أكبر الملفات") { showLarge() })
        root.addView(button("وسائط WhatsApp") { showCategory(StorageItem.Category.WHATSAPP, "وسائط WhatsApp") })
        root.addView(button("Downloads") { showCategory(StorageItem.Category.DOWNLOAD, "Downloads") })
        root.addView(button("الصور والفيديو") { showPhotosVideos() })
        root.addView(button("الملفات المتكررة") { findDuplicates() })

        root.addView(sectionTitle("Google"))
        root.addView(cardText(
            "Gmail وGoogle Drive موجودان الآن داخل مدير التخزين كأدوات سريعة. " +
            "الربط الكامل داخل التطبيق عبر Google API سيحتاج إعداد OAuth لمرة واحدة."
        ))
        root.addView(button("Gmail — المرفقات الأكبر من 10 MB") {
            openUrl("https://mail.google.com/mail/u/0/#search/has%3Aattachment+larger%3A10M")
        })
        root.addView(button("Gmail — مرفقات قديمة لأكثر من سنتين") {
            openUrl("https://mail.google.com/mail/u/0/#search/has%3Aattachment+older_than%3A2y")
        })
        root.addView(button("Google Drive — إدارة مساحة التخزين") {
            openUrl("https://drive.google.com/drive/quota")
        })
        root.addView(button("Google Drive — ملفاتي") {
            openUrl("https://drive.google.com/drive/my-drive")
        })

        root.addView(button("حذف المحدد بأمان") { deleteSelected() }.apply {
            setTextColor(Color.rgb(179,38,30))
        })

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(listBox)
        setContentView(scroll)
    }

    private fun requestBasicPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 100)
    }

    private fun openAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else requestBasicPermissions()
    }

    private fun runScan() {
        listBox.removeAllViews()
        status.text = "جارٍ فحص التخزين…"
        executor.execute {
            val r = scanner.scan()
            allItems = r.items
            val cats = allItems.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.size } }
            runOnUiThread {
                status.text = buildString {
                    append("المساحة الكلية: ${fmt(r.totalBytes)}\n")
                    append("المستخدم: ${fmt(r.usedBytes)}\n")
                    append("المتاح: ${fmt(r.freeBytes)}\n\n")
                    append("العناصر المفحوصة: ${allItems.size}\n")
                    append("WhatsApp: ${fmt(cats[StorageItem.Category.WHATSAPP] ?: 0)}\n")
                    append("الصور: ${fmt(cats[StorageItem.Category.IMAGE] ?: 0)}\n")
                    append("الفيديو: ${fmt(cats[StorageItem.Category.VIDEO] ?: 0)}\n")
                    append("Downloads: ${fmt(cats[StorageItem.Category.DOWNLOAD] ?: 0)}")
                }
            }
        }
    }

    private fun showLarge() {
        if (!ensureScan()) return
        val items = allItems.filter { it.size >= 100L * 1024 * 1024 }.sortedByDescending { it.size }
        showItems("الملفات الأكبر من 100 MB", items)
    }

    private fun showCategory(cat: StorageItem.Category, title: String) {
        if (!ensureScan()) return
        showItems(title, allItems.filter { it.category == cat }.sortedByDescending { it.size })
    }

    private fun showPhotosVideos() {
        if (!ensureScan()) return
        showItems("الصور والفيديو", allItems.filter {
            it.category == StorageItem.Category.IMAGE || it.category == StorageItem.Category.VIDEO
        }.sortedByDescending { it.size })
    }

    private fun ensureScan(): Boolean {
        if (allItems.isEmpty()) {
            Toast.makeText(this, "اضغط فحص الجهاز أولًا", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun showItems(title: String, items: List<StorageItem>) {
        listBox.removeAllViews()
        listBox.addView(TextView(this).apply {
            text = "$title — ${items.size} عنصر — ${fmt(items.sumOf { it.size })}"
            textSize = 19f
            setTypeface(typeface, 1)
            gravity = Gravity.END
            setPadding(0, dp(6), 0, dp(8))
        })

        val page = items.take(100)
        page.forEach { item -> addItemRow(item) }

        if (items.size > page.size) {
            listBox.addView(TextView(this).apply {
                text = "يعرض أول 100 عنصر لتسريع الشاشة. سنضيف التنقل بين الصفحات في التحديث القادم."
                setPadding(0, dp(12), 0, dp(12))
            })
        }
    }

    private fun addItemRow(item: StorageItem) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(3), 0, dp(3))
            }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val cb = CheckBox(this).apply {
            isChecked = selected.contains(item)
            setOnCheckedChangeListener { _, yes ->
                if (yes) selected.add(item) else selected.remove(item)
            }
        }

        val tx = TextView(this).apply {
            text = "${item.name}\n${fmt(item.size)} · ${date(item.modifiedSeconds)}"
            textSize = 15f
            setTextColor(Color.rgb(23,33,28))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openItem(item) }
        }

        val preview = Button(this).apply {
            text = "فتح"
            isAllCaps = false
            textSize = 13f
            setOnClickListener { openItem(item) }
        }

        top.addView(cb)
        top.addView(tx)
        top.addView(preview)

        val path = TextView(this).apply {
            text = item.path ?: item.relativePath
            textSize = 12f
            setTextColor(Color.rgb(102,115,109))
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.START
            setPadding(dp(4), dp(4), dp(4), 0)
        }

        card.addView(top)
        card.addView(path)
        listBox.addView(card)
    }

    private fun openItem(item: StorageItem) {
        try {
            val uri = when {
                item.uri != null -> item.uri
                item.path != null -> FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    File(item.path)
                )
                else -> null
            } ?: run {
                Toast.makeText(this, "تعذر تحديد الملف", Toast.LENGTH_SHORT).show()
                return
            }

            val mime = item.mime ?: guessMime(item.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "فتح / معاينة الملف"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "لا يوجد تطبيق مناسب لفتح هذا النوع من الملفات", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر فتح الملف: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر فتح الرابط: ${e.message ?: ""}", Toast.LENGTH_LONG).show()
        }
    }

    private fun findDuplicates() {
        if (!ensureScan()) return
        listBox.removeAllViews()
        listBox.addView(cardText(
            "جارٍ البحث عن الملفات المتكررة…\nنحسب البصمة فقط للملفات التي لها نفس الحجم لتقليل وقت الفحص."
        ))
        executor.execute {
            val candidates = allItems.filter { it.size > 0 }
                .groupBy { it.size }
                .values
                .filter { it.size > 1 }
                .flatten()

            val byHash = LinkedHashMap<String, MutableList<StorageItem>>()
            for (item in candidates) {
                val h = scanner.hash(item) ?: continue
                byHash.getOrPut(h) { mutableListOf() }.add(item)
            }
            val duplicates = byHash.values.filter { it.size > 1 }.flatten()
            runOnUiThread {
                showItems("الملفات المتكررة المؤكدة", duplicates.sortedByDescending { it.size })
            }
        }
    }

    private fun deleteSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "لم تحدد أي ملف", Toast.LENGTH_SHORT).show()
            return
        }

        val total = selected.sumOf { it.size }
        AlertDialog.Builder(this)
            .setTitle("مراجعة الحذف")
            .setMessage(
                "عدد الملفات: ${selected.size}\n" +
                "المساحة التي ستتوفر تقريبًا: ${fmt(total)}\n\n" +
                "يمكنك إلغاء العملية والضغط على «فتح» لمراجعة أي ملف قبل الحذف."
            )
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("متابعة") { _, _ -> performDelete() }
            .show()
    }

    private fun performDelete() {
        val uris = selected.mapNotNull { it.uri }
        val fileItems = selected.filter { it.path != null }
        var directDeleted = 0

        for (item in fileItems) {
            try {
                if (File(item.path!!).delete()) directDeleted++
            } catch (_: Exception) { }
        }

        if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
            try {
                val pi: PendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                startIntentSenderForResult(pi.intentSender, 200, null, 0, 0, 0)
            } catch (e: Exception) {
                Toast.makeText(this, "تعذر فتح طلب حذف الوسائط: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            for (u in uris) {
                try {
                    contentResolver.delete(u, null, null)
                } catch (_: Exception) { }
            }
        }

        if (directDeleted > 0) {
            Toast.makeText(this, "تم حذف $directDeleted ملف مباشر. أعد الفحص.", Toast.LENGTH_LONG).show()
        }
        selected.clear()
    }

    private fun button(label: String, click: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setOnClickListener { click() }
        setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(5), 0, dp(5))
        }
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 20f
        setTypeface(typeface, 1)
        setTextColor(Color.rgb(13,75,53))
        gravity = Gravity.END
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun cardText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(Color.rgb(23,33,28))
        setBackgroundColor(Color.WHITE)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(6), 0, dp(10))
        }
    }

    private fun fmt(bytes: Long): String = Formatter.formatFileSize(this, bytes)

    private fun date(sec: Long): String =
        if (sec <= 0) "تاريخ غير معروف"
        else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sec * 1000))

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
