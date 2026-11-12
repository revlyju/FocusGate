package com.example.myapplication.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.myapplication.data.AppBlockRepository
import com.example.myapplication.data.ChallengeRepository
import com.example.myapplication.ui.BlockingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Core AccessibilityService that monitors foreground package state changes,
 * intercepts launches of restricted applications, enforces system-critical package guardrails,
 * and respects active temporary unlock passes.
 */
class FocusGateAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusGateAccessService"
        private const val DEBOUNCE_THRESHOLD_MS = 1200L

        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var appBlockRepository: AppBlockRepository
    private lateinit var challengeRepository: ChallengeRepository

    @Volatile
    private var activeBlockedPackages: Set<String> = emptySet()

    @Volatile
    private var protectedPackages: Set<String> = emptySet()

    /**
     * Live mirror of the temp-unlock map: packageName → expiryTimestampMs.
     * Updated reactively from DataStore so the event callback never suspends.
     */
    @Volatile
    private var activeTempUnlocks: Map<String, Long> = emptyMap()

    private var lastBlockedPackage: String? = null
    private var lastTriggerTimestamp: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "FocusGate Accessibility Service connected.")

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        appBlockRepository = AppBlockRepository.getInstance(applicationContext)
        challengeRepository = ChallengeRepository.getInstance(applicationContext)

        // Resolve immutable/critical protected packages
        protectedPackages = appBlockRepository.getProtectedPackages()

        // Keep in-memory blocked package list synced with DataStore
        serviceScope.launch {
            appBlockRepository.blockedPackagesFlow.collectLatest { blockedList ->
                // Ensure protected packages can never sneak into the active blocked list
                activeBlockedPackages = blockedList - protectedPackages
                Log.d(TAG, "Updated active blocked list: ${activeBlockedPackages.size} apps.")
            }
        }

        // Keep the temp-unlock map in sync so event callbacks can check without suspending
        serviceScope.launch {
            challengeRepository.tempUnlockMapFlow.collectLatest { map ->
                activeTempUnlocks = map
                Log.d(TAG, "Updated temp-unlock map: ${map.size} active passes.")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if ((event == null) || (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            return
        }

        val targetPackage = event.packageName?.toString() ?: return

        // ── Critical System & Self Protection Guardrail (Prevent Self-Lockout) ─
        if (targetPackage in protectedPackages || targetPackage == applicationContext.packageName) {
            return
        }

        // Only act on packages that are explicitly blocked
        if (!activeBlockedPackages.contains(targetPackage)) return

        // ── Temporary unlock pass check ────────────────────────────────────────
        val expiresAt = activeTempUnlocks[targetPackage]
        if (expiresAt != null && System.currentTimeMillis() < expiresAt) {
            Log.d(TAG, "Temp unlock active for $targetPackage (expires in ${expiresAt - System.currentTimeMillis()} ms). Allowing through.")
            return
        }

        // ── Debounce repeated rapid events ────────────────────────────────────
        val now = SystemClock.uptimeMillis()
        if ((targetPackage == lastBlockedPackage) && ((now - lastTriggerTimestamp) < DEBOUNCE_THRESHOLD_MS)) {
            return
        }
        lastBlockedPackage = targetPackage
        lastTriggerTimestamp = now

        Log.w(TAG, "Access blocked to distracting app: $targetPackage. Launching FocusGate overlay.")
        launchBlockingOverlay(targetPackage)
    }

    private fun launchBlockingOverlay(packageName: String) {
        try {
            val intent = Intent(this, BlockingActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                putExtra(BlockingActivity.EXTRA_PACKAGE_NAME, packageName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockingActivity for $packageName", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusGate Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        Log.i(TAG, "FocusGate Accessibility Service destroyed.")
    }
}
