package com.example.myapplication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.data.AiGatekeeperRepository
import com.example.myapplication.data.AppInfo
import com.example.myapplication.ui.components.CatMood
import com.example.myapplication.ui.components.FocusCatMascot
import com.example.myapplication.ui.theme.*
import com.example.myapplication.ui.util.rememberAppIcon
import java.util.Calendar

// ══════════════════════════════════════════════════════════════════════════════
//  Local palette shorthands (all from Color.kt — no greens)
// ══════════════════════════════════════════════════════════════════════════════
private val Bg       = ObsidianBackground
private val Surface1 = CharcoalSurface
private val Amber    = AmberPrimary
private val Peach    = PeachSand
private val White    = WarmWhite
private val Muted    = MutedTaupe
private val Crimson  = CrimsonAlert

// ══════════════════════════════════════════════════════════════════════════════
//  Main Composable
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    uiState: AppBlockUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleAppBlock: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRelockApp: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Bg,
        topBar = {
            FocusGateTopBar(onOpenSettings = { selectedTab = 3 })
        },
        bottomBar = {
            FocusGateBottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Amber, strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Scanning applications…", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            when (selectedTab) {
                0 -> HomeTab(
                    uiState = uiState,
                    paddingValues = paddingValues,
                    onRelockApp = onRelockApp,
                    onNavigateToSettings = { selectedTab = 3 }
                )
                1 -> StatsTab(paddingValues = paddingValues)
                2 -> ChallengesTab(paddingValues = paddingValues)
                3 -> SettingsTab(
                    uiState = uiState,
                    paddingValues = paddingValues,
                    onSearchQueryChange = onSearchQueryChange,
                    onToggleAppBlock = onToggleAppBlock
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Top App Bar
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusGateTopBar(onOpenSettings: () -> Unit = {}) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = Peach,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "FocusGate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Muted)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Surface1,
            titleContentColor = White
        )
    )
}

// ══════════════════════════════════════════════════════════════════════════════
//  Bottom Navigation
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun FocusGateBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        Icons.Default.Home      to "Home",
        Icons.Default.BarChart  to "Stats",
        Icons.Default.EmojiEvents to "Challenges",
        Icons.Default.Settings  to "Settings"
    )
    NavigationBar(
        containerColor = Surface1,
        tonalElevation = 0.dp
    ) {
        tabs.forEachIndexed { index, (icon, label) ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Amber,
                    selectedTextColor = Amber,
                    indicatorColor = AmberContainer,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted
                )
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Tab 1 — Home
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeTab(
    uiState: AppBlockUiState,
    paddingValues: PaddingValues,
    onRelockApp: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning,"
            hour < 17 -> "Good afternoon,"
            else      -> "Good evening,"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg).padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Greeting + Mascot ───────────────────────────────────────────────
        item {
            Column {
                Text(greeting, style = MaterialTheme.typography.titleMedium, color = Muted)
                Text(
                    "Focus better today.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        }

        // ── Hero Focus Card with cat resting on top ─────────────────────────
        item {
            Box {
                // Hero metrics card — shifted down to make room for cat paws
                HeroFocusCard(
                    modifier = Modifier.padding(top = 50.dp)
                )
                // Cat sits above the card — paws tucked over the top border
                FocusCatMascot(
                    mood = CatMood.LOUNGING,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-30).dp)
                        .size(140.dp, 100.dp)
                )
            }
        }

        // ── Active Unlock Sessions (countdown + Re-lock Now) ────────────────
        if (uiState.activeUnlockSessions.isNotEmpty()) {
            item {
                ActiveUnlockSessionsCard(
                    sessions = uiState.activeUnlockSessions,
                    onRelockApp = onRelockApp
                )
            }
        }

        // ── Service Status Card (Accessibility + Overlay) ───────────────────
        item {
            StyledServiceStatusCard(
                isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                isOverlayEnabled = uiState.isOverlayPermissionEnabled
            )
        }

        // ── Blocked Apps Quick-Access ───────────────────────────────────────
        item {
            BlockedAppsPreviewCard(
                apps = uiState.installedApps.filter { it.isBlocked },
                onManage = onNavigateToSettings
            )
        }
    }
}

// ── Hero card ─────────────────────────────────────────────────────────────────
@Composable
private fun HeroFocusCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            // Circular Amber progress ring — Focus Time Today placeholder
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = Surface1,
                    strokeWidth = 8.dp
                )
                CircularProgressIndicator(
                    progress = { 0.62f },
                    modifier = Modifier.fillMaxSize(),
                    color = Amber,
                    strokeWidth = 8.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("1h 32m", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = White)
                    Text("Focus Time", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2-column metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricChip(icon = Icons.Default.Shield, label = "Distractions\nBlocked", value = "8")
                Box(modifier = Modifier.width(1.dp).height(48.dp).background(SubtleBorder))
                MetricChip(icon = Icons.AutoMirrored.Filled.TrendingUp, label = "Focus\nStreak", value = "4 day")
            }
        }
    }
}

@Composable
private fun MetricChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 2)
    }
}

// ── Blocked apps preview card ─────────────────────────────────────────────────
@Composable
private fun BlockedAppsPreviewCard(
    apps: List<AppInfo>,
    onManage: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Blocked Apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = White)
                TextButton(onClick = onManage) {
                    Text("Manage →", color = Amber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val preview = apps.take(4)
                preview.forEach { app ->
                    val icon = rememberAppIcon(app.icon)
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(CharcoalSurfaceVar),
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon != null) {
                            Image(bitmap = icon, contentDescription = app.appName, modifier = Modifier.size(34.dp))
                        } else {
                            Icon(Icons.Default.Apps, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                val extra = apps.size - 4
                if (extra > 0) {
                    Surface(shape = CircleShape, color = AmberContainer) {
                        Text(
                            "+$extra",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Amber,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (apps.isEmpty()) {
                    Text("No apps blocked yet. Go to Settings → Apps.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Styled Service Status Card (re-themed wrapper around existing ServiceStatusCard)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun StyledServiceStatusCard(
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean
) {
    ServiceStatusCard(
        isAccessibilityEnabled = isAccessibilityEnabled,
        isOverlayEnabled = isOverlayEnabled
    )
}

// ══════════════════════════════════════════════════════════════════════════════
//  Active Unlock Sessions Card
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ActiveUnlockSessionsCard(
    sessions: List<ActiveUnlockSession>,
    onRelockApp: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        border = BorderStroke(1.dp, Amber.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(AmberContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.HourglassTop, null, tint = Amber, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Active Temporary Unlocks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = White)
                    Text("${sessions.size} app${if (sessions.size > 1) "s" else ""} currently accessible", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            sessions.forEach { session ->
                ActiveSessionRow(session = session, onRelock = { onRelockApp(session.appInfo.packageName) })
            }
        }
    }
}

@Composable
private fun ActiveSessionRow(session: ActiveUnlockSession, onRelock: () -> Unit) {
    val iconBitmap = rememberAppIcon(session.appInfo.icon)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CharcoalSurfaceVar
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Surface1),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconBitmap != null) {
                        Image(bitmap = iconBitmap, contentDescription = session.appInfo.appName, modifier = Modifier.size(30.dp))
                    } else {
                        Icon(Icons.Default.Apps, null, tint = Muted, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.appInfo.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = AmberContainer) {
                        Text(
                            session.formattedTime,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Amber
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onRelock,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = CrimsonAlertContainer,
                    contentColor = Crimson
                )
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Re-lock Now", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Tab 2 — Stats
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun StatsTab(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val aiRepo = remember { AiGatekeeperRepository.getInstance(context) }
    val pastExcuses by aiRepo.pastExcusesFlow.collectAsState(initial = emptyList())

    val tabs = listOf("This Week", "This Month", "All Time")
    var selectedFilter by remember { mutableIntStateOf(0) }
    // Placeholder bar heights (Mon–Sun)
    val barHeights = listOf(0.4f, 0.55f, 0.7f, 0.9f, 0.65f, 0.3f, 0.5f)
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg).padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Statistics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = White)
        }
        // Filter chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { i, label ->
                    FilterChip(
                        selected = selectedFilter == i,
                        onClick = { selectedFilter = i },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber,
                            selectedLabelColor = ObsidianBackground,
                            containerColor = Surface1,
                            labelColor = Muted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == i,
                            borderColor = SubtleBorder,
                            selectedBorderColor = Amber
                        )
                    )
                }
            }
        }
        // Focus Time header
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Surface1), border = BorderStroke(1.dp, SubtleBorder)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Focus Time", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("14h 37m", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = White)
                        Surface(shape = RoundedCornerShape(8.dp), color = AmberContainer) {
                            Text("↑ +32%", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Amber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Bar chart
                    Row(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        barHeights.forEachIndexed { i, h ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Box(
                                    modifier = Modifier.width(28.dp).height((h * 70).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Amber)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(days[i], style = MaterialTheme.typography.labelSmall, color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        // Bottom metrics
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatMiniCard(label = "Distractions\nBlocked", value = "87", icon = Icons.Default.Block, modifier = Modifier.weight(1f))
                StatMiniCard(label = "Streak", value = "12 days 🔥", icon = Icons.Default.LocalFireDepartment, modifier = Modifier.weight(1f))
            }
        }

        // Recent Approved AI Excuses Log
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Surface1),
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, null, tint = Amber, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Recent Approved AI Excuses",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    if (pastExcuses.isEmpty()) {
                        Text(
                            "No approved excuses yet. Stay disciplined!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    } else {
                        pastExcuses.take(5).forEach { excuse ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CharcoalSurfaceVar,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            excuse.appName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Amber
                                        )
                                        val diff = (System.currentTimeMillis() - excuse.timestamp).coerceAtLeast(0L)
                                        val timeStr = when {
                                            diff < 120_000L -> "Just now"
                                            diff < 3600_000L -> "${diff / 60_000L}m ago"
                                            diff < 86400_000L -> "${diff / 3600_000L}h ago"
                                            else -> "${diff / 86400_000L}d ago"
                                        }
                                        Text(
                                            timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Muted
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "\"${excuse.summary}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Surface1), border = BorderStroke(1.dp, SubtleBorder)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Tab 3 — Challenges & Bank
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChallengesTab(paddingValues: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg).padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Challenges & Bank", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = White)
        }
        // GitHub Bank Status Card (live controls)
        item {
            GitHubSettingsCard()
        }
        // DSA stats card
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Surface1), border = BorderStroke(1.dp, SubtleBorder)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = Amber, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Algo Ransom Stats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = White)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatMiniPill(label = "Solved", value = "14")
                        StatMiniPill(label = "Failed", value = "3")
                        StatMiniPill(label = "Streak", value = "5 🔥")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Tab 4 — Settings & Apps
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SettingsTab(
    uiState: AppBlockUiState,
    paddingValues: PaddingValues,
    onSearchQueryChange: (String) -> Unit,
    onToggleAppBlock: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg).padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings & Apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = White)
        }

        // ── AI Gatekeeper (Groq BYOK) ───────────────────────────────────────
        item { AiSettingsCard() }

        // ── GitHub Commit Bank ──────────────────────────────────────────────
        item { GitHubSettingsCard() }

        // ── Blocked Apps Manager header ─────────────────────────────────────
        item {
            Text(
                "Blocked Apps Manager",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Text(
                "Select the apps you want to block.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }

        // Search bar
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps…", color = Muted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                trailingIcon = {
                    AnimatedVisibility(visible = uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, null, tint = Muted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber,
                    unfocusedBorderColor = SubtleBorder,
                    focusedContainerColor = CharcoalSurfaceVar,
                    unfocusedContainerColor = CharcoalSurfaceVar,
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    cursorColor = Amber
                )
            )
        }

        // Empty state
        if (uiState.filteredApps.isEmpty()) {
            item { EmptySearchState(searchQuery = uiState.searchQuery) }
        } else {
            items(items = uiState.filteredApps, key = { it.packageName }) { appInfo ->
                val remaining = uiState.activeUnlockRemainingMap[appInfo.packageName]
                AmberAppItemCard(
                    appInfo = appInfo,
                    remainingSeconds = remaining,
                    onToggleBlock = { onToggleAppBlock(appInfo.packageName) }
                )
            }
        }

        // Save Changes hint
        item {
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = ObsidianBackground)
            ) {
                Text("Save Changes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Amber-themed App item card (replaces old green-toggle version)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun AmberAppItemCard(
    appInfo: AppInfo,
    remainingSeconds: Long? = null,
    onToggleBlock: () -> Unit
) {
    val iconBitmap = rememberAppIcon(appInfo.icon)
    val isTempUnlocked = remainingSeconds != null && remainingSeconds > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isTempUnlocked -> AmberContainer.copy(alpha = 0.5f)
                appInfo.isBlocked -> CrimsonAlertContainer.copy(alpha = 0.4f)
                else -> Surface1
            }
        ),
        border = BorderStroke(1.dp, SubtleBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(CharcoalSurfaceVar),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconBitmap != null) {
                        Image(bitmap = iconBitmap, contentDescription = appInfo.appName, modifier = Modifier.size(36.dp))
                    } else {
                        Icon(Icons.Default.Apps, null, tint = Muted, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            appInfo.appName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isTempUnlocked && remainingSeconds != null) {
                            val min = remainingSeconds / 60
                            val sec = remainingSeconds % 60
                            Surface(shape = RoundedCornerShape(6.dp), color = AmberContainer) {
                                Text(
                                    String.format(java.util.Locale.US, "%02d:%02d", min, sec),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Amber
                                )
                            }
                        }
                    }
                    Text(appInfo.packageName, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = appInfo.isBlocked,
                onCheckedChange = { onToggleBlock() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBackground,
                    checkedTrackColor = Amber,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = CharcoalSurfaceVar
                )
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Empty search state
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun EmptySearchState(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(CharcoalSurfaceVar),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SearchOff, null, tint = Muted, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("No applications found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)
            Spacer(Modifier.height(4.dp))
            Text(
                if (searchQuery.isNotBlank()) "No match for \"$searchQuery\"" else "No applications available",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Preview
// ══════════════════════════════════════════════════════════════════════════════
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AppListScreenPreview() {
    com.example.myapplication.ui.theme.MyApplicationTheme {
        AppListScreen(
            uiState = AppBlockUiState(
                installedApps = listOf(
                    AppInfo("com.instagram.android", "Instagram", isBlocked = true),
                    AppInfo("com.google.android.youtube", "YouTube", isBlocked = true),
                    AppInfo("com.zhiliaoapp.musically", "TikTok", isBlocked = false),
                    AppInfo("com.twitter.android", "X (Twitter)", isBlocked = false),
                ),
                filteredApps = listOf(
                    AppInfo("com.instagram.android", "Instagram", isBlocked = true),
                    AppInfo("com.google.android.youtube", "YouTube", isBlocked = true),
                    AppInfo("com.zhiliaoapp.musically", "TikTok", isBlocked = false),
                    AppInfo("com.twitter.android", "X (Twitter)", isBlocked = false),
                ),
                blockedCount = 2,
                isAccessibilityEnabled = true,
                isOverlayPermissionEnabled = true,
                isLoading = false,
                activeUnlockSessions = listOf(
                    ActiveUnlockSession(
                        appInfo = AppInfo("com.instagram.android", "Instagram", isBlocked = true),
                        remainingSeconds = 275L,
                        expiresAtTimestamp = System.currentTimeMillis() + 275000L
                    )
                ),
                activeUnlockRemainingMap = mapOf("com.instagram.android" to 275L)
            ),
            onSearchQueryChange = {},
            onToggleAppBlock = {},
            onRelockApp = {}
        )
    }
}
