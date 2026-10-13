package com.bas.storage

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Google Photos only. Cloud deletions take place in Google's own UI.
 * Android does not provide a supported deep link to the exact Google Photos
 * storage-review page, so the optional Google One link is best effort.
 */
class PhotosCleanupActivity : Activity() {
    private val photosPackage = "com.google.android.apps.photos"
    private val photosUrl = "https://photos.google.com/"
    private val reviewUrl = "https://one.google.com/storage/management/photos/large"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val surface = Color.rgb(247, 250, 252)
        val scroll = ScrollView(this).apply { setBackgroundColor(surface) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(22), dp(32), dp(22), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "صور Google"
            textSize = 28f
            setTextColor(Color.rgb(27, 54, 47))
            gravity = Gravity.CENTER_HORIZONTAL
            setTypeface(typeface, 1)
        })
        root.addView(TextView(this).apply {
            text = "Google Photos"
            textSize = 17f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(Color.rgb(89, 104, 104))
            setPadding(0, dp(5), 0, dp(30))
        })

        root.addView(action("فتح تطبيق Google Photos") { openPhotos() })
        root.addView(action("مراجعة الصور والفيديوهات الكبيرة") { openCloudReview() })

        root.addView(TextView(this).apply {
            text = "الحذف الحقيقي للصور السحابية يتم من داخل خدمة Google الرسمية، وليس من ملفات الهاتف. " +
                "قد يفتح الزر الثاني صفحة إدارة التخزين العامة بدل قائمة الصور الكبيرة؛ " +
                "وهذا يعتمد على طريقة معالجة Google للرابط."
            textSize = 14f
            setTextColor(Color.rgb(89, 104, 104))
            setPadding(dp(6), dp(28), dp(6), dp(8))
        })

        setContentView(scroll)
    }

    private fun openPhotos() {
        try {
            val launch = packageManager.getLaunchIntentForPackage(photosPackage)
            if (launch != null) {
                startActivity(launch)
                return
            }
        } catch (_: Exception) { }
        openUrl(photosUrl)
    }

    private fun openCloudReview() {
        // Prefer the browser: Google One may otherwise intercept the URL
        // without taking the user to the large-items review screen.
        val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(reviewUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage("com.android.chrome")
        }
        try {
            startActivity(chromeIntent)
        } catch (_: Exception) {
            openUrl(reviewUrl)
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
        } catch (_: ActivityNotFoundException) {
            showUnavailable()
        } catch (_: SecurityException) {
            showUnavailable()
        }
    }

    private fun showUnavailable() {
        AlertDialog.Builder(this)
            .setTitle("تعذر الفتح")
            .setMessage("افتح تطبيق صور Google مباشرة من جهازك ثم اختر إدارة مساحة التخزين من إعدادات الصور.")
            .setPositiveButton("موافق", null)
            .show()
    }

    private fun action(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 18f
        minHeight = dp(58)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(8), 0, dp(8))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
