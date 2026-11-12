package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.myapplication.data.AiGatekeeperRepository
import com.example.myapplication.data.AppBlockRepository
import com.example.myapplication.data.AppInfo
import com.example.myapplication.data.Challenge
import com.example.myapplication.data.ChallengeRepository
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

/**
 * Fullscreen Blocking Activity presented whenever a restricted app is launched
 * and no active temporary unlock pass exists.
 */
class BlockingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appBlockRepo = AppBlockRepository.getInstance(applicationContext)
        val challengeRepo = ChallengeRepository.getInstance(applicationContext)
        val aiRepo = AiGatekeeperRepository.getInstance(applicationContext)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val scope = rememberCoroutineScope()
                var appInfo by remember { mutableStateOf<AppInfo?>(null) }
                var currentChallenge by remember { mutableStateOf<Challenge?>(null) }
                var isChallengeLoading by remember { mutableStateOf(false) }

                val groqApiKey by aiRepo.groqApiKeyFlow.collectAsState(initial = "")
                val cooldownRemainingMs by aiRepo.observeCooldownRemainingMs(targetPackage)
                    .collectAsState(initial = 0L)

                // Load app metadata once
                LaunchedEffect(targetPackage) {
                    if (targetPackage.isNotBlank()) {
                        appInfo = appBlockRepo.getAppInfo(targetPackage)
                    }
                }

                BlockOverlayScreen(
                    appInfo = appInfo,
                    blockedPackage = targetPackage,
                    currentChallenge = currentChallenge,
                    isChallengeLoading = isChallengeLoading,
                    groqApiKey = groqApiKey,
                    aiCooldownRemainingMs = cooldownRemainingMs,
                    onReturnHome = {
                        returnToHome()
                    },
                    onRequestChallenge = {
                        // Lazily load a challenge when user taps "Solve to Unlock"
                        scope.launch {
                            isChallengeLoading = true
                            currentChallenge = challengeRepo.getNextChallenge()
                            isChallengeLoading = false
                        }
                    },
                    onChallengeCorrect = {
                        // Grant a 5-min temporary pass and close the overlay
                        scope.launch {
                            challengeRepo.recordSuccessfulUnlock(targetPackage)
                            finish()
                        }
                    },
                    onChallengeWrong = {
                        // Load a fresh challenge of the same difficulty
                        scope.launch {
                            isChallengeLoading = true
                            currentChallenge = challengeRepo.getNextChallenge()
                            isChallengeLoading = false
                        }
                    },
                    onAiNegotiationApproved = { grantedMinutes, excuseSummary, appLabel ->
                        scope.launch {
                            aiRepo.recordApprovedExcuse(appLabel, excuseSummary)
                            aiRepo.grantTemporaryUnlock(targetPackage, grantedMinutes)
                            finish()
                        }
                    },
                    onAiNegotiationDeniedOrExhausted = {
                        scope.launch {
                            aiRepo.triggerCooldown(targetPackage)
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun returnToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
