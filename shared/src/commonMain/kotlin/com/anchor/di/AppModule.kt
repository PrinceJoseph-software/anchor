package com.anchor.di

import com.anchor.data.repository.*
import com.anchor.domain.repository.*
import com.anchor.domain.usecase.AbandonSession
import com.anchor.domain.usecase.CompleteSession
import com.anchor.domain.usecase.ComputeReliability
import com.anchor.domain.usecase.ComputeStreak
import com.anchor.domain.usecase.RecordSkip
import com.anchor.domain.usecase.RecordSnooze
import com.anchor.domain.usecase.StartSession
import com.anchor.domain.usecase.TodaysProgress
import com.anchor.platform.Interrupter
import com.anchor.platform.PermissionController
import com.anchor.presentation.home.HomeViewModel
import com.anchor.presentation.intervention.InterventionViewModel
import com.anchor.presentation.history.HistoryViewModel
import com.anchor.presentation.onboarding.OnboardingViewModel
import com.anchor.presentation.permissions.PermissionsViewModel
import com.anchor.presentation.session.SessionViewModel
import com.anchor.presentation.settings.AppSelectionViewModel
import com.anchor.presentation.settings.SettingsViewModel
import com.anchor.presentation.stats.GoalViewModel
import com.anchor.presentation.stats.StatsViewModel
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import org.koin.core.module.Module

expect val platformModule: Module

val sharedModule = module {
    single<DecisionRepository> { InMemoryDecisionRepository() }
    // ActionRepository, SessionRepository, HistoryRepository, SettingsRepository, GoalRepository come from platformModule
    single<TriggerSource> { TimeBasedTriggerSource(get()) }
    single { PermissionController() }
    single { Interrupter() }

    factory { StartSession(get(), get(), get()) }
    factory { CompleteSession(get(), get(), get()) }
    factory { AbandonSession(get(), get(), get()) }
    factory { RecordSnooze(get()) }
    factory { RecordSkip(get(), get()) }
    factory { ComputeReliability(get()) }
    factory { TodaysProgress(get(), get()) }
    factory { ComputeStreak(get()) }

    factory { (sessionId: String) -> SessionViewModel(sessionId, get(), get(), get(), get(), get()) }
    factory { OnboardingViewModel(get()) }
    factory { PermissionsViewModel(get(), get()) }
    factory { HomeViewModel(get(), get(), get(), get(), get(), get()) }
    factory { StatsViewModel(get(), get(), get()) }
    factory { GoalViewModel(get(), get(), get()) }
    factory { HistoryViewModel(get()) }
    factory { SettingsViewModel(get(), get(), get(), get(), get()) }
    factory { AppSelectionViewModel(get(), get()) }
    factory { InterventionViewModel(get(), get(), get(), get(), get()) }
}

fun initKoin(extra: KoinAppDeclaration? = null) = startKoin {
    extra?.invoke(this)
    modules(sharedModule, platformModule)
}
