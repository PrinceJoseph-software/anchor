package com.anchor.platform

/** True on Android, false on iOS/desktop. Used to gate Android-only UI. */
expect fun isAndroid(): Boolean

/**
 * Opens the system share sheet (Android) or copies to clipboard (iOS).
 * Used by the export dialog to share JSON data.
 */
expect fun shareText(text: String)

/** Returns true if the guided feature tour has already been shown to the user. */
expect fun hasShownCoachMark(): Boolean

/** Marks the guided feature tour as having been shown so it won't repeat. */
expect fun setCoachMarkShown()

/**
 * Platform-abstracted permission + intervention surface.
 * Android implements all three; iOS gracefully degrades.
 */
expect class PermissionController() {
    fun hasUsageAccess(): Boolean
    fun requestUsageAccess()
    fun hasOverlay(): Boolean
    fun requestOverlay()
    fun hasNotifications(): Boolean
    suspend fun requestNotifications(): Boolean
    /** Returns true if the Anchor Accessibility Service is enabled in system Accessibility Settings. */
    fun hasAccessibilityService(): Boolean
    /** Opens the system Accessibility Settings screen so the user can enable the service. */
    fun requestAccessibilityService()
}

expect class Interrupter() {
    /** Surface a decision-point to the user. On Android: system overlay window.
     *  On iOS: schedule a critical local notification + queue an in-app prompt. */
    fun interrupt(title: String, body: String, deepLink: String, forceNotification: Boolean = false)
}

/**
 * Post a persistent foreground notification showing that a focus session is in progress.
 * On Android this starts a foreground service that keeps the notification alive and ticks
 * the elapsed-time counter. On iOS it is a no-op (the in-app timer is always visible).
 */
expect fun startSessionNotification(actionName: String, startedAt: Long)

/** Dismiss the session-in-progress notification posted by [startSessionNotification]. */
expect fun stopSessionNotification()
