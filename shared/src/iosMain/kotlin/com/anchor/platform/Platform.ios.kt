package com.anchor.platform

import platform.UserNotifications.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private object IOSPermissionState {
    var notificationsGranted: Boolean = false
}

actual fun isAndroid(): Boolean = false

actual fun shareText(text: String) {
    // iOS: copy to clipboard — UIActivityViewController requires a UIViewController
    // context that is not available from KMP shared code.
    platform.UIKit.UIPasteboard.generalPasteboard.setString(text)
}

actual fun hasShownCoachMark(): Boolean =
    platform.Foundation.NSUserDefaults.standardUserDefaults.boolForKey("coach_mark_shown")

actual fun setCoachMarkShown() {
    platform.Foundation.NSUserDefaults.standardUserDefaults.setBool(true, "coach_mark_shown")
    platform.Foundation.NSUserDefaults.standardUserDefaults.synchronize()
}

actual class PermissionController actual constructor() {
    actual fun hasUsageAccess(): Boolean = false // unsupported on iOS
    actual fun requestUsageAccess() { /* no-op: surfaced as info card in UI */ }
    actual fun hasOverlay(): Boolean = false
    actual fun requestOverlay() { /* no-op */ }
    actual fun hasAccessibilityService(): Boolean = false // not applicable on iOS
    actual fun requestAccessibilityService() { /* no-op on iOS */ }
    actual fun hasNotifications(): Boolean = IOSPermissionState.notificationsGranted
    actual suspend fun requestNotifications(): Boolean = suspendCoroutine { continuation ->
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { ok, _ ->
            IOSPermissionState.notificationsGranted = ok
            continuation.resume(ok)
        }
    }
}

actual fun startSessionNotification(actionName: String, startedAt: Long) { /* no-op: iOS uses the in-app timer */ }
actual fun stopSessionNotification() { /* no-op */ }

actual class Interrupter actual constructor() {
    // forceNotification is ignored on iOS — we always use a local notification.
    actual fun interrupt(title: String, body: String, deepLink: String, forceNotification: Boolean) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultCriticalSound())
            setUserInfo(mapOf("deepLink" to deepLink))
        }
        val req = UNNotificationRequest.requestWithIdentifier(
            identifier = "anchor.intervention.${platform.Foundation.NSDate().timeIntervalSince1970}",
            content = content,
            trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false),
        )
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(req, null)
    }
}
