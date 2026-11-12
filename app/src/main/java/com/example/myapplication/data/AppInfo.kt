package com.example.myapplication.data

import android.graphics.drawable.Drawable

/**
 * Represents an installed Android application and its FocusGate blocking state.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isBlocked: Boolean = false,
    val isSystemApp: Boolean = false
)
