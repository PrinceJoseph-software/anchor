package com.anchor.platform

import android.content.Context
import android.content.pm.ApplicationInfo

actual class AppListProvider(private val context: Context) {
    actual constructor() : this(AndroidContextHolder.application)

    actual fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        // 0 = no extra metadata — GET_META_DATA loads all bundle extras for every package
        // which can exhaust memory on Samsung devices with 300+ packages and cause a crash.
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledApplications(0)
        return packages.mapNotNull { info ->
            // Skip pure system apps (not updated by user); skip ourselves.
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            if (isSystem || info.packageName == context.packageName) return@mapNotNull null

            // getApplicationLabel can throw on obscure/stub packages on Samsung OneUI.
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrNull() ?: return@mapNotNull null

            AppInfo(packageName = info.packageName, name = label)
        }.sortedBy { it.name.lowercase() }
    }
}
