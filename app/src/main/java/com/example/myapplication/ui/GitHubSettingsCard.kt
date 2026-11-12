package com.example.myapplication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.GitHubApiResult
import com.example.myapplication.data.GitHubGateRepository
import kotlinx.coroutines.launch

private val GhGreen = com.example.myapplication.ui.theme.AmberPrimary
private val GhDark = com.example.myapplication.ui.theme.CharcoalSurface

private sealed class GhVerifyStatus {
    object Idle : GhVerifyStatus()
    object Loading : GhVerifyStatus()
    data class Connected(val username: String, val commitsToday: Int) : GhVerifyStatus()
    data class Failed(val reason: String) : GhVerifyStatus()
}

/**
 * Dashboard card for configuring GitHub credentials and the Commit Screen-Time Bank.
 * Includes collapsible PAT setup instructions and a "Verify & Save" button.
 */
@Composable
fun GitHubSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { GitHubGateRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    val bankState by repository.bankStateFlow.collectAsState(initial = com.example.myapplication.data.GitHubBankState())

    var usernameInput by remember(bankState.username) { mutableStateOf(bankState.username) }
    var patInput by remember(bankState.pat) { mutableStateOf(bankState.pat) }
    var patVisible by remember { mutableStateOf(false) }
    var helpExpanded by remember { mutableStateOf(false) }
    var verifyStatus by remember { mutableStateOf<GhVerifyStatus>(GhVerifyStatus.Idle) }

    // Derive status from persisted state on first load
    val hasCredentials = bankState.username.isNotBlank() && bankState.pat.isNotBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.example.myapplication.ui.theme.CharcoalSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GhDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "GitHub Commit Bank",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "1 commit = +15 mins screen-time · resets at midnight",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Live Bank Status Chip ─────────────────────────────────────────
            if (hasCredentials) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GhGreen.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = GhGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Today's redeemed commits: ${bankState.redeemedCommitsToday}",
                                style = MaterialTheme.typography.labelSmall,
                                color = GhGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "Bank: ${bankState.bankedMinutesAvailable} min",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GhGreen
                        )
                    }
                }
            }

            // ── Inputs ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it; verifyStatus = GhVerifyStatus.Idle },
                label = { Text("GitHub Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GhGreen,
                    unfocusedBorderColor = com.example.myapplication.ui.theme.SubtleBorder,
                    focusedContainerColor = com.example.myapplication.ui.theme.CharcoalSurfaceVar,
                    unfocusedContainerColor = com.example.myapplication.ui.theme.CharcoalSurfaceVar,
                    focusedTextColor = com.example.myapplication.ui.theme.WarmWhite,
                    unfocusedTextColor = com.example.myapplication.ui.theme.WarmWhite
                )
            )

            OutlinedTextField(
                value = patInput,
                onValueChange = { patInput = it; verifyStatus = GhVerifyStatus.Idle },
                label = { Text("Personal Access Token (github_pat_... or ghp_...)") },
                singleLine = true,
                visualTransformation = if (patVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { patVisible = !patVisible }) {
                        Icon(
                            imageVector = if (patVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (patVisible) "Hide token" else "Show token"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GhGreen,
                    unfocusedBorderColor = com.example.myapplication.ui.theme.SubtleBorder,
                    focusedContainerColor = com.example.myapplication.ui.theme.CharcoalSurfaceVar,
                    unfocusedContainerColor = com.example.myapplication.ui.theme.CharcoalSurfaceVar,
                    focusedTextColor = com.example.myapplication.ui.theme.WarmWhite,
                    unfocusedTextColor = com.example.myapplication.ui.theme.WarmWhite
                )
            )

            // ── Collapsible PAT Setup Guide ───────────────────────────────────
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { helpExpanded = !helpExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "How to get a safe PAT (Zero Code Access)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (helpExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = helpExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(4.dp))

                            PATStep(
                                step = 1,
                                text = "Open GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens (github.com/settings/tokens?type=beta)."
                            )
                            PATStep(
                                step = 2,
                                text = "Click Generate new token, name it \"FocusGate\", and choose an expiration (e.g. 90 days or 1 year)."
                            )
                            PATStep(
                                step = 3,
                                text = "Under Repository access, select Public Repositories (read-only)."
                            )
                            PATStep(
                                step = 4,
                                text = "Under Permissions, leave all repository and account permissions set to No access — zero code read/write access needed."
                            )
                            PATStep(
                                step = 5,
                                text = "Generate, copy the github_pat_... token, and paste it in the field above. It is saved once, locally on your device only."
                            )
                            PATStep(
                                step = 6,
                                text = "(Optional) To count private repo commits too, enable \"Include private contributions on my profile\" in your GitHub Profile Settings."
                            )
                        }
                    }
                }
            }

            // ── Verify & Save Button ──────────────────────────────────────────
            Button(
                onClick = {
                    if (usernameInput.isBlank() || patInput.isBlank()) return@Button
                    verifyStatus = GhVerifyStatus.Loading
                    scope.launch {
                        when (val result = repository.verifyCredentials(usernameInput.trim(), patInput.trim())) {
                            is GitHubApiResult.Success -> {
                                repository.saveCredentials(usernameInput.trim(), patInput.trim())
                                verifyStatus = GhVerifyStatus.Connected(
                                    username = usernameInput.trim(),
                                    commitsToday = result.todayTotalCommits
                                )
                            }
                            is GitHubApiResult.Error -> {
                                verifyStatus = GhVerifyStatus.Failed(result.message)
                            }
                        }
                    }
                },
                enabled = verifyStatus !is GhVerifyStatus.Loading && usernameInput.isNotBlank() && patInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.example.myapplication.ui.theme.AmberPrimary,
                    contentColor = com.example.myapplication.ui.theme.ObsidianBackground
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (verifyStatus is GhVerifyStatus.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Verifying…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verify & Save", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Status Feedback ───────────────────────────────────────────────
            when (val s = verifyStatus) {
                is GhVerifyStatus.Idle -> {
                    if (!hasCredentials) {
                        Text(
                            "Not configured — enter your username and PAT above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                is GhVerifyStatus.Connected -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = GhGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Connected (@${s.username}) — ${s.commitsToday} commits today",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is GhVerifyStatus.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Invalid token / username — ${s.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                GhVerifyStatus.Loading -> { /* spinner shown in button */ }
            }
        }
    }
}

@Composable
private fun PATStep(step: Int, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(1.dp)) {
                Text(
                    step.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
