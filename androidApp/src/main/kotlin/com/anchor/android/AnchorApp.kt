package com.anchor.android

import android.app.Application
import com.anchor.di.initKoin
import com.anchor.domain.repository.SettingsRepository
import com.anchor.platform.AndroidContextHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.android.inject

class AnchorApp : Application() {

    private val settings: SettingsRepository by inject()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.application = this
        initKoin {
            androidContext(this@AnchorApp)
        }
        ReminderScheduler(this).start()

        // Start the usage timer service whenever Lock Mode is ON and a daily limit is set.
        scope.launch {
            combine(settings.lockMode, settings.blockAfterMinutes) { lock, limit ->
                lock && limit > 0
            }.collect { shouldRun ->
                if (shouldRun) UsageTimerService.start(this@AnchorApp)
                else UsageTimerService.stop(this@AnchorApp)
            }
        }
    }
}
