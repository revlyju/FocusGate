package com.example.myapplication.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Supported challenge interaction types.
 */
enum class ChallengeType {
    OUTPUT_PREDICTION,
    FIND_ERROR,
    COMPLEXITY,
    PARSONS_ORDER,
    UNKNOWN;

    companion object {
        fun from(value: String): ChallengeType = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/**
 * Data model for a single coding challenge loaded from challenges.json.
 *
 * @property id            Unique identifier (e.g. "q_01").
 * @property difficulty    Tier 1–4 mapping to escalating unlock levels.
 * @property type          Interaction pattern for the UI renderer.
 * @property topic         Short display label for the challenge topic.
 * @property prompt        Question text shown to the user.
 * @property codeLines     Lines of code displayed in a monospace block (may be empty).
 * @property options       Answer choices for OUTPUT_PREDICTION and COMPLEXITY types.
 * @property correctAnswer Authoritative answer string; for PARSONS_ORDER this is a
 *                         comma-separated index sequence (e.g. "2,3,0,1").
 * @property explanation   Shown after an incorrect or correct attempt.
 */
data class Challenge(
    val id: String,
    val difficulty: Int,
    val type: ChallengeType,
    val topic: String,
    val prompt: String,
    val codeLines: List<String>,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
) {
    companion object {
        /**
         * Deserialises a single [JSONObject] entry into a [Challenge].
         * Uses only org.json (zero extra Gradle dependencies).
         */
        fun fromJson(obj: JSONObject): Challenge {
            val codeLines = mutableListOf<String>()
            val codeArr = obj.optJSONArray("codeLines")
            if (codeArr != null) {
                for (i in 0 until codeArr.length()) {
                    codeLines.add(codeArr.getString(i))
                }
            }

            val options = mutableListOf<String>()
            val optArr = obj.optJSONArray("options")
            if (optArr != null) {
                for (i in 0 until optArr.length()) {
                    options.add(optArr.getString(i))
                }
            }

            return Challenge(
                id = obj.getString("id"),
                difficulty = obj.getInt("difficulty"),
                type = ChallengeType.from(obj.getString("type")),
                topic = obj.getString("topic"),
                prompt = obj.getString("prompt"),
                codeLines = codeLines,
                options = options,
                correctAnswer = obj.getString("correctAnswer"),
                explanation = obj.getString("explanation"),
            )
        }

        /**
         * Deserialises a full [JSONArray] string into a [List] of [Challenge].
         */
        fun listFromJsonArray(jsonArrayString: String): List<Challenge> {
            val array = JSONArray(jsonArrayString)
            val list = mutableListOf<Challenge>()
            for (i in 0 until array.length()) {
                try {
                    list.add(fromJson(array.getJSONObject(i)))
                } catch (_: Exception) {
                    // Skip malformed entries rather than crashing the app
                }
            }
            return list
        }
    }
}
