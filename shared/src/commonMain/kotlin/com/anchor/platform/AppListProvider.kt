package com.anchor.platform

data class AppInfo(
    val packageName: String,
    val name: String,
)

expect class AppListProvider {
    constructor()
    fun getInstalledApps(): List<AppInfo>
}
