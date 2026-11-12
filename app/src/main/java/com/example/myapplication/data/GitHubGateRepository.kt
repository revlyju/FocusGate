package com.example.myapplication.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GitHubBankState(
    val username: String = "",
    val pat: String = "",
    val bankDateKey: String = "",
    val redeemedCommitsToday: Int = 0,
    val bankedMinutesAvailable: Int = 0
)

data class GitHubSyncResult(
    val todayTotalCommits: Int,
    val newCommitsEarned: Int,
    val bankedMinutesAvailable: Int
)

/**
 * Repository managing the GitHub Commit Screen-Time Bank:
 * - Persists GitHub credentials, daily bank state, redeemed commits, and banked minutes.
 * - Automatically resets the bank at local midnight (lazy reset on read/sync).
 * - Syncs new commits via [GitHubApiService] and credits [MINUTES_PER_COMMIT] mins per new commit.
 * - Spends banked minutes to issue temporary unlock passes via [AppBlockRepository].
 * Compatible with API 24+.
 */
class GitHubGateRepository private constructor(context: Context) {

    private val context: Context = context.applicationContext

    companion object {
        private val KEY_GH_USERNAME = stringPreferencesKey("github_username")
        private val KEY_GH_PAT = stringPreferencesKey("github_pat")
        private val KEY_BANK_DATE = stringPreferencesKey("bank_date_key")
        private val KEY_REDEEMED_COMMITS = intPreferencesKey("redeemed_commits_today")
        private val KEY_BANKED_MINUTES = intPreferencesKey("banked_minutes_available")

        const val MINUTES_PER_COMMIT = 15

        @Volatile
        private var instance: GitHubGateRepository? = null

        fun getInstance(context: Context): GitHubGateRepository =
            instance ?: synchronized(this) {
                instance ?: GitHubGateRepository(context.applicationContext).also { instance = it }
            }

        /** Returns today's local date as a stable string, e.g. "2026-11-12". */
        fun todayDateKey(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // ─── DataStore Flows ──────────────────────────────────────────────────────

    val bankStateFlow: Flow<GitHubBankState> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val storedDate = prefs[KEY_BANK_DATE] ?: ""
            val todayStr = todayDateKey()

            // Lazy daily reset: if date changed, reflect zero balances in the flow
            if (storedDate.isNotBlank() && storedDate != todayStr) {
                GitHubBankState(
                    username = prefs[KEY_GH_USERNAME] ?: "",
                    pat = prefs[KEY_GH_PAT] ?: "",
                    bankDateKey = storedDate,
                    redeemedCommitsToday = 0,
                    bankedMinutesAvailable = 0
                )
            } else {
                GitHubBankState(
                    username = prefs[KEY_GH_USERNAME] ?: "",
                    pat = prefs[KEY_GH_PAT] ?: "",
                    bankDateKey = storedDate,
                    redeemedCommitsToday = prefs[KEY_REDEEMED_COMMITS] ?: 0,
                    bankedMinutesAvailable = prefs[KEY_BANKED_MINUTES] ?: 0
                )
            }
        }

    val usernameFlow: Flow<String> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_GH_USERNAME] ?: "" }

    val patFlow: Flow<String> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_GH_PAT] ?: "" }

    // ─── Credential Management ────────────────────────────────────────────────

    suspend fun saveCredentials(username: String, pat: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GH_USERNAME] = username.trim()
            prefs[KEY_GH_PAT] = pat.trim()
        }
    }

    suspend fun getCredentials(): Pair<String, String> {
        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        return (prefs[KEY_GH_USERNAME] ?: "") to (prefs[KEY_GH_PAT] ?: "")
    }

    // ─── Daily Reset ──────────────────────────────────────────────────────────

    private suspend fun ensureDateReset() {
        val todayStr = todayDateKey()
        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val storedDate = prefs[KEY_BANK_DATE] ?: ""

        if (storedDate != todayStr) {
            context.dataStore.edit { p ->
                p[KEY_BANK_DATE] = todayStr
                p[KEY_REDEEMED_COMMITS] = 0
                p[KEY_BANKED_MINUTES] = 0
            }
        }
    }

    // ─── Commit Sync ──────────────────────────────────────────────────────────

    /**
     * Fetches today's total commits from GitHub and credits any new commits to the bank
     * at [MINUTES_PER_COMMIT] minutes each.
     */
    suspend fun syncCommitsFromGitHub(): Result<GitHubSyncResult> {
        val (username, pat) = getCredentials()
        if (username.isBlank() || pat.isBlank()) {
            return Result.failure(IllegalStateException("GitHub credentials not configured."))
        }

        return when (val apiResult = GitHubApiService.fetchTodayCommits(username, pat)) {
            is GitHubApiResult.Error -> Result.failure(Exception(apiResult.message))
            is GitHubApiResult.Success -> {
                ensureDateReset()

                val todayTotal = apiResult.todayTotalCommits
                var newCommitsEarned = 0
                var finalBankedMinutes = 0

                context.dataStore.edit { prefs ->
                    val redeemed = prefs[KEY_REDEEMED_COMMITS] ?: 0
                    val currentBanked = prefs[KEY_BANKED_MINUTES] ?: 0
                    val newUnredeemed = (todayTotal - redeemed).coerceAtLeast(0)

                    newCommitsEarned = newUnredeemed
                    finalBankedMinutes = if (newUnredeemed > 0) {
                        val updated = currentBanked + newUnredeemed * MINUTES_PER_COMMIT
                        prefs[KEY_REDEEMED_COMMITS] = todayTotal
                        prefs[KEY_BANKED_MINUTES] = updated
                        prefs[KEY_BANK_DATE] = todayDateKey()
                        updated
                    } else {
                        currentBanked
                    }
                }

                Result.success(
                    GitHubSyncResult(
                        todayTotalCommits = todayTotal,
                        newCommitsEarned = newCommitsEarned,
                        bankedMinutesAvailable = finalBankedMinutes
                    )
                )
            }
        }
    }

    /**
     * Verifies credentials by fetching today's commit count without modifying the bank.
     * Used by the "Verify & Save" action in settings.
     */
    suspend fun verifyCredentials(username: String, pat: String): GitHubApiResult =
        GitHubApiService.fetchTodayCommits(username, pat)

    // ─── Spend Banked Minutes ─────────────────────────────────────────────────

    /**
     * Deducts [minutesToSpend] from the bank and grants a temporary unlock pass for [packageName].
     * Returns true on success, false if insufficient balance.
     */
    suspend fun spendBankedMinutes(
        packageName: String,
        minutesToSpend: Int
    ): Boolean {
        ensureDateReset()

        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val currentBanked = prefs[KEY_BANKED_MINUTES] ?: 0

        if (currentBanked < minutesToSpend) return false

        // Deduct from bank
        context.dataStore.edit { p ->
            p[KEY_BANKED_MINUTES] = (currentBanked - minutesToSpend).coerceAtLeast(0)
        }

        // Grant unlock via shared temp-unlock map (same key used by AppBlockRepository)
        val expiresAt = System.currentTimeMillis() + minutesToSpend * 60_000L
        val keyTempMap = AppBlockRepository.KEY_TEMP_UNLOCK_MAP
        context.dataStore.edit { p ->
            val raw = p[keyTempMap] ?: ""
            val map = raw.split(",")
                .mapNotNull { entry ->
                    val parts = entry.split("=")
                    if (parts.size == 2 && parts[0].isNotBlank())
                        parts[0] to (parts[1].toLongOrNull() ?: 0L)
                    else null
                }.toMap().toMutableMap()
            map[packageName] = expiresAt
            p[keyTempMap] = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        }

        return true
    }

    suspend fun getBankedMinutes(): Int {
        ensureDateReset()
        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        return prefs[KEY_BANKED_MINUTES] ?: 0
    }

    suspend fun getRedeemedCommitsToday(): Int {
        ensureDateReset()
        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        return prefs[KEY_REDEEMED_COMMITS] ?: 0
    }
}
