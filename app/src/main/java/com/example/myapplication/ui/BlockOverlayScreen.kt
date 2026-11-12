package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.AiGatekeeperRepository
import com.example.myapplication.data.AppInfo
import com.example.myapplication.data.Challenge
import com.example.myapplication.data.ChallengeType
import com.example.myapplication.data.ChatMessage
import com.example.myapplication.data.GroqApiResult
import com.example.myapplication.data.GroqApiService
import com.example.myapplication.ui.util.NetworkUtils
import com.example.myapplication.ui.util.rememberAppIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
//  Design constants
// ═══════════════════════════════════════════════════════════════════════════════

private val BgDark = Color(0xFF0F172A)
private val BgMid = Color(0xFF1E293B)
private val AccentBlue = Color(0xFF2563EB)
private val AccentTeal = Color(0xFF10B981)
private val AccentPurple = Color(0xFF8B5CF6)
private val SlateText = Color(0xFF94A3B8)
private val RedAlert = Color(0xFFEF4444)
private val CodeBg = Color(0xFF1A2332)
private val CorrectGreen = Color(0xFF22C55E)
private val WrongRed = Color(0xFFEF4444)

private enum class OverlayMode {
    DEFAULT,
    CODE_CHALLENGE,
    AI_NEGOTIATION
}

/**
 * Full-screen blocking overlay supporting both the DSA Coding Challenge gate
 * and the Bring-Your-Own-Key (BYOK) Groq "Convince the AI" negotiation gate.
 */
@Composable
fun BlockOverlayScreen(
    appInfo: AppInfo?,
    blockedPackage: String,
    currentChallenge: Challenge?,
    isChallengeLoading: Boolean,
    groqApiKey: String,
    aiCooldownRemainingMs: Long,
    onReturnHome: () -> Unit,
    onRequestChallenge: () -> Unit,
    onChallengeCorrect: () -> Unit,
    onChallengeWrong: () -> Unit,
    onAiNegotiationApproved: (grantedMinutes: Int, excuseSummary: String, appLabel: String) -> Unit,
    onAiNegotiationDeniedOrExhausted: () -> Unit,
) {
    BackHandler { onReturnHome() }

    val isOnline by NetworkUtils.rememberIsOnline()
    var currentMode by remember { mutableStateOf(OverlayMode.DEFAULT) }

    // When a challenge is loaded while in DEFAULT, switch to CODE_CHALLENGE
    LaunchedEffect(currentChallenge) {
        if (currentChallenge != null && currentMode == OverlayMode.DEFAULT) {
            currentMode = OverlayMode.CODE_CHALLENGE
        }
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(BgDark, BgMid, BgDark),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = currentMode,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "ScreenSwitch",
        ) { mode ->
            when (mode) {
                OverlayMode.DEFAULT -> {
                    BlockingPane(
                        appInfo = appInfo,
                        blockedPackage = blockedPackage,
                        isChallengeLoading = isChallengeLoading,
                        isOnline = isOnline,
                        groqApiKey = groqApiKey,
                        aiCooldownRemainingMs = aiCooldownRemainingMs,
                        onReturnHome = onReturnHome,
                        onRequestChallenge = onRequestChallenge,
                        onOpenAiNegotiation = { currentMode = OverlayMode.AI_NEGOTIATION },
                    )
                }

                OverlayMode.CODE_CHALLENGE -> {
                    if (currentChallenge != null) {
                        ChallengePane(
                            challenge = currentChallenge,
                            onReturnHome = onReturnHome,
                            onCorrect = onChallengeCorrect,
                            onWrong = onChallengeWrong,
                            onSwitchToAi = {
                                currentMode = OverlayMode.AI_NEGOTIATION
                            },
                            canSwitchToAi = isOnline && groqApiKey.isNotBlank() && aiCooldownRemainingMs <= 0L,
                        )
                    } else {
                        // Fallback loader if challenge not yet arrived
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentTeal)
                        }
                    }
                }

                OverlayMode.AI_NEGOTIATION -> {
                    AiNegotiationPane(
                        appInfo = appInfo,
                        blockedPackage = blockedPackage,
                        groqApiKey = groqApiKey,
                        isOnline = isOnline,
                        aiCooldownRemainingMs = aiCooldownRemainingMs,
                        onReturnHome = onReturnHome,
                        onSwitchToCode = {
                            currentMode = OverlayMode.CODE_CHALLENGE
                            onRequestChallenge()
                        },
                        onApproved = onAiNegotiationApproved,
                        onDeniedOrExhausted = onAiNegotiationDeniedOrExhausted,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  BlockingPane – the initial "stay focused" view with Mode Switcher
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BlockingPane(
    appInfo: AppInfo?,
    blockedPackage: String,
    isChallengeLoading: Boolean,
    isOnline: Boolean,
    groqApiKey: String,
    aiCooldownRemainingMs: Long,
    onReturnHome: () -> Unit,
    onRequestChallenge: () -> Unit,
    onOpenAiNegotiation: () -> Unit,
) {
    var remainingSeconds by remember { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
    }

    val progress by animateFloatAsState(
        targetValue = remainingSeconds / 10f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "LockoutProgress",
    )

    val iconBitmap = rememberAppIcon(appInfo?.icon)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // ── Status badge ──────────────────────────────────────────────────────
        Surface(
            shape = CircleShape,
            color = RedAlert.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, RedAlert.copy(alpha = 0.6f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Lock, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "FOCUSGATE GUARD ACTIVE",
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── App icon ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (iconBitmap != null) {
                Image(bitmap = iconBitmap, contentDescription = appInfo?.appName ?: blockedPackage, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
            } else {
                Icon(Icons.Default.HourglassTop, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "${appInfo?.appName ?: blockedPackage} is Blocked",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Take a breath. Remember your goals and what truly deserves your attention right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(24.dp))

        // ── Countdown ring ────────────────────────────────────────────────────
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(105.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color.White.copy(alpha = 0.1f),
                strokeWidth = 7.dp,
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = if (remainingSeconds > 0) Color(0xFF38BDF8) else AccentTeal,
                strokeWidth = 7.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (remainingSeconds > 0) remainingSeconds.toString() else "✓",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    if (remainingSeconds > 0) "seconds" else "ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateText,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Primary Go Back button ────────────────────────────────────────────
        Button(
            onClick = onReturnHome,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Home, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Go Back / Stay Focused", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        // ── Dual Unlock Modes Section ─────────────────────────────────────────
        Text(
            "EARN AN EMERGENCY UNLOCK PASS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SlateText,
            letterSpacing = 1.sp,
        )

        Spacer(Modifier.height(10.dp))

        // Button 1: Solve Code Challenge
        OutlinedButton(
            onClick = onRequestChallenge,
            enabled = !isChallengeLoading,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (isChallengeLoading) {
                CircularProgressIndicator(
                    color = AccentTeal,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Loading challenge…", style = MaterialTheme.typography.bodyLarge)
            } else {
                Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Solve Code Challenge (Offline)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Button 2: Convince the AI (Connectivity & Cooldown aware)
        val isCooldownActive = aiCooldownRemainingMs > 0L
        val isAiConfigured = groqApiKey.isNotBlank()
        val canUseAi = isOnline && !isCooldownActive && isAiConfigured

        val cooldownMinsLeft = (aiCooldownRemainingMs / (60 * 1000L)) + 1

        OutlinedButton(
            onClick = onOpenAiNegotiation,
            enabled = canUseAi,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (canUseAi) AccentPurple.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (canUseAi) AccentPurple else SlateText,
                disabledContentColor = SlateText.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Icon(Icons.Default.Psychology, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    !isOnline -> "Offline — Use Code Challenge"
                    isCooldownActive -> "AI Locked (${cooldownMinsLeft}m cooldown)"
                    !isAiConfigured -> "Convince AI (Key Required in Settings)"
                    else -> "Convince the AI (Groq Llama 3.3)"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  AiNegotiationPane – 3-Turn Negotiation Chat with Groq Llama 3.3
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AiNegotiationPane(
    appInfo: AppInfo?,
    blockedPackage: String,
    groqApiKey: String,
    isOnline: Boolean,
    aiCooldownRemainingMs: Long,
    onReturnHome: () -> Unit,
    onSwitchToCode: () -> Unit,
    onApproved: (grantedMinutes: Int, excuseSummary: String, appLabel: String) -> Unit,
    onDeniedOrExhausted: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { AiGatekeeperRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val targetAppName = appInfo?.appName ?: blockedPackage

    // Conversation state: user has at most 3 messages
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var userMessageInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var finalDecision by remember { mutableStateOf<String?>(null) } // "APPROVED" | "DENIED"
    var approvedMinutes by remember { mutableIntStateOf(0) }

    val userMessagesSent = messages.count { it.role == "user" }
    val messagesRemaining = (3 - userMessagesSent).coerceAtLeast(0)

    val isLockedByCooldown = aiCooldownRemainingMs > 0L || finalDecision == "DENIED"

    // Auto-scroll on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Initial greeting from AI
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    role = "assistant",
                    content = "Why do you need emergency access to $targetAppName right now? Be specific. You have 3 messages to convince me."
                )
            )
        }
    }

    val sendMessageAction: () -> Unit = {
        if (userMessageInput.isNotBlank() && !isSending && messagesRemaining > 0 && !isLockedByCooldown && isOnline) {
            val userText = userMessageInput.trim()
            userMessageInput = ""
            focusManager.clearFocus()
            errorMessage = null

            messages.add(ChatMessage(role = "user", content = userText))
            isSending = true

            scope.launch {
                val pastExcusesFormatted = repository.getFormattedPastExcuses()
                val result = GroqApiService.negotiateUnlock(
                    apiKey = groqApiKey,
                    targetAppName = targetAppName,
                    pastExcusesFormatted = pastExcusesFormatted,
                    conversationHistory = messages.toList()
                )

                isSending = false

                when (result) {
                    is GroqApiResult.Success -> {
                        val response = result.data
                        messages.add(ChatMessage(role = "assistant", content = response.reply))

                        val newUserCount = messages.count { it.role == "user" }

                        when {
                            response.decision == "APPROVED" -> {
                                finalDecision = "APPROVED"
                                approvedMinutes = response.grantedMinutes
                                onApproved(
                                    response.grantedMinutes,
                                    response.excuseSummary.ifBlank { "Constructive emergency task" },
                                    targetAppName
                                )
                            }
                            response.decision == "DENIED" || newUserCount >= 3 -> {
                                finalDecision = "DENIED"
                                onDeniedOrExhausted()
                            }
                            else -> {
                                // "CONTINUE" and messages < 3: waiting for user's next response
                            }
                        }
                    }
                    is GroqApiResult.Error -> {
                        errorMessage = "Connection dropped — switch to offline Code Challenge."
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // ── Top Header ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AccentPurple.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Psychology, null, tint = AccentPurple, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "AI GATEKEEPER · GROQ 70B",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Message Counter Badge
            Surface(
                shape = CircleShape,
                color = if (messagesRemaining > 0 && !isLockedByCooldown) MaterialTheme.colorScheme.surfaceVariant else WrongRed.copy(alpha = 0.2f)
            ) {
                Text(
                    text = if (isLockedByCooldown) "Locked (30m)" else "Messages: $messagesRemaining / 3",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLockedByCooldown) WrongRed else Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            IconButton(onClick = onReturnHome) {
                Icon(Icons.Default.Home, "Return home", tint = SlateText)
            }
        }

        // Error Banner (e.g. network timeout without burning cooldown)
        AnimatedVisibility(visible = errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Cooldown Alert Banner
        if (isLockedByCooldown) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WrongRed.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, WrongRed.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Negotiation Failed — AI Locked for 30m",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WrongRed
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Closing and reopening the app will not reset this lockout. Solve an offline Code Challenge instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText
                    )
                }
            }
        }

        // ── Chat Bubble List ──────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        ),
                        color = if (isUser) AccentBlue else CodeBg,
                        border = if (isUser) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isUser) "You" else "Gatekeeper AI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) Color.White.copy(alpha = 0.8f) else AccentPurple
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (isSending) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CodeBg,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentPurple)
                                Spacer(Modifier.width(8.dp))
                                Text("Analyzing argument...", style = MaterialTheme.typography.bodySmall, color = SlateText)
                            }
                        }
                    }
                }
            }
        }

        // ── Input or Failure Actions ──────────────────────────────────────────
        if (isLockedByCooldown) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSwitchToCode,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Switch to Code Challenge (Offline)", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onReturnHome,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Go Back to Focus")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userMessageInput,
                    onValueChange = { userMessageInput = it },
                    placeholder = { Text("Your exact, urgent reason...", color = SlateText) },
                    enabled = !isSending && messagesRemaining > 0 && isOnline,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessageAction() }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = { sendMessageAction() },
                    enabled = userMessageInput.isNotBlank() && !isSending && messagesRemaining > 0 && isOnline,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    modifier = Modifier.size(52.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Switch to Code Challenge shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "No excuses? Try the coding challenge instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateText,
                    fontSize = 11.sp
                )
                Text(
                    text = "Code Challenge",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentTeal,
                    modifier = Modifier.clickable { onSwitchToCode() }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ChallengePane – interactive challenge view
// ═══════════════════════════════════════════════════════════════════════════════

/** Tracks interactive submission state inside a challenge. */
private enum class SubmitState { IDLE, CORRECT, WRONG }

@Composable
private fun ChallengePane(
    challenge: Challenge,
    onReturnHome: () -> Unit,
    onCorrect: () -> Unit,
    onWrong: () -> Unit,
    onSwitchToAi: () -> Unit,
    canSwitchToAi: Boolean,
) {
    // Per-challenge state – reset whenever a new challenge arrives
    var selectedOption by remember(challenge.id) { mutableStateOf<String?>(null) }
    var selectedLineIndex by remember(challenge.id) { mutableIntStateOf(-1) }
    val parsonsOrder = remember(challenge.id) { mutableStateListOf<Int>() }
    var submitState by remember(challenge.id) { mutableStateOf(SubmitState.IDLE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (challenge.difficulty) {
                    1 -> Color(0xFF1D4ED8).copy(alpha = 0.4f)
                    2 -> Color(0xFF7C3AED).copy(alpha = 0.4f)
                    3 -> Color(0xFFB45309).copy(alpha = 0.4f)
                    else -> Color(0xFFB91C1C).copy(alpha = 0.4f)
                },
            ) {
                Text(
                    "TIER ${challenge.difficulty} · ${challenge.type.name.replace('_', ' ')}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))

            if (canSwitchToAi) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentPurple.copy(alpha = 0.2f),
                    modifier = Modifier.clickable { onSwitchToAi() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Psychology, null, tint = AccentPurple, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Convince AI", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(4.dp))
            }

            IconButton(onClick = onReturnHome) {
                Icon(Icons.Default.Home, "Return home", tint = SlateText)
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            challenge.topic,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            challenge.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = SlateText,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // ── Challenge body – rendered per type ────────────────────────────────
        when (challenge.type) {
            ChallengeType.OUTPUT_PREDICTION, ChallengeType.COMPLEXITY -> {
                if (challenge.codeLines.isNotEmpty()) {
                    CodeBlock(lines = challenge.codeLines, numbered = false)
                    Spacer(Modifier.height(16.dp))
                }
                OptionCards(
                    options = challenge.options,
                    selectedOption = selectedOption,
                    submitState = submitState,
                    correctAnswer = challenge.correctAnswer,
                    onSelect = { if (submitState == SubmitState.IDLE) selectedOption = it },
                )
            }

            ChallengeType.FIND_ERROR -> {
                FindErrorBlock(
                    lines = challenge.codeLines,
                    selectedIndex = selectedLineIndex,
                    submitState = submitState,
                    correctIndex = challenge.correctAnswer.trim().toIntOrNull() ?: -1,
                    onSelectLine = { if (submitState == SubmitState.IDLE) selectedLineIndex = it },
                )
            }

            ChallengeType.PARSONS_ORDER -> {
                ParsonsBlock(
                    lines = challenge.codeLines,
                    tappedOrder = parsonsOrder,
                    submitState = submitState,
                    onTapLine = { idx ->
                        if ((submitState == SubmitState.IDLE) && (idx !in parsonsOrder)) {
                            parsonsOrder.add(idx)
                        }
                    },
                    onReset = { parsonsOrder.clear() },
                )
            }

            else -> {
                Text("Unsupported challenge type.", color = SlateText)
            }
        }

        // ── Explanation after submission ───────────────────────────────────────
        AnimatedVisibility(visible = submitState != SubmitState.IDLE) {
            ExplanationCard(
                isCorrect = submitState == SubmitState.CORRECT,
                explanation = challenge.explanation,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Submit button ──────────────────────────────────────────────────────
        val canSubmit = when (challenge.type) {
            ChallengeType.OUTPUT_PREDICTION, ChallengeType.COMPLEXITY -> selectedOption != null
            ChallengeType.FIND_ERROR -> selectedLineIndex >= 0
            ChallengeType.PARSONS_ORDER -> parsonsOrder.size == challenge.codeLines.size
            else -> false
        }

        Button(
            enabled = (canSubmit && (submitState == SubmitState.IDLE)),
            onClick = {
                val userAnswer = when (challenge.type) {
                    ChallengeType.OUTPUT_PREDICTION, ChallengeType.COMPLEXITY -> selectedOption ?: ""
                    ChallengeType.FIND_ERROR -> selectedLineIndex.toString()
                    ChallengeType.PARSONS_ORDER -> parsonsOrder.joinToString(",")
                    else -> ""
                }
                submitState = if (userAnswer.trim() == challenge.correctAnswer.trim()) SubmitState.CORRECT else SubmitState.WRONG
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Submit Answer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // ── Outcome action buttons ─────────────────────────────────────────────
        AnimatedVisibility(visible = submitState == SubmitState.CORRECT) {
            Column {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onCorrect,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text("Unlock 5-Min Pass 🎉", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(visible = submitState == SubmitState.WRONG) {
            Column {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onWrong,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Try Next Challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onReturnHome,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateText),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Text("Go Back / Stay Focused", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-composables
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CodeBlock(lines: List<String>, numbered: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CodeBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            lines.forEachIndexed { idx, line ->
                Row {
                    if (numbered) {
                        Text(
                            text = "$idx  ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = SlateText,
                        )
                    }
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCards(
    options: List<String>,
    selectedOption: String?,
    submitState: SubmitState,
    correctAnswer: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { opt ->
            val isSelected = opt == selectedOption
            val borderColor by animateColorAsState(
                targetValue = when {
                    submitState == SubmitState.IDLE && isSelected -> AccentBlue
                    submitState != SubmitState.IDLE && opt == correctAnswer -> CorrectGreen
                    submitState == SubmitState.WRONG && isSelected -> WrongRed
                    else -> Color.White.copy(alpha = 0.1f)
                },
                label = "OptBorder",
            )
            val bgColor by animateColorAsState(
                targetValue = when {
                    submitState == SubmitState.IDLE && isSelected -> AccentBlue.copy(alpha = 0.18f)
                    submitState != SubmitState.IDLE && opt == correctAnswer -> CorrectGreen.copy(alpha = 0.15f)
                    submitState == SubmitState.WRONG && isSelected -> WrongRed.copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
                label = "OptBg",
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(opt) },
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun FindErrorBlock(
    lines: List<String>,
    selectedIndex: Int,
    submitState: SubmitState,
    correctIndex: Int,
    onSelectLine: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CodeBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            lines.forEachIndexed { idx, line ->
                val isSelected = idx == selectedIndex
                val lineColor by animateColorAsState(
                    targetValue = when {
                        submitState != SubmitState.IDLE && idx == correctIndex -> CorrectGreen.copy(alpha = 0.25f)
                        submitState == SubmitState.WRONG && isSelected -> WrongRed.copy(alpha = 0.25f)
                        isSelected -> AccentBlue.copy(alpha = 0.25f)
                        else -> Color.Transparent
                    },
                    label = "LineHl_$idx",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lineColor)
                        .clickable { onSelectLine(idx) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$idx",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = SlateText,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0),
                    )
                }
            }
        }
    }
}

@Composable
private fun ParsonsBlock(
    lines: List<String>,
    tappedOrder: List<Int>,
    submitState: SubmitState,
    onTapLine: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (tappedOrder.isEmpty()) "Tap lines below to build order…" else "Order: ${tappedOrder.joinToString(" → ") { "[$it]" }}",
                style = MaterialTheme.typography.bodySmall,
                color = if (tappedOrder.isEmpty()) SlateText else AccentTeal,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (tappedOrder.isNotEmpty() && submitState == SubmitState.IDLE) {
                IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Reset order", tint = SlateText, modifier = Modifier.size(18.dp))
                }
            }
        }

        lines.forEachIndexed { idx, line ->
            val posInOrder = tappedOrder.indexOf(idx)
            val alreadyTapped = posInOrder >= 0

            val borderColor by animateColorAsState(
                targetValue = when {
                    submitState != SubmitState.IDLE -> if (alreadyTapped) CorrectGreen else Color.White.copy(alpha = 0.1f)
                    alreadyTapped -> AccentTeal
                    else -> Color.White.copy(alpha = 0.12f)
                },
                label = "PBorder_$idx",
            )
            val bgColor by animateColorAsState(
                targetValue = when {
                    submitState != SubmitState.IDLE && alreadyTapped -> CorrectGreen.copy(alpha = 0.1f)
                    alreadyTapped -> AccentTeal.copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
                label = "PBg_$idx",
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = bgColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !alreadyTapped && submitState == SubmitState.IDLE) { onTapLine(idx) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (alreadyTapped) AccentTeal else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(26.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                if (alreadyTapped) (posInOrder + 1).toString() else idx.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alreadyTapped) Color.White else SlateText,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplanationCard(isCorrect: Boolean, explanation: String) {
    val border = if (isCorrect) CorrectGreen else WrongRed
    val bg = border.copy(alpha = 0.12f)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                if (isCorrect) "✓ Correct!" else "✗ Not quite…",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = border,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
            )
        }
    }
}
