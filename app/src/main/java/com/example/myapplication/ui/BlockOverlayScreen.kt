package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import com.example.myapplication.ui.components.CatMood
import com.example.myapplication.ui.components.FocusCatMascot
import com.example.myapplication.ui.theme.*
import com.example.myapplication.ui.util.NetworkUtils
import com.example.myapplication.ui.util.rememberAppIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
//  Local palette shorthands
// ══════════════════════════════════════════════════════════════════════════════
private val Bg       = ObsidianBackground
private val Surf     = CharcoalSurface
private val SurfVar  = CharcoalSurfaceVar
private val Amber    = AmberPrimary
private val AmberD   = AmberDeep
private val Peach    = PeachSand
private val White    = WarmWhite
private val Muted    = MutedTaupe
private val Crimson  = CrimsonAlert
private val CodeBg   = Color(0xFF1A1612)

private enum class OverlayMode {
    DEFAULT, CODE_CHALLENGE, AI_NEGOTIATION, GITHUB_BANK, ACCESS_GRANTED
}

// ══════════════════════════════════════════════════════════════════════════════
//  BlockOverlayScreen — entry-point
// ══════════════════════════════════════════════════════════════════════════════
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
    onGitHubUnlockGranted: () -> Unit = {},
    onContinueToApp: () -> Unit = onReturnHome,
) {
    BackHandler { onReturnHome() }

    val isOnline by NetworkUtils.rememberIsOnline()
    var currentMode by remember { mutableStateOf(OverlayMode.DEFAULT) }
    var grantedMinutesForCelebration by remember { mutableIntStateOf(10) }

    LaunchedEffect(currentChallenge) {
        if (currentChallenge != null && currentMode == OverlayMode.DEFAULT) {
            currentMode = OverlayMode.CODE_CHALLENGE
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedContent(
            targetState = currentMode,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "ScreenSwitch"
        ) { mode ->
            when (mode) {
                OverlayMode.DEFAULT -> BlockingPane(
                    appInfo = appInfo,
                    blockedPackage = blockedPackage,
                    isChallengeLoading = isChallengeLoading,
                    isOnline = isOnline,
                    groqApiKey = groqApiKey,
                    aiCooldownRemainingMs = aiCooldownRemainingMs,
                    onReturnHome = onReturnHome,
                    onRequestChallenge = onRequestChallenge,
                    onOpenAiNegotiation = { currentMode = OverlayMode.AI_NEGOTIATION },
                    onOpenGitHubBank = { currentMode = OverlayMode.GITHUB_BANK }
                )
                OverlayMode.CODE_CHALLENGE -> if (currentChallenge != null) {
                    ChallengePane(
                        challenge = currentChallenge,
                        onReturnHome = onReturnHome,
                        onCorrect = {
                            grantedMinutesForCelebration = 5
                            currentMode = OverlayMode.ACCESS_GRANTED
                            onChallengeCorrect()
                        },
                        onWrong = onChallengeWrong,
                        onSwitchToAi = { currentMode = OverlayMode.AI_NEGOTIATION },
                        canSwitchToAi = isOnline && groqApiKey.isNotBlank() && aiCooldownRemainingMs <= 0L
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Amber)
                    }
                }
                OverlayMode.AI_NEGOTIATION -> AiNegotiationPane(
                    appInfo = appInfo,
                    blockedPackage = blockedPackage,
                    groqApiKey = groqApiKey,
                    isOnline = isOnline,
                    aiCooldownRemainingMs = aiCooldownRemainingMs,
                    onReturnHome = onReturnHome,
                    onSwitchToCode = { currentMode = OverlayMode.CODE_CHALLENGE; onRequestChallenge() },
                    onApproved = { mins, summary, label ->
                        grantedMinutesForCelebration = mins
                        currentMode = OverlayMode.ACCESS_GRANTED
                        onAiNegotiationApproved(mins, summary, label)
                    },
                    onDeniedOrExhausted = onAiNegotiationDeniedOrExhausted
                )
                OverlayMode.GITHUB_BANK -> GitHubBankPane(
                    blockedPackage = blockedPackage,
                    isOnline = isOnline,
                    onReturnHome = onReturnHome,
                    onUnlockGranted = { minutes ->
                        grantedMinutesForCelebration = minutes
                        currentMode = OverlayMode.ACCESS_GRANTED
                        onGitHubUnlockGranted()
                    }
                )
                OverlayMode.ACCESS_GRANTED -> AccessGrantedPane(
                    grantedMinutes = grantedMinutesForCelebration,
                    onContinue = onContinueToApp
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Step 1 — "Not so fast." Gate Landing
// ══════════════════════════════════════════════════════════════════════════════
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
    onOpenGitHubBank: () -> Unit,
) {
    val iconBitmap = rememberAppIcon(appInfo?.icon)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Suspicious Cat peeking ──────────────────────────────────────────
        FocusCatMascot(
            mood = CatMood.SUSPICIOUS,
            modifier = Modifier.size(160.dp, 120.dp)
        )

        Spacer(Modifier.height(16.dp))

        // ── App icon ────────────────────────────────────────────────────────
        if (iconBitmap != null) {
            Box(
                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)).background(SurfVar),
                contentAlignment = Alignment.Center
            ) {
                Image(bitmap = iconBitmap, contentDescription = appInfo?.appName, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            }
            Spacer(Modifier.height(10.dp))
        }

        Text(
            "Not so fast.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "You were trying to open ${appInfo?.appName ?: blockedPackage} again, weren't you?",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(24.dp))

        // ── THE DEAL card ───────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Surf),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("THE DEAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("10 MINUTES", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Amber)
                }
                Text("Earn your access by completing one challenge.", style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center)

                Spacer(Modifier.height(18.dp))

                // ── 3 selector tiles ────────────────────────────────────────
                val isCooldownActive = aiCooldownRemainingMs > 0L
                val isAiConfigured = groqApiKey.isNotBlank()
                val canUseAi = isOnline && !isCooldownActive && isAiConfigured
                val cooldownMins = (aiCooldownRemainingMs / (60 * 1000L)) + 1

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // AI Intent tile
                    ChallengeTile(
                        icon = Icons.Default.Psychology,
                        label = "AI Intent",
                        badge = when {
                            !isOnline -> "Offline"
                            isCooldownActive -> "${cooldownMins}m cooldown"
                            !isAiConfigured -> "Key needed"
                            else -> null
                        },
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAiNegotiation
                    )
                    // Algo Ransom tile
                    ChallengeTile(
                        icon = Icons.Default.Code,
                        label = "Algo Ransom",
                        badge = if (isChallengeLoading) "Loading…" else null,
                        modifier = Modifier.weight(1f),
                        onClick = onRequestChallenge
                    )
                    // Git Proof tile
                    ChallengeTile(
                        icon = Icons.Default.Commit,
                        label = "Git Proof",
                        badge = null,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenGitHubBank
                    )
                }

                Spacer(Modifier.height(18.dp))

                // ── Primary CTA ─────────────────────────────────────────────
                Button(
                    onClick = onRequestChallenge,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = ObsidianBackground),
                    enabled = !isChallengeLoading
                ) {
                    if (isChallengeLoading) {
                        CircularProgressIndicator(color = ObsidianBackground, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Loading…", fontWeight = FontWeight.ExtraBold)
                    } else {
                        Text("Choose your challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Give up link ────────────────────────────────────────────────────
        TextButton(onClick = onReturnHome) {
            Icon(Icons.Default.Home, null, tint = Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Give up and return home", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChallengeTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = SurfVar,
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = Amber, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = White, textAlign = TextAlign.Center)
            if (badge != null) {
                Surface(shape = CircleShape, color = CrimsonAlertContainer) {
                    Text(badge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Crimson, fontSize = 9.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Step 2a — AI Intent Evaluator
// ══════════════════════════════════════════════════════════════════════════════
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
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var userInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var finalDecision by remember { mutableStateOf<String?>(null) }
    // Eye tracking — synced to text length
    val eyeOffset = remember(userInput) {
        if (userInput.isEmpty()) 0f else ((userInput.length % 20) / 10f) - 1f
    }

    val userMessagesSent = messages.count { it.role == "user" }
    val messagesRemaining = (3 - userMessagesSent).coerceAtLeast(0)
    val isLockedByCooldown = aiCooldownRemainingMs > 0L || finalDecision == "DENIED"

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(ChatMessage(role = "assistant", content = "Why do you need emergency access to $targetAppName right now? Be specific. You have 3 messages to convince me."))
        }
    }

    val sendAction: () -> Unit = {
        if (userInput.isNotBlank() && !isSending && messagesRemaining > 0 && !isLockedByCooldown && isOnline) {
            val text = userInput.trim()
            userInput = ""
            focusManager.clearFocus()
            errorMessage = null
            messages.add(ChatMessage(role = "user", content = text))
            isSending = true
            scope.launch {
                val pastExcuses = repository.getFormattedPastExcuses()
                val result = GroqApiService.negotiateUnlock(groqApiKey, targetAppName, pastExcuses, messages.toList())
                isSending = false
                when (result) {
                    is GroqApiResult.Success -> {
                        val r = result.data
                        messages.add(ChatMessage(role = "assistant", content = r.reply))
                        val newUserCount = messages.count { it.role == "user" }
                        when {
                            r.decision == "APPROVED" -> {
                                finalDecision = "APPROVED"
                                onApproved(r.grantedMinutes, r.excuseSummary.ifBlank { "Emergency task" }, targetAppName)
                            }
                            r.decision == "DENIED" || newUserCount >= 3 -> {
                                finalDecision = "DENIED"
                                onDeniedOrExhausted()
                            }
                        }
                    }
                    is GroqApiResult.Error -> errorMessage = "Connection dropped — switch to offline Code Challenge."
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            FocusCatMascot(mood = CatMood.EVALUATING, eyeLookOffsetX = eyeOffset, modifier = Modifier.size(80.dp, 70.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AI Intent Evaluator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
                Text("Groq Llama 3.3 70B", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            // Messages left pill
            Surface(shape = CircleShape, color = if (messagesRemaining > 0 && !isLockedByCooldown) AmberContainer else CrimsonAlertContainer) {
                Text(
                    if (isLockedByCooldown) "Locked (30m)" else "Messages: $messagesRemaining / 3",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLockedByCooldown) Crimson else Amber,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            IconButton(onClick = onReturnHome) {
                Icon(Icons.Default.Close, "Return home", tint = Muted)
            }
        }

        // Error banner
        AnimatedVisibility(visible = errorMessage != null) {
            Surface(shape = RoundedCornerShape(10.dp), color = CrimsonAlertContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, null, tint = Crimson, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMessage ?: "", style = MaterialTheme.typography.bodySmall, color = Crimson, modifier = Modifier.weight(1f))
                }
            }
        }

        // Cooldown alert
        if (isLockedByCooldown) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CrimsonAlertContainer,
                border = BorderStroke(1.dp, Crimson.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Negotiation Failed — AI Locked for 30m", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Crimson)
                    Spacer(Modifier.height(2.dp))
                    Text("Solve an offline Code Challenge instead.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }

        // ── Chat bubbles ─────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp),
                        color = if (isUser) AmberContainer else Surf,
                        border = if (!isUser) BorderStroke(1.dp, SubtleBorder) else null,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(if (isUser) "You" else "Gatekeeper AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isUser) Amber else Peach)
                            Spacer(Modifier.height(4.dp))
                            Text(msg.content, style = MaterialTheme.typography.bodyMedium, color = White)
                        }
                    }
                }
            }
            if (isSending) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Surf) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Amber)
                                Spacer(Modifier.width(8.dp))
                                Text("Analyzing argument…", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                    }
                }
            }
        }

        // ── Input / Locked actions ───────────────────────────────────────────
        if (isLockedByCooldown) {
            Column {
                Button(onClick = onSwitchToCode, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = ObsidianBackground), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Switch to Code Challenge (Offline)", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onReturnHome, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted), modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Text("Go Back to Focus")
                }
            }
        } else {
            // Dark textarea + Submit button (0 / 200 style)
            Column {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { if (it.length <= 200) userInput = it },
                    placeholder = { Text("Your exact, urgent reason…", color = Muted) },
                    enabled = !isSending && messagesRemaining > 0 && isOnline,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendAction() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber,
                        unfocusedBorderColor = SubtleBorder,
                        focusedContainerColor = SurfVar,
                        unfocusedContainerColor = SurfVar,
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        cursorColor = Amber
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("${userInput.length} / 200", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { sendAction() },
                    enabled = userInput.isNotBlank() && !isSending && messagesRemaining > 0 && isOnline,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = ObsidianBackground)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ObsidianBackground, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Analyzing…", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Submit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Tip row
                Surface(shape = RoundedCornerShape(10.dp), color = SurfVar, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tip: Only urgent or work-critical reasons will be accepted.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("No excuses? Try the coding challenge.", style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 11.sp)
                    Text("Code Challenge →", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Amber, modifier = Modifier.clickable { onSwitchToCode() })
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Step 2b — Algo Ransom (DSA Code Challenge)
// ══════════════════════════════════════════════════════════════════════════════
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
    var selectedOption by remember(challenge.id) { mutableStateOf<String?>(null) }
    var selectedLineIndex by remember(challenge.id) { mutableIntStateOf(-1) }
    val parsonsOrder = remember(challenge.id) { mutableStateListOf<Int>() }
    var submitState by remember(challenge.id) { mutableStateOf(SubmitState.IDLE) }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FocusCatMascot(mood = CatMood.EVALUATING, modifier = Modifier.size(70.dp, 60.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Algo Ransom", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
                Text("Solve the puzzle to prove your focus.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            // Difficulty badge
            Surface(shape = RoundedCornerShape(8.dp), color = AmberContainer) {
                Text("TIER ${challenge.difficulty}", color = Amber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            if (canSwitchToAi) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = SurfVar, modifier = Modifier.clickable { onSwitchToAi() }) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, null, tint = Peach, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI", color = White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(onClick = onReturnHome) {
                Icon(Icons.Default.Close, "Return home", tint = Muted)
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(challenge.topic, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
        Spacer(Modifier.height(6.dp))
        Text(challenge.prompt, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        when (challenge.type) {
            ChallengeType.OUTPUT_PREDICTION, ChallengeType.COMPLEXITY -> {
                if (challenge.codeLines.isNotEmpty()) {
                    AmberCodeBlock(lines = challenge.codeLines, numbered = false)
                    Spacer(Modifier.height(16.dp))
                }
                AmberOptionCards(options = challenge.options, selectedOption = selectedOption, submitState = submitState, correctAnswer = challenge.correctAnswer, onSelect = { if (submitState == SubmitState.IDLE) selectedOption = it })
            }
            ChallengeType.FIND_ERROR -> {
                AmberFindErrorBlock(lines = challenge.codeLines, selectedIndex = selectedLineIndex, submitState = submitState, correctIndex = challenge.correctAnswer.trim().toIntOrNull() ?: -1, onSelectLine = { if (submitState == SubmitState.IDLE) selectedLineIndex = it })
            }
            ChallengeType.PARSONS_ORDER -> {
                AmberParsonsBlock(lines = challenge.codeLines, tappedOrder = parsonsOrder, submitState = submitState, onTapLine = { idx -> if (submitState == SubmitState.IDLE && idx !in parsonsOrder) parsonsOrder.add(idx) }, onReset = { parsonsOrder.clear() })
            }
            else -> Text("Unsupported challenge type.", color = Muted)
        }

        AnimatedVisibility(visible = submitState != SubmitState.IDLE) {
            ExplanationCard(isCorrect = submitState == SubmitState.CORRECT, explanation = challenge.explanation)
        }

        Spacer(Modifier.height(20.dp))

        val canSubmit = when (challenge.type) {
            ChallengeType.OUTPUT_PREDICTION, ChallengeType.COMPLEXITY -> selectedOption != null
            ChallengeType.FIND_ERROR -> selectedLineIndex >= 0
            ChallengeType.PARSONS_ORDER -> parsonsOrder.size == challenge.codeLines.size
            else -> false
        }

        Button(
            enabled = canSubmit && submitState == SubmitState.IDLE,
            onClick = {
                val answer = when (challenge.type) {
                    ChallengeType.OUTPUT_PREDICTION, ChallengeType.COMPLEXITY -> selectedOption ?: ""
                    ChallengeType.FIND_ERROR -> selectedLineIndex.toString()
                    ChallengeType.PARSONS_ORDER -> parsonsOrder.joinToString(",")
                    else -> ""
                }
                submitState = if (answer.trim() == challenge.correctAnswer.trim()) SubmitState.CORRECT else SubmitState.WRONG
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = ObsidianBackground),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Submit Answer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        AnimatedVisibility(visible = submitState == SubmitState.CORRECT) {
            Column {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onCorrect, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = ObsidianBackground), modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Unlock 5-Min Pass 🎉", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        AnimatedVisibility(visible = submitState == SubmitState.WRONG) {
            Column {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onWrong, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfVar, contentColor = White)) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Try Next Challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Bottom progress
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("1 / 1", style = MaterialTheme.typography.labelSmall, color = Muted)
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(progress = { 1f }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)), color = Amber, trackColor = SurfVar)
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onReturnHome, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted), modifier = Modifier.fillMaxWidth().height(44.dp), border = BorderStroke(1.dp, SubtleBorder)) {
            Text("Go Back / Stay Focused", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Code block (amber-themed) ─────────────────────────────────────────────────
@Composable
private fun AmberCodeBlock(lines: List<String>, numbered: Boolean) {
    Surface(shape = RoundedCornerShape(12.dp), color = CodeBg, border = BorderStroke(1.dp, SubtleBorder), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState())) {
            lines.forEachIndexed { idx, line ->
                Row {
                    if (numbered) Text("$idx  ", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Muted)
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE8D5BC))
                }
            }
        }
    }
}

// ── Option cards (amber-themed) ───────────────────────────────────────────────
@Composable
private fun AmberOptionCards(options: List<String>, selectedOption: String?, submitState: SubmitState, correctAnswer: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { opt ->
            val isSelected = opt == selectedOption
            val borderColor by animateColorAsState(
                targetValue = when {
                    submitState == SubmitState.IDLE && isSelected -> Amber
                    submitState != SubmitState.IDLE && opt == correctAnswer -> Amber
                    submitState == SubmitState.WRONG && isSelected -> Crimson
                    else -> SubtleBorder
                }, label = "OptBorder"
            )
            val bgColor by animateColorAsState(
                targetValue = when {
                    submitState == SubmitState.IDLE && isSelected -> AmberContainer
                    submitState != SubmitState.IDLE && opt == correctAnswer -> AmberContainer.copy(alpha = 0.6f)
                    submitState == SubmitState.WRONG && isSelected -> CrimsonAlertContainer
                    else -> SurfVar
                }, label = "OptBg"
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(opt) }
            ) {
                Text(opt, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
            }
        }
    }
}

@Composable
private fun AmberFindErrorBlock(lines: List<String>, selectedIndex: Int, submitState: SubmitState, correctIndex: Int, onSelectLine: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = CodeBg, border = BorderStroke(1.dp, SubtleBorder), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            lines.forEachIndexed { idx, line ->
                val isSelected = idx == selectedIndex
                val lineColor by animateColorAsState(
                    targetValue = when {
                        submitState != SubmitState.IDLE && idx == correctIndex -> AmberContainer
                        submitState == SubmitState.WRONG && isSelected -> CrimsonAlertContainer
                        isSelected -> AmberContainer.copy(alpha = 0.6f)
                        else -> Color.Transparent
                    }, label = "LineHl_$idx"
                )
                Row(modifier = Modifier.fillMaxWidth().background(lineColor).clickable { onSelectLine(idx) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$idx", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted, modifier = Modifier.width(28.dp))
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE8D5BC))
                }
            }
        }
    }
}

@Composable
private fun AmberParsonsBlock(lines: List<String>, tappedOrder: List<Int>, submitState: SubmitState, onTapLine: (Int) -> Unit, onReset: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(if (tappedOrder.isEmpty()) "Tap lines below to build order…" else "Order: ${tappedOrder.joinToString(" → ") { "[$it]" }}", style = MaterialTheme.typography.bodySmall, color = if (tappedOrder.isEmpty()) Muted else Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (tappedOrder.isNotEmpty() && submitState == SubmitState.IDLE) {
                IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Reset", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        lines.forEachIndexed { idx, line ->
            val posInOrder = tappedOrder.indexOf(idx)
            val alreadyTapped = posInOrder >= 0
            val borderColor by animateColorAsState(targetValue = when { alreadyTapped -> Amber; else -> SubtleBorder }, label = "PBorder_$idx")
            val bgColor by animateColorAsState(targetValue = when { alreadyTapped -> AmberContainer; else -> SurfVar }, label = "PBg_$idx")
            Surface(shape = RoundedCornerShape(10.dp), color = bgColor, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().clickable(enabled = !alreadyTapped && submitState == SubmitState.IDLE) { onTapLine(idx) }) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = if (alreadyTapped) Amber else SurfVar, modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(if (alreadyTapped) (posInOrder + 1).toString() else idx.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (alreadyTapped) ObsidianBackground else Muted)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE8D5BC), modifier = Modifier.horizontalScroll(rememberScrollState()))
                }
            }
        }
    }
}

@Composable
private fun ExplanationCard(isCorrect: Boolean, explanation: String) {
    val border = if (isCorrect) Amber else Crimson
    val bg = if (isCorrect) AmberContainer else CrimsonAlertContainer
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = bg), border = BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(if (isCorrect) "✓ Correct!" else "✗ Not quite…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = border)
            Spacer(Modifier.height(6.dp))
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Step 2c — Git Proof (GitHub Commit Bank Pane)
// ══════════════════════════════════════════════════════════════════════════════
private sealed class BankSyncState {
    object Idle : BankSyncState()
    object Syncing : BankSyncState()
    data class Done(val newCommits: Int, val bankedMins: Int, val todayTotal: Int) : BankSyncState()
    data class Failed(val reason: String) : BankSyncState()
}

@Composable
private fun GitHubBankPane(
    blockedPackage: String,
    isOnline: Boolean,
    onReturnHome: () -> Unit,
    onUnlockGranted: (minutes: Int) -> Unit,
) {
    val context = LocalContext.current
    val ghRepo = remember { GitHubGateRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    val bankState by ghRepo.bankStateFlow.collectAsState(initial = com.example.myapplication.data.GitHubBankState())
    var syncState by remember { mutableStateOf<BankSyncState>(BankSyncState.Idle) }
    var spendError by remember { mutableStateOf<String?>(null) }

    val username = bankState.username
    val isConfigured = username.isNotBlank() && bankState.pat.isNotBlank()
    val bankedMins = bankState.bankedMinutesAvailable

    LaunchedEffect(isOnline, isConfigured) {
        if (isOnline && isConfigured && syncState is BankSyncState.Idle) {
            syncState = BankSyncState.Syncing
            val result = ghRepo.syncCommitsFromGitHub()
            syncState = result.fold(
                onSuccess = { r -> BankSyncState.Done(r.newCommitsEarned, r.bankedMinutesAvailable, r.todayTotalCommits) },
                onFailure = { BankSyncState.Failed(it.message ?: "Sync failed") }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = AmberContainer, border = BorderStroke(1.dp, Amber.copy(alpha = 0.4f))) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Commit, null, tint = Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("GITHUB COMMIT BANK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = White, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onReturnHome) { Icon(Icons.Default.Close, "Home", tint = Muted) }
        }

        Spacer(Modifier.height(20.dp))

        // Not-configured / offline banners
        if (!isConfigured) {
            Banner(text = "GitHub credentials not configured. Set them up in Settings.", isError = true)
            Spacer(Modifier.height(12.dp))
        } else if (!isOnline) {
            Banner(text = "Offline — Sync disabled. You can still spend already-banked minutes below.", isError = false)
            Spacer(Modifier.height(12.dp))
        }

        // Sync result
        if (isConfigured) {
            AnimatedVisibility(visible = syncState is BankSyncState.Done || syncState is BankSyncState.Failed) {
                val isDone = syncState is BankSyncState.Done
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDone) AmberContainer else CrimsonAlertContainer,
                    border = BorderStroke(1.dp, if (isDone) Amber.copy(alpha = 0.5f) else Crimson.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (val s = syncState) {
                        is BankSyncState.Done -> Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, null, tint = Amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Synced @$username · ${s.todayTotal} commits today${if (s.newCommits > 0) " · +${s.newCommits * GitHubGateRepository.MINUTES_PER_COMMIT} mins earned" else ""}", style = MaterialTheme.typography.bodySmall, color = Amber, fontWeight = FontWeight.SemiBold)
                        }
                        is BankSyncState.Failed -> Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, null, tint = Crimson, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync failed: ${s.reason}", style = MaterialTheme.typography.bodySmall, color = Crimson)
                        }
                        else -> {}
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // Stats card
        Surface(shape = RoundedCornerShape(16.dp), color = Surf, border = BorderStroke(1.dp, SubtleBorder), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Today's Commit Activity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 0.5.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBox("Today's Commits", if (syncState is BankSyncState.Done) "${(syncState as BankSyncState.Done).todayTotal}" else "${bankState.redeemedCommitsToday}", "commits", White)
                    StatBox("Available Bank", "$bankedMins", "mins", Amber)
                }
                Text("1 commit = +${GitHubGateRepository.MINUTES_PER_COMMIT} mins · Bank resets at midnight", style = MaterialTheme.typography.bodySmall, color = Muted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Sync button
        if (isConfigured) {
            Button(
                onClick = {
                    if (!isOnline) return@Button
                    syncState = BankSyncState.Syncing
                    scope.launch {
                        val r = ghRepo.syncCommitsFromGitHub()
                        syncState = r.fold(onSuccess = { s -> BankSyncState.Done(s.newCommitsEarned, s.bankedMinutesAvailable, s.todayTotalCommits) }, onFailure = { BankSyncState.Failed(it.message ?: "Sync failed") })
                    }
                },
                enabled = isOnline && syncState !is BankSyncState.Syncing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SurfVar, contentColor = White)
            ) {
                if (syncState is BankSyncState.Syncing) {
                    CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing Commits…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp), tint = if (isOnline) Amber else Muted)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isOnline) "Sync Commits" else "Offline", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Spend controls
        spendError?.let { err ->
            Text(err, style = MaterialTheme.typography.bodySmall, color = Crimson, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }

        if (!isConfigured) {
            Text("Configure GitHub credentials in Settings to earn screen-time.", style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        } else if (bankedMins < GitHubGateRepository.MINUTES_PER_COMMIT) {
            Surface(shape = RoundedCornerShape(12.dp), color = SurfVar, border = BorderStroke(1.dp, SubtleBorder), modifier = Modifier.fillMaxWidth()) {
                Text("Push a commit (+${GitHubGateRepository.MINUTES_PER_COMMIT} mins) and tap Sync.", style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
        } else {
            Text("SPEND BANKED SCREEN-TIME", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            if (bankedMins >= 15) {
                AmberSpendButton("Unlock 15 Mins") {
                    scope.launch {
                        val ok = ghRepo.spendBankedMinutes(blockedPackage, 15)
                        if (ok) onUnlockGranted(15) else spendError = "Insufficient balance — sync to earn more."
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (bankedMins >= 30) {
                AmberSpendButton("Unlock 30 Mins") {
                    scope.launch {
                        val ok = ghRepo.spendBankedMinutes(blockedPackage, 30)
                        if (ok) onUnlockGranted(30) else spendError = "Insufficient balance."
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (bankedMins >= 60) {
                AmberSpendButton("Unlock 60 Mins") {
                    scope.launch {
                        val ok = ghRepo.spendBankedMinutes(blockedPackage, 60)
                        if (ok) onUnlockGranted(60) else spendError = "Insufficient balance."
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onReturnHome, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted), modifier = Modifier.fillMaxWidth().height(44.dp), border = BorderStroke(1.dp, SubtleBorder)) {
            Text("Go Back / Stay Focused")
        }
    }
}

@Composable
private fun AmberSpendButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = ObsidianBackground), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatBox(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun Banner(text: String, isError: Boolean) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (isError) CrimsonAlertContainer else SurfVar, border = BorderStroke(1.dp, if (isError) Crimson.copy(alpha = 0.5f) else SubtleBorder), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Default.Warning else Icons.Default.WifiOff, null, tint = if (isError) Crimson else Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (isError) Crimson else Muted)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Step 3 — Access Granted! Celebration Screen
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun AccessGrantedPane(grantedMinutes: Int, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FocusCatMascot(mood = CatMood.HAPPY_WAVE, modifier = Modifier.size(180.dp, 160.dp))

        Spacer(Modifier.height(24.dp))

        // Amber checkmark badge
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(AmberContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = Amber, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(20.dp))

        Text("Access Granted!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "You've earned $grantedMinutes minutes of focused access.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = ObsidianBackground)
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}
