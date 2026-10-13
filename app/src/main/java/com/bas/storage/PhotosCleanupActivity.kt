package com.bas.storage

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Opens Google's own cloud-storage cleanup screen. BAS never deletes cloud
 * photos and never requests access to the user's Google account credentials.
 *
 * This activity is also a launcher icon, enabling one-tap access from home.
 * Android / Google One may change link handling; choose another route in BAS
 * if the desired native review screen does not open.
 */
class PhotosCleanupActivity : Activity() {
    companion object {
        const val EXTRA_CHOOSE_ROUTE = "com.bas.storage.CHOOSE_PHOTOS_ROUTE"
        private const val URL = "https://one.google.com/storage/management/photos/large"
        private const val PREFS = "photos_cleanup"
        private const val KEY_ROUTE = "open_route"
        private val routeIds = arrayOf("one", "photos", "system")
        private val routeNames = arrayOf(
            "Google One — شاشة تنظيف التخزين",
            "صور Google — التطبيق",
            "اختيار التطبيق تلقائيًا"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_CHOOSE_ROUTE, false)) {
            showRoutePicker()
        } else {
            val preferred = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_ROUTE, "one") ?: "one"
            launch(preferred)
        }
    }

    private fun showRoutePicker() {
        val current = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_ROUTE, "one") ?: "one"
        val checked = routeIds.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("كيف تريد فتح تنظيف صور Google؟")
            .setSingleChoiceItems(routeNames, checked) { dialog, which ->
                val route = routeIds[which]
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putString(KEY_ROUTE, route).apply()
                dialog.dismiss()
                launch(route)
            }
            .setNegativeButton("إلغاء") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun launch(route: String) {
        val targetPackage = when (route) {
            "one" -> "com.google.android.apps.subscriptions.red"
            "photos" -> "com.google.android.apps.photos"
            else -> null
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (targetPackage != null) setPackage(targetPackage)
        }
        try {
            startActivity(intent)
            finish()
        } catch (_: ActivityNotFoundException) {
            showFallback(route)
        } catch (_: SecurityException) {
            showFallback(route)
        }
    }

    private fun showFallback(route: String) {
        AlertDialog.Builder(this)
            .setTitle("تعذر فتح شاشة التنظيف")
            .setMessage(
                if (route == "system")
                    "لا يوجد تطبيق يستقبل الرابط حاليًا. يمكنك تغيير طريقة الفتح."
                else
                    "تطبيق Google المختار لم يقبل الرابط على هذا الجهاز. جرّب طريقة أخرى."
            )
            .setPositiveButton("اختيار طريقة أخرى") { _, _ -> showRoutePicker() }
            .setNegativeButton("إغلاق") { _, _ -> finish() }
            .setNeutralButton("فتح الرابط بالنظام") { _, _ ->
                if (route != "system") launch("system")
                else Toast.makeText(this, "تعذر فتح الرابط", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}
