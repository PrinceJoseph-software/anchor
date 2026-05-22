package com.anchor.platform

actual class AppListProvider {
    actual constructor()
    actual fun getInstalledApps(): List<AppInfo> {
        // iOS doesn't allow getting a list of installed apps freely
        return emptyList()
    }
}
