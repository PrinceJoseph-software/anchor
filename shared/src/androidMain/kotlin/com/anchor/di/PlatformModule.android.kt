package com.anchor.di

import com.anchor.data.repository.AndroidSettingsRepository
import com.anchor.data.repository.AndroidActionRepository
import com.anchor.data.repository.AndroidGoalRepository
import com.anchor.data.repository.AndroidSessionRepository
import com.anchor.data.repository.AndroidHistoryRepository
import com.anchor.domain.repository.ActionRepository
import com.anchor.domain.repository.GoalRepository
import com.anchor.domain.repository.SessionRepository
import com.anchor.domain.repository.HistoryRepository
import com.anchor.domain.repository.SettingsRepository
import com.anchor.platform.AppListProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<SettingsRepository> { AndroidSettingsRepository(get()) }
    single<ActionRepository> { AndroidActionRepository(get()) }
    single<SessionRepository> { AndroidSessionRepository(get()) }
    single<HistoryRepository> { AndroidHistoryRepository(get()) }
    single<GoalRepository> { AndroidGoalRepository() }
    // AppListProvider uses AndroidContextHolder internally via its no-arg constructor.
    // Must be registered here so AppSelectionViewModel can receive it via Koin injection.
    single { AppListProvider() }
}
