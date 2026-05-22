package com.anchor.presentation.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.platform.PermissionController
import com.anchor.platform.isAndroid
import com.anchor.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionsState(
    val usage: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val accessibility: Boolean = false,
) {
    val allGranted: Boolean get() = usage && overlay && notifications && accessibility

    /**
     * Android: requires at least 2 of the core 3 permissions — Usage and Overlay cannot be granted
     * later from inside the app, so they must be configured during onboarding.
     * Accessibility Service is optional for onboarding (only needed for Lock Mode) but shown
     * so users know it exists.
     * iOS: only Notifications is available, so that alone is sufficient.
     */
    val canFinish: Boolean get() = if (isAndroid()) {
        listOf(usage, overlay, notifications).count { it } >= 2
    } else {
        notifications
    }

    /** How many of the presented permissions have been granted. */
    val grantedCount: Int get() = listOf(usage, overlay, notifications, accessibility).count { it }
}

class PermissionsViewModel(
    private val controller: PermissionController,
    private val settings: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PermissionsState())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    fun refresh() = _state.update { it.copy(
        usage = controller.hasUsageAccess(),
        overlay = controller.hasOverlay(),
        notifications = controller.hasNotifications(),
        accessibility = controller.hasAccessibilityService(),
    )}

    fun requestUsage() {
        controller.requestUsageAccess()
        refresh()
    }
    fun requestOverlay() {
        controller.requestOverlay()
        refresh()
    }
    fun requestNotifications() {
        viewModelScope.launch { controller.requestNotifications(); refresh() }
    }
    fun requestAccessibility() {
        controller.requestAccessibilityService()
        refresh()
    }

    fun completeOnboarding() = viewModelScope.launch {
        // Sync real OS permission state into the stored setting before completing, so the
        // Notifications toggle in Settings accurately reflects what was actually granted.
        settings.setNotifications(controller.hasNotifications())
        settings.setOnboardingCompleted(true)
    }
}
