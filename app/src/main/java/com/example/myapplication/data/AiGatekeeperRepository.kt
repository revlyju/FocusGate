package com.example.myapplication.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class PastExcuse(
    val timestamp: Long,
    val appName: String,
    val summary: String
)

/**
 * Repository managing:
 *  - BYOK Groq API key persistence and verification status
 *  - Rolling history of up to 15 approved past excuses
 *  - Anti-reset 30-minute cooldowns per package on negotiation failure
 *  - Temporary pass issuance for granted minutes
 */
class AiGatekeeperRepository private constructor(context: Context) {

    private val context: Context = context.applicationContext

    companion object {
        private val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        private val KEY_PAST_EXCUSES_JSON = stringPreferencesKey("ai_past_excuses_json")
        const val COOLDOWN_DURATION_MS = 30 * 60 * 1000L // 30 minutes lockout

        @Volatile
        private var instance: AiGatekeeperRepository? = null

        fun getInstance(context: Context): AiGatekeeperRepository {
            return instance ?: synchronized(this) {
                instance ?: AiGatekeeperRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    // ─── API Key Management ──────────────────────────────────────────────────

    val groqApiKeyFlow: Flow<String> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_GROQ_API_KEY] ?: "" }

    suspend fun getGroqApiKey(): String = groqApiKeyFlow.first()

    suspend fun setGroqApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GROQ_API_KEY] = apiKey.trim()
        }
    }

    // ─── Rolling Past Excuses Memory (Last 15) ────────────────────────────────

    val pastExcusesFlow: Flow<List<PastExcuse>> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val jsonString = prefs[KEY_PAST_EXCUSES_JSON] ?: "[]"
            parsePastExcuses(jsonString)
        }

    suspend fun getPastExcuses(): List<PastExcuse> = pastExcusesFlow.first()

    suspend fun recordApprovedExcuse(appName: String, summary: String) {
        val current = getPastExcuses().toMutableList()
        current.add(0, PastExcuse(timestamp = System.currentTimeMillis(), appName = appName, summary = summary))
        // Keep at most 15
        val trimmed = if (current.size > 15) current.take(15) else current

        context.dataStore.edit { prefs ->
            prefs[KEY_PAST_EXCUSES_JSON] = serializePastExcuses(trimmed)
        }
    }

    suspend fun getFormattedPastExcuses(): String {
        val excuses = getPastExcuses()
        if (excuses.isEmpty()) {
            return "No prior approved excuses recorded. (First time user)"
        }
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        excuses.forEachIndexed { index, excuse ->
            val diffMs = now - excuse.timestamp
            val relativeTime = formatRelativeTime(diffMs)
            sb.append("${index + 1}. $relativeTime [${excuse.appName}]: \"${excuse.summary}\"\n")
        }
        return sb.toString().trimEnd()
    }

    private fun formatRelativeTime(diffMs: Long): String {
        val minutes = diffMs / (60 * 1000L)
        val hours = diffMs / (60 * 60 * 1000L)
        val days = diffMs / (24 * 60 * 60 * 1000L)

        return when {
            minutes < 2 -> "Just now"
            minutes < 60 -> "$minutes minutes ago"
            hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
            else -> "$days day${if (days == 1L) "" else "s"} ago"
        }
    }

    private fun parsePastExcuses(json: String): List<PastExcuse> {
        val result = mutableListOf<PastExcuse>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    PastExcuse(
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        appName = obj.optString("appName", "Unknown"),
                        summary = obj.optString("summary", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun serializePastExcuses(list: List<PastExcuse>): String {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("timestamp", item.timestamp)
                put("appName", item.appName)
                put("summary", item.summary)
            }
            array.put(obj)
        }
        return array.toString()
    }

    // ─── Anti-Reset Cooldown Layer (30-Minute Lockout per package) ─────────────

    private fun cooldownKeyForPackage(packageName: String) =
        longPreferencesKey("ai_cooldown_$packageName")

    suspend fun getCooldownRemainingMs(packageName: String): Long {
        val prefs = context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val cooldownUntil = prefs[cooldownKeyForPackage(packageName)] ?: 0L
        val now = System.currentTimeMillis()
        return (cooldownUntil - now).coerceAtLeast(0L)
    }

    suspend fun triggerCooldown(packageName: String) {
        val lockoutUntil = System.currentTimeMillis() + COOLDOWN_DURATION_MS
        context.dataStore.edit { prefs ->
            prefs[cooldownKeyForPackage(packageName)] = lockoutUntil
        }
    }

    fun observeCooldownRemainingMs(packageName: String): Flow<Long> {
        return context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                val cooldownUntil = prefs[cooldownKeyForPackage(packageName)] ?: 0L
                val now = System.currentTimeMillis()
                (cooldownUntil - now).coerceAtLeast(0L)
            }
    }

    // ─── Grant Temporary Unlock Pass ──────────────────────────────────────────

    /**
     * Reuses the existing temporary unlock preference map in DataStore
     * so FocusGateAccessibilityService immediately permits access.
     */
    suspend fun grantTemporaryUnlock(packageName: String, minutes: Int) {
        val durationMs = minutes * 60 * 1000L
        val expiresAt = System.currentTimeMillis() + durationMs

        val keyTempMap = stringPreferencesKey("challenge_temp_unlock_map")
        context.dataStore.edit { prefs ->
            val existing = prefs[keyTempMap] ?: ""
            val map = existing.split(",")
                .mapNotNull { entry ->
                    val parts = entry.split("=")
                    if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
                }.toMap().toMutableMap()

            map[packageName] = expiresAt
            prefs[keyTempMap] = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
    }
}
