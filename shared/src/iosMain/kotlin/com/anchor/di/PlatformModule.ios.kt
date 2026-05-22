package com.anchor.di

import com.anchor.data.repository.NSUserDefaultsActionRepository
import com.anchor.data.repository.NSUserDefaultsGoalRepository
import com.anchor.data.repository.NSUserDefaultsHistoryRepository
import com.anchor.data.repository.NSUserDefaultsSessionRepository
import com.anchor.data.repository.NSUserDefaultsSettingsRepository
import com.anchor.domain.repository.ActionRepository
import com.anchor.domain.repository.GoalRepository
import com.anchor.domain.repository.HistoryRepository
import com.anchor.domain.repository.SessionRepository
import com.anchor.domain.repository.SettingsRepository
import com.anchor.platform.AppListProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<SettingsRepository> { NSUserDefaultsSettingsRepository() }
    single<ActionRepository> { NSUserDefaultsActionRepository() }
    single<SessionRepository> { NSUserDefaultsSessionRepository() }
    single<HistoryRepository> { NSUserDefaultsHistoryRepository() }
    single<GoalRepository> { NSUserDefaultsGoalRepository() }
    single { AppListProvider() }
}
