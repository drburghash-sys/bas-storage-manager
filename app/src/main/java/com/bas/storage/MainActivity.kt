package com.bas.storage

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var scanner: StorageScanner
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var listBox: LinearLayout
    private var allItems: List<StorageItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = StorageScanner(this)
        buildUi()
        requestBasicPermissions()
    }

    private fun buildUi() {
        val bg = Color.rgb(246, 247, 248)
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
            setTextColor(Color.rgb(23, 33, 28))
            setTypeface(typeface, 1)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        root.addView(TextView(this).apply {
            text = "راجع ملفًا واحدًا في كل مرة — احذف أو احتفظ ثم انتقل مباشرة"
            textSize = 15f
            setTextColor(Color.rgb(102, 115, 109))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(16))
        })

        status = cardText("لم يبدأ الفحص بعد")
        root.addView(status)

        root.addView(sectionTitle("التخزين المحلي"))
        root.addView(button("منح وصول كامل للملفات") { openAllFilesAccess() })
        root.addView(button("فحص الجهاز الآن") { runScan() })
        root.addView(button("مراجعة أكبر الملفات") { reviewLarge() })
        root.addView(button("مراجعة وسائط WhatsApp") {
            reviewCategory(StorageItem.Category.WHATSAPP, "وسائط WhatsApp")
        })
        root.addView(button("مراجعة Downloads") {
            reviewCategory(StorageItem.Category.DOWNLOAD, "Downloads")
        })
        root.addView(button("مراجعة الصور والفيديو") { reviewPhotosVideos() })
        root.addView(button("مراجعة الملفات المتكررة") { findDuplicates() })

        root.addView(sectionTitle("Google"))
        root.addView(cardText(
            "لن يفتح BAS تطبيق Google Files أو Drive لمراجعة الملفات. " +
            "سيكون Gmail وGoogle Drive داخل نفس شاشة المراجعة المتتابعة بعد ربط Google OAuth."
        ))
        root.addView(button("Gmail — الربط داخل BAS") { cloudInfo("Gmail") })
        root.addView(button("Google Drive — الربط داخل BAS") { cloudInfo("Google Drive") })

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
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            })
        } else {
            requestBasicPermissions()
        }
    }

    private fun runScan() {
        listBox.removeAllViews()
        status.text = "جارٍ فحص التخزين…"

        executor.execute {
            val result = scanner.scan()
            allItems = result.items
            val cats = allItems.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.size } }

            runOnUiThread {
                status.text = buildString {
                    append("المساحة الكلية: ${fmt(result.totalBytes)}\n")
                    append("المستخدم: ${fmt(result.usedBytes)}\n")
                    append("المتاح: ${fmt(result.freeBytes)}\n\n")
                    append("العناصر المفحوصة: ${allItems.size}\n")
                    append("WhatsApp: ${fmt(cats[StorageItem.Category.WHATSAPP] ?: 0)}\n")
                    append("الصور: ${fmt(cats[StorageItem.Category.IMAGE] ?: 0)}\n")
                    append("الفيديو: ${fmt(cats[StorageItem.Category.VIDEO] ?: 0)}\n")
                    append("Downloads: ${fmt(cats[StorageItem.Category.DOWNLOAD] ?: 0)}")
                }
            }
        }
    }

    private fun ensureScan(): Boolean {
        if (allItems.isEmpty()) {
            Toast.makeText(this, "اضغط «فحص الجهاز الآن» أولًا", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun reviewLarge() {
        if (!ensureScan()) return
        startReview(
            "أكبر الملفات",
            allItems.filter { it.size >= 100L * 1024 * 1024 }.sortedByDescending { it.size }
        )
    }

    private fun reviewCategory(category: StorageItem.Category, title: String) {
        if (!ensureScan()) return
        startReview(title, allItems.filter { it.category == category }.sortedByDescending { it.size })
    }

    private fun reviewPhotosVideos() {
        if (!ensureScan()) return
        startReview(
            "الصور والفيديو",
            allItems.filter {
                it.category == StorageItem.Category.IMAGE ||
                    it.category == StorageItem.Category.VIDEO
            }.sortedByDescending { it.size }
        )
    }

    private fun startReview(title: String, items: List<StorageItem>) {
        if (items.isEmpty()) {
            Toast.makeText(this, "لا توجد ملفات في هذا القسم", Toast.LENGTH_SHORT).show()
            return
        }
        ReviewSession.title = title
        ReviewSession.items = items.toMutableList()
        startActivityForResult(Intent(this, ReviewActivity::class.java), 300)
    }

    private fun findDuplicates() {
        if (!ensureScan()) return

        listBox.removeAllViews()
        listBox.addView(cardText(
            "جارٍ البحث عن الملفات المتكررة…\nسيتم فتحها مباشرة في وضع المراجعة عند انتهاء الفحص."
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

            val duplicates = byHash.values
                .filter { it.size > 1 }
                .flatten()
                .sortedByDescending { it.size }

            runOnUiThread {
                listBox.removeAllViews()
                startReview("الملفات المتكررة", duplicates)
            }
        }
    }

    private fun cloudInfo(name: String) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(
                "سأجعل $name يظهر داخل BAS بنفس أسلوب: ملف → حذف/احتفاظ → التالي.\n\n" +
                "الخطوة المتبقية هي ربط Google OAuth لمرة واحدة حتى يستطيع BAS قراءة الملفات السحابية وحذفها بإذن حسابك. " +
                "لن يتم فتح تطبيق Google Files كطريقة المراجعة."
            )
            .setPositiveButton("موافق", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 300 && resultCode == RESULT_OK) {
            runScan()
        }
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
        setTextColor(Color.rgb(13, 75, 53))
        gravity = Gravity.END
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun cardText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(Color.rgb(23, 33, 28))
        setBackgroundColor(Color.WHITE)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(6), 0, dp(10))
        }
    }

    private fun fmt(bytes: Long): String = Formatter.formatFileSize(this, bytes)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
