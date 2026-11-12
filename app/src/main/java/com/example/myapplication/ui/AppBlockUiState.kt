package com.example.myapplication.ui

import com.example.myapplication.data.AppInfo

/**
 * Model representing an active temporary unlock session for an app.
 */
data class ActiveUnlockSession(
    val appInfo: AppInfo,
    val remainingSeconds: Long,
    val expiresAtTimestamp: Long
) {
    val formattedTime: String
        get() {
            val minutes = (remainingSeconds / 60).coerceAtLeast(0)
            val seconds = (remainingSeconds % 60).coerceAtLeast(0)
            return java.util.Locale.US.let { String.format(it, "%02d:%02d", minutes, seconds) }
        }
}

/**
 * UI state representing the app blocking dashboard with live countdown support.
 */
data class AppBlockUiState(
    val installedApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val blockedCount: Int = 0,
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayPermissionEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val activeUnlockSessions: List<ActiveUnlockSession> = emptyList(),
    val activeUnlockRemainingMap: Map<String, Long> = emptyMap()
)
