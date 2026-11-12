package com.example.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

sealed class GitHubApiResult {
    data class Success(val todayTotalCommits: Int) : GitHubApiResult()
    data class Error(val message: String) : GitHubApiResult()
}

/**
 * Lightweight GitHub GraphQL API client using plain HttpURLConnection (no third-party deps).
 * Fetches today's total commit count (public + private restricted) for a given user.
 * Compatible with API 24+.
 */
object GitHubApiService {

    private const val ENDPOINT = "https://api.github.com/graphql"
    private const val TIMEOUT_MS = 5_000

    private fun isoFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /** Local midnight today expressed as a UTC ISO-8601 string. */
    private fun todayMidnightUtcIso(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return isoFormatter().format(cal.time)
    }

    /** Current instant expressed as a UTC ISO-8601 string. */
    private fun nowUtcIso(): String = isoFormatter().format(java.util.Date())

    suspend fun fetchTodayCommits(username: String, pat: String): GitHubApiResult =
        withContext(Dispatchers.IO) {
            try {
                val from = todayMidnightUtcIso()
                val to = nowUtcIso()

                val query = """
                    query(${'$'}login: String!, ${'$'}from: DateTime!, ${'$'}to: DateTime!) {
                      user(login: ${'$'}login) {
                        contributionsCollection(from: ${'$'}from, to: ${'$'}to) {
                          totalCommitContributions
                          restrictedContributionsCount
                        }
                      }
                    }
                """.trimIndent()

                val variables = JSONObject().apply {
                    put("login", username.trim())
                    put("from", from)
                    put("to", to)
                }

                val requestBody = JSONObject().apply {
                    put("query", query)
                    put("variables", variables)
                }.toString()

                val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Authorization", "Bearer ${pat.trim()}")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "FocusGate-Android")
                    doOutput = true
                }

                OutputStreamWriter(connection.outputStream).use { it.write(requestBody) }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                }
                connection.disconnect()

                if (responseCode != 200) {
                    return@withContext GitHubApiResult.Error("HTTP $responseCode: $responseBody")
                }

                val root = JSONObject(responseBody)

                // Check for GraphQL-level errors
                if (root.has("errors")) {
                    val firstError = root.getJSONArray("errors").getJSONObject(0)
                    val msg = firstError.optString("message", "GraphQL error")
                    return@withContext GitHubApiResult.Error(msg)
                }

                val data = root.optJSONObject("data")
                val user = data?.optJSONObject("user")
                    ?: return@withContext GitHubApiResult.Error("User \"$username\" not found")

                val collection = user.optJSONObject("contributionsCollection")
                    ?: return@withContext GitHubApiResult.Error("No contributions data returned")

                val totalPublic = collection.optInt("totalCommitContributions", 0)
                val totalRestricted = collection.optInt("restrictedContributionsCount", 0)

                GitHubApiResult.Success(todayTotalCommits = totalPublic + totalRestricted)

            } catch (e: Exception) {
                GitHubApiResult.Error(e.message ?: "Network error")
            }
        }
}
