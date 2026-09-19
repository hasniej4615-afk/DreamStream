package com.duta.movie.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

object DeviceUtils {

    /**
     * Determines whether the current device is a Television, Android TV, Google TV,
     * Fire TV, or TV Box (certified or uncertified AOSP).
     */
    fun isTvDevice(context: Context): Boolean {
        val pm = context.packageManager

        // 1. Standard Leanback feature (Official Android TV / Google TV)
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        if (pm.hasSystemFeature("android.software.leanback")) return true

        // 2. Hardware TV feature
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
        if (pm.hasSystemFeature("android.hardware.type.television")) return true

        // 3. UI Mode Manager report
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true

        // 4. Amazon Fire TV detection
        if (pm.hasSystemFeature("amazon.hardware.fire_tv")) return true
        if (Build.MANUFACTURER.equals("Amazon", ignoreCase = true) &&
            (Build.MODEL.startsWith("AFT", ignoreCase = true) || Build.PRODUCT.startsWith("AFT", ignoreCase = true))) {
            return true
        }

        // 5. Uncertified TV Box heuristic: No touchscreen AND non-telephony device
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return true
        }

        return false
    }
}
