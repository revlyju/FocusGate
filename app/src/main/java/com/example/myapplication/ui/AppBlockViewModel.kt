package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppBlockRepository
import com.example.myapplication.data.AppInfo
import com.example.myapplication.ui.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel managing installed application states, search filters, block toggles,
 * and a 1-second live countdown ticker for active temporary unlock sessions.
 */
class AppBlockViewModel(
    application: Application,
    private val repository: AppBlockRepository = AppBlockRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val searchQueryState = MutableStateFlow("")
    private val installedAppsRawState = MutableStateFlow<List<AppInfo>>(emptyList())
    private val permissionsState = MutableStateFlow(
        Pair(
            PermissionUtils.isAccessibilityServiceEnabled(application),
            PermissionUtils.isOverlayPermissionGranted(application)
        )
    )
    private val isLoadingState = MutableStateFlow(true)

    // Current snapshot of temporary unlocks from DataStore: packageName -> expiryTimestampMs
    private val activeUnlocksRawState = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Remaining seconds mapped by package name, updated every 1s by the ticker coroutine
    private val activeUnlockRemainingSecondsState = MutableStateFlow<Map<String, Long>>(emptyMap())

    private var tickerJob: Job? = null

    private data class AppFilterState(
        val updatedApps: List<AppInfo>,
        val filteredApps: List<AppInfo>,
        val query: String,
        val blockedSet: Set<String>
    )

    private val appsFilterFlow = combine(
        installedAppsRawState,
        repository.blockedPackagesFlow,
        searchQueryState
    ) { apps, blockedSet, query ->
        val updatedApps = apps.map { app ->
            val blocked = blockedSet.contains(app.packageName)
            if (app.isBlocked != blocked) app.copy(isBlocked = blocked) else app
        }

        val filtered = if (query.isBlank()) {
            updatedApps
        } else {
            val trimmed = query.trim().lowercase()
            updatedApps.filter {
                it.appName.lowercase().contains(trimmed) || it.packageName.lowercase().contains(trimmed)
            }
        }

        AppFilterState(
            updatedApps = updatedApps,
            filteredApps = filtered,
            query = query,
            blockedSet = blockedSet
        )
    }

    val uiState: StateFlow<AppBlockUiState> = combine(
        appsFilterFlow,
        permissionsState,
        isLoadingState,
        activeUnlockRemainingSecondsState
    ) { appState, (accessibility, overlay), loading, remainingMap ->
        // Build active unlock sessions list with full app metadata and ticking seconds
        val appsByPkg = appState.updatedApps.associateBy { it.packageName }
        val rawUnlocks = activeUnlocksRawState.value
        val sessions = remainingMap.mapNotNull { (pkg, remainingSec) ->
            val expiry = rawUnlocks[pkg] ?: 0L
            val appInfo = appsByPkg[pkg] ?: AppInfo(packageName = pkg, appName = pkg, isBlocked = true)
            if (remainingSec > 0) {
                ActiveUnlockSession(
                    appInfo = appInfo,
                    remainingSeconds = remainingSec,
                    expiresAtTimestamp = expiry
                )
            } else null
        }.sortedBy { it.remainingSeconds }

        AppBlockUiState(
            installedApps = appState.updatedApps,
            filteredApps = appState.filteredApps,
            searchQuery = appState.query,
            blockedCount = appState.blockedSet.size,
            isAccessibilityEnabled = accessibility,
            isOverlayPermissionEnabled = overlay,
            isLoading = loading,
            activeUnlockSessions = sessions,
            activeUnlockRemainingMap = remainingMap
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppBlockUiState()
    )

    init {
        loadApps()
        observeActiveTemporaryUnlocks()
    }

    private fun observeActiveTemporaryUnlocks() {
        viewModelScope.launch {
            repository.activeTemporaryUnlocksFlow.collect { unlockMap ->
                activeUnlocksRawState.value = unlockMap
                restartTicker(unlockMap)
            }
        }
    }

    private fun restartTicker(unlockMap: Map<String, Long>) {
        tickerJob?.cancel()

        if (unlockMap.isEmpty()) {
            activeUnlockRemainingSecondsState.value = emptyMap()
            return
        }

        tickerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = mutableMapOf<String, Long>()

                unlockMap.forEach { (pkg, expiry) ->
                    val diffSec = (expiry - now) / 1000L
                    if (diffSec > 0) {
                        remaining[pkg] = diffSec
                    }
                }

                activeUnlockRemainingSecondsState.value = remaining

                if (remaining.isEmpty()) {
                    break
                }

                delay(1000L)
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            isLoadingState.value = true
            val apps = repository.loadInstalledApps()
            installedAppsRawState.value = apps
            isLoadingState.value = false
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        searchQueryState.value = newQuery
    }

    fun toggleAppBlock(packageName: String) {
        viewModelScope.launch {
            repository.toggleAppBlock(packageName)
        }
    }

    fun relockApp(packageName: String) {
        viewModelScope.launch {
            repository.relockApp(packageName)
        }
    }

    fun refreshPermissions() {
        val app = getApplication<Application>()
        permissionsState.value = Pair(
            PermissionUtils.isAccessibilityServiceEnabled(app),
            PermissionUtils.isOverlayPermissionGranted(app)
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppBlockViewModel::class.java)) {
                return AppBlockViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
