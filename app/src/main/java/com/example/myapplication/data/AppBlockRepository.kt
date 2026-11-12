package com.example.myapplication.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focusgate_preferences")

/**
 * Repository responsible for querying installed apps and persisting blocked package preferences,
 * enforcing hardcoded system-critical safelists, and managing temporary unlock sessions.
 */
class AppBlockRepository private constructor(context: Context) {

    private val context: Context = context.applicationContext

    companion object {
        val KEY_BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
        val KEY_LOCKOUT_DURATION_SECONDS = intPreferencesKey("lockout_duration_seconds")
        val KEY_TEMP_UNLOCK_MAP = stringPreferencesKey("challenge_temp_unlock_map")
        const val DEFAULT_LOCKOUT_SECONDS = 15

        /**
         * Hardcoded base set of system and critical application package names that must never be blocked.
         */
        val HARDCODED_CRITICAL_PACKAGES = setOf(
            "com.android.settings",
            "com.android.systemui",
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.emergency",
            "com.google.android.packageinstaller"
        )

        @Volatile
        private var instance: AppBlockRepository? = null

        fun getInstance(context: Context): AppBlockRepository {
            return instance ?: synchronized(this) {
                instance ?: AppBlockRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Resolves the full set of protected package names, including FocusGate itself,
     * critical system components, and the dynamically resolved default Home launcher.
     */
    fun getProtectedPackages(): Set<String> {
        val protectedSet = mutableSetOf<String>()
        protectedSet.add(context.packageName)
        protectedSet.addAll(HARDCODED_CRITICAL_PACKAGES)

        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val pm = context.packageManager
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(homeIntent, 0)
            }
            val homePkg = resolveInfo?.activityInfo?.packageName
            if (!homePkg.isNullOrBlank()) {
                protectedSet.add(homePkg)
            }
        } catch (_: Exception) {
        }

        return protectedSet
    }

    /**
     * Flow of blocked package names from DataStore (guaranteed never to contain protected packages).
     */
    val blockedPackagesFlow: Flow<Set<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val set = preferences[KEY_BLOCKED_PACKAGES] ?: emptySet()
            val protectedPkgs = getProtectedPackages()
            set - protectedPkgs
        }

    /**
     * Flow of the lockout countdown duration in seconds.
     */
    val lockoutDurationFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_LOCKOUT_DURATION_SECONDS] ?: DEFAULT_LOCKOUT_SECONDS
        }

    /**
     * Reactive flow of currently active temporary unlock sessions (packageName -> unlockedUntilTimestamp).
     * Filters out sessions that have already expired.
     */
    val activeTemporaryUnlocksFlow: Flow<Map<String, Long>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val raw = preferences[KEY_TEMP_UNLOCK_MAP] ?: ""
            val now = System.currentTimeMillis()
            parseTempUnlockMap(raw).filterValues { expiry -> expiry > now }
        }

    /**
     * Direct snapshot of currently blocked packages.
     */
    suspend fun getBlockedPackages(): Set<String> {
        return blockedPackagesFlow.first()
    }

    /**
     * Checks if a specific package is blocked.
     */
    suspend fun isAppBlocked(packageName: String): Boolean {
        if (packageName in getProtectedPackages()) return false
        return getBlockedPackages().contains(packageName)
    }

    /**
     * Toggles the blocked state of a package (ignores protected packages).
     */
    suspend fun toggleAppBlock(packageName: String) {
        if (packageName in getProtectedPackages()) return

        context.dataStore.edit { preferences ->
            val current = preferences[KEY_BLOCKED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            // Strip any protected packages if present
            current.removeAll(getProtectedPackages())
            preferences[KEY_BLOCKED_PACKAGES] = current
        }
    }

    /**
     * Sets the blocked state of a package explicitly (ignores protected packages).
     */
    suspend fun setAppBlocked(packageName: String, blocked: Boolean) {
        if (packageName in getProtectedPackages()) return

        context.dataStore.edit { preferences ->
            val current = preferences[KEY_BLOCKED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (blocked) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            current.removeAll(getProtectedPackages())
            preferences[KEY_BLOCKED_PACKAGES] = current
        }
    }

    /**
     * Immediately clears any temporary unlock for [packageName], relocking it instantly.
     */
    suspend fun relockApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val raw = preferences[KEY_TEMP_UNLOCK_MAP] ?: ""
            val currentMap = parseTempUnlockMap(raw).toMutableMap()
            if (currentMap.containsKey(packageName)) {
                currentMap.remove(packageName)
                preferences[KEY_TEMP_UNLOCK_MAP] = serialiseTempUnlockMap(currentMap)
            }
        }
    }

    /**
     * Updates the lockout duration in seconds.
     */
    suspend fun setLockoutDuration(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCKOUT_DURATION_SECONDS] = seconds
        }
    }

    /**
     * Queries all user-launchable applications installed on the device,
     * filtering out any protected and system-critical applications to prevent self-lockout.
     */
    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val protectedPkgs = getProtectedPackages()

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val blockedSet = getBlockedPackages()
        val appList = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            // Exclude already processed apps and protected/system packages
            if (seenPackages.contains(pkg) || pkg in protectedPkgs) {
                continue
            }
            seenPackages.add(pkg)

            val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg
            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            appList.add(
                AppInfo(
                    packageName = pkg,
                    appName = label,
                    icon = icon,
                    isBlocked = blockedSet.contains(pkg),
                    isSystemApp = isSystem
                )
            )
        }

        appList.sortedBy { it.appName.lowercase() }
    }

    /**
     * Resolves metadata for a specific package name.
     */
    suspend fun getAppInfo(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isBlocked = isAppBlocked(packageName)

            AppInfo(
                packageName = packageName,
                appName = label,
                icon = icon,
                isBlocked = isBlocked,
                isSystemApp = isSystem
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTempUnlockMap(raw: String): Map<String, Long> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) {
                    val pkg = parts[0].trim()
                    val ts = parts[1].trim().toLongOrNull()
                    if (pkg.isNotEmpty() && ts != null) pkg to ts else null
                } else null
            }
            .toMap()
    }

    private fun serialiseTempUnlockMap(map: Map<String, Long>): String {
        return map.entries.joinToString(",") { "${it.key}=${it.value}" }
    }
}
