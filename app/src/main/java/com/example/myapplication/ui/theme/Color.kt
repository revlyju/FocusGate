package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  FocusGate — Warm Amber & Obsidian Palette
//  Zero greens, zero Material default purples.
// ═══════════════════════════════════════════════════════════════════════════════

// Backgrounds
val ObsidianBackground   = Color(0xFF12100E)
val CharcoalSurface      = Color(0xFF1E1A17)
val CharcoalSurfaceVar   = Color(0xFF28221E)
val SubtleBorder         = Color(0xFF362E28)

// Accent / Primary
val AmberPrimary   = Color(0xFFE08E45)
val AmberDeep      = Color(0xFFC46D24)
val AmberContainer = Color(0xFF3D2A14) // subtle amber fill for containers

// Secondary
val PeachSand  = Color(0xFFE8C39E)
val PeachSandContainer = Color(0xFF4A3520)

// Text
val WarmWhite  = Color(0xFFF5EFEA)
val MutedTaupe = Color(0xFF9E9288)

// Alert
val CrimsonAlert          = Color(0xFFD9534F)
val CrimsonAlertContainer = Color(0xFF4A1515)

// Legacy aliases kept so existing files compile without changes
// (they now map to the amber palette)
@Suppress("unused")
val Purple80      = AmberPrimary
@Suppress("unused")
val PurpleGrey80  = PeachSand
@Suppress("unused")
val Pink80        = PeachSand
@Suppress("unused")
val Purple40      = AmberDeep
@Suppress("unused")
val PurpleGrey40  = MutedTaupe
@Suppress("unused")
val Pink40        = AmberDeep
