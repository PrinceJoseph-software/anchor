package com.anchor.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anchor.domain.repository.SettingsRepository
import com.anchor.platform.AppInfo
import com.anchor.platform.AppListProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppSelectionState(
    val apps: List<AppInfo> = emptyList(),
    val blockedPackages: Set<String> = emptySet(),
)

class AppSelectionViewModel(
    private val settings: SettingsRepository,
    private val appListProvider: AppListProvider,
) : ViewModel() {
    
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    
    val state: StateFlow<AppSelectionState> = combine(
        _apps, settings.blockedApps
    ) { apps, blocked ->
        AppSelectionState(apps, blocked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSelectionState())

    init {
        loadApps()
    }

    private fun loadApps() = viewModelScope.launch {
        // getInstalledApps() queries PackageManager for every installed app — must run on IO,
        // not Main, otherwise Samsung devices (200+ packages) cause an ANR and crash to home.
        _apps.value = withContext(Dispatchers.IO) { appListProvider.getInstalledApps() }
    }

    fun toggleApp(packageName: String) = viewModelScope.launch {
        val current: MutableSet<String> = settings.blockedApps.first().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        settings.setBlockedApps(current)
    }
}
