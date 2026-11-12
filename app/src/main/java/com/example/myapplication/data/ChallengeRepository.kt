package com.example.myapplication.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Repository responsible for:
 *  - Loading and caching challenges from main/assets/challenges.json.
 *  - Escalating difficulty tier tracking via DataStore Preferences.
 *  - Issuing and validating per-package 5-minute temporary unlock passes.
 */
class ChallengeRepository private constructor(private val context: Context) {

    // ─── DataStore keys ──────────────────────────────────────────────────────

    companion object {
        /** Number of unlock-solving sessions today (escalates difficulty tier). */
        private val KEY_UNLOCK_COUNT_TODAY = intPreferencesKey("challenge_unlock_count_today")

        /** Wall-clock ms of the most recent successful unlock. */
        private val KEY_LAST_UNLOCK_TIMESTAMP = longPreferencesKey("challenge_last_unlock_timestamp")

        /**
         * Comma-separated "package=expiryMs" pairs representing temporary passes.
         * e.g. "com.instagram.android=1733999999000,com.twitter.android=1734000500000"
         */
        private val KEY_TEMP_UNLOCK_MAP = stringPreferencesKey("challenge_temp_unlock_map")

        /** IDs of challenges already served in the current session (to avoid repeats). */
        private val KEY_SERVED_IDS = stringSetPreferencesKey("challenge_served_ids")

        /** Decay interval: if gap > 2 h, decrement unlock count by 1. */
        private const val DECAY_INTERVAL_MS = 7_200_000L   // 2 hours

        /** Duration of a successful temporary unlock pass. */
        private const val TEMP_UNLOCK_DURATION_MS = 300_000L  // 5 minutes

        @Volatile
        private var instance: ChallengeRepository? = null

        fun getInstance(context: Context): ChallengeRepository {
            return instance ?: synchronized(this) {
                instance ?: ChallengeRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    // ─── In-memory challenge bank ─────────────────────────────────────────────

    private var allChallenges: List<Challenge>? = null

    /** Loads and caches challenges from assets; safe to call repeatedly. */
    private suspend fun getChallenges(): List<Challenge> = withContext(Dispatchers.IO) {
        allChallenges ?: run {
            val json = context.assets.open("challenges.json").bufferedReader().use { it.readText() }
            Challenge.listFromJsonArray(json).also { allChallenges = it }
        }
    }

    // ─── Escalation mechanics ─────────────────────────────────────────────────

    /** Current effective unlock count after applying the decay rule. */
    private suspend fun getEffectiveUnlockCount(): Int {
        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

        val rawCount = prefs[KEY_UNLOCK_COUNT_TODAY] ?: 0
        val lastTs = prefs[KEY_LAST_UNLOCK_TIMESTAMP] ?: 0L
        val now = System.currentTimeMillis()

        return if ((lastTs > 0L) && ((now - lastTs) >= DECAY_INTERVAL_MS)) {
            // Apply decay: subtract 1, floor at 0
            val decayed = (rawCount - 1).coerceAtLeast(0)
            // Persist the decayed value so it isn't re-applied every call
            context.dataStore.edit { prefs2 ->
                prefs2[KEY_UNLOCK_COUNT_TODAY] = decayed
                prefs2[KEY_LAST_UNLOCK_TIMESTAMP] = now
            }
            decayed
        } else {
            rawCount
        }
    }

    /** Maps unlock count → difficulty tier. */
    private fun difficultyForCount(count: Int): Int = when (count) {
        0 -> 1  // 1st unlock attempt → difficulty 1
        1 -> 2  // 2nd unlock attempt → difficulty 2
        2 -> 3  // 3rd unlock attempt → difficulty 3
        else -> 4  // 4th and beyond → difficulty 4
    }

    /**
     * Selects a non-repeating challenge for the current escalation tier.
     * Resets served-id tracking if all challenges in the tier are exhausted.
     */
    suspend fun getNextChallenge(): Challenge? {
        val count = getEffectiveUnlockCount()
        val targetDifficulty = difficultyForCount(count)
        val bank = getChallenges().filter { it.difficulty == targetDifficulty }
        if (bank.isEmpty()) return null

        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val servedIds = prefs[KEY_SERVED_IDS] ?: emptySet()

        val available = bank.filter { it.id !in servedIds }
        // If all tier challenges exhausted, reset the served list and start fresh
        val candidate = if (available.isNotEmpty()) available.random() else {
            context.dataStore.edit { it[KEY_SERVED_IDS] = emptySet() }
            bank.random()
        }

        // Mark as served
        context.dataStore.edit { prefs2 ->
            val current = prefs2[KEY_SERVED_IDS]?.toMutableSet() ?: mutableSetOf()
            current.add(candidate.id)
            prefs2[KEY_SERVED_IDS] = current
        }

        return candidate
    }

    // ─── Unlock pass management ───────────────────────────────────────────────

    /**
     * Called when the user solves a challenge correctly.
     * Increments unlock count, timestamps the event, and grants a 5-min pass for [packageName].
     */
    suspend fun recordSuccessfulUnlock(packageName: String) {
        val now = System.currentTimeMillis()
        val expiresAt = now + TEMP_UNLOCK_DURATION_MS

        context.dataStore.edit { prefs ->
            // Escalate count
            val currentCount = prefs[KEY_UNLOCK_COUNT_TODAY] ?: 0
            prefs[KEY_UNLOCK_COUNT_TODAY] = currentCount + 1
            prefs[KEY_LAST_UNLOCK_TIMESTAMP] = now

            // Update the temp-unlock map
            val existing = prefs[KEY_TEMP_UNLOCK_MAP] ?: ""
            val map = parseTempUnlockMap(existing).toMutableMap()
            map[packageName] = expiresAt
            prefs[KEY_TEMP_UNLOCK_MAP] = serialiseTempUnlockMap(map)
        }
    }

    /**
     * Returns true if [packageName] currently holds an active temporary unlock pass.
     */
    suspend fun hasActiveUnlock(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val raw = prefs[KEY_TEMP_UNLOCK_MAP] ?: return false
        val map = parseTempUnlockMap(raw)
        val expiresAt = map[packageName] ?: return false
        return now < expiresAt
    }

    /**
     * Milliseconds remaining in the active unlock pass for [packageName], or 0 if none.
     */
    suspend fun unlockRemainingMs(packageName: String): Long {
        val now = System.currentTimeMillis()
        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val raw = prefs[KEY_TEMP_UNLOCK_MAP] ?: return 0L
        val map = parseTempUnlockMap(raw)
        val expiresAt = map[packageName] ?: return 0L
        return (expiresAt - now).coerceAtLeast(0L)
    }

    /**
     * Reactive flow of the temp-unlock map, used by the AccessibilityService
     * to avoid suspension on the event callback thread.
     */
    val tempUnlockMapFlow: Flow<Map<String, Long>> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            parseTempUnlockMap(prefs[KEY_TEMP_UNLOCK_MAP] ?: "")
        }

    /** Flow of the current escalation tier (1–4) after decay. */
    val currentDifficultyTierFlow: Flow<Int> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val count = prefs[KEY_UNLOCK_COUNT_TODAY] ?: 0
            difficultyForCount(count)
        }

    // ─── Serialisation helpers ────────────────────────────────────────────────

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
        return map.entries.joinToString(",") { (pkg, ts) -> "$pkg=$ts" }
    }
}
