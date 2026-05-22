package com.anchor.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anchor.platform.AndroidContextHolder
import com.anchor.platform.NotificationPermissionRequester
import com.anchor.presentation.navigation.AnchorNav
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity(), NotificationPermissionRequester {
    private var pendingNotificationResult: CompletableDeferred<Boolean>? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingNotificationResult?.complete(granted)
            pendingNotificationResult = null
        }

    // Observable deep link — Compose reads this inside setContent{} so recomposition fires
    // when onNewIntent delivers a new link while the activity is already running.
    private var deepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink = intent.getStringExtra("anchor.deepLink")
        AndroidContextHolder.setNotificationRequester(this)
        setContent { AnchorNav(deepLink = deepLink) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink = intent.getStringExtra("anchor.deepLink")
    }

    override fun onDestroy() {
        if (isFinishing) {
            AndroidContextHolder.setNotificationRequester(null)
        }
        pendingNotificationResult?.complete(false)
        pendingNotificationResult = null
        super.onDestroy()
    }

    override suspend fun requestPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        pendingNotificationResult?.complete(false)
        val result = CompletableDeferred<Boolean>()
        pendingNotificationResult = result
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return result.await()
    }
}
