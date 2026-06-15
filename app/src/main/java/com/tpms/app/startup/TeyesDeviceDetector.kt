package com.tpms.app.startup

import android.content.Context
import android.os.Build

object TeyesDeviceDetector {

    private val TEYES_PACKAGES = listOf(
        "com.teyes",
        "com.fyt",
        "ru.teyes",
        "com.teyes.app",
        "com.teyes.radio",
        "com.teyes.carlink"
    )

    private val TEYES_MANUFACTURER_HINTS = listOf("teyes", "fyt", "kingbeats")
    private val TEYES_MODEL_HINTS = listOf("cc3", "cc2", "spro", "uis7862", "ums512")

    fun isLikelyTeyesHeadUnit(context: Context): Boolean {
        if (hasTeyesSystemPackage(context)) return true
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        val combined = "$manufacturer $brand $model $product"
        return TEYES_MANUFACTURER_HINTS.any { combined.contains(it) } ||
            TEYES_MODEL_HINTS.any { combined.contains(it) }
    }

    private fun hasTeyesSystemPackage(context: Context): Boolean {
        val pm = context.packageManager
        return TEYES_PACKAGES.any { prefix ->
            runCatching {
                pm.getInstalledPackages(0).any { info ->
                    info.packageName.startsWith(prefix)
                }
            }.getOrDefault(false)
        }
    }
}
