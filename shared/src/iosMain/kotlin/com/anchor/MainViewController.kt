package com.anchor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.anchor.di.initKoin
import com.anchor.presentation.navigation.AnchorNav
import platform.UIKit.UIViewController

private val koin by lazy { initKoin() }

/**
 * Holds a pending deep link set by [onNotificationTap].
 * Compose reads this inside the [ComposeUIViewController] lambda so the composition
 * automatically reacts when it changes (e.g. notification tap while app is running).
 */
private var pendingDeepLink by mutableStateOf<String?>(null)

/**
 * Called from Swift's [UNUserNotificationCenterDelegate.userNotificationCenter(_:didReceive:)]
 * whenever a notification is tapped. Pass the "deepLink" string from the notification's
 * userInfo dictionary (e.g. "intervention://reminder").
 */
fun onNotificationTap(deepLink: String) {
    pendingDeepLink = deepLink
}

fun MainViewController(): UIViewController {
    koin // ensure Koin is initialised before the first composition
    return ComposeUIViewController { AnchorNav(deepLink = pendingDeepLink) }
}
