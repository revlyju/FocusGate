package com.example.myapplication.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
//  Cat Colour Constants
// ══════════════════════════════════════════════════════════════════════════════
private val CatBody       = Color(0xFF1B1816)
private val CatHighlight  = Color(0xFF2E2925)
private val EarInner      = Color(0xFF5C3D2E)
private val AmberEye      = Color(0xFFE08E45)
private val AmberEyeGlow  = Color(0xFFFFC066)
private val PeachPaw      = Color(0xFFE8C39E)
private val PeachSand     = Color(0xFFE8C39E)
private val SpeechBg      = Color(0xFF28221E)
private val SpeechText    = Color(0xFFF5EFEA)
private val SparkleGold   = Color(0xFFFFD580)
private val CrimsonX      = Color(0xFFD9534F)

// ══════════════════════════════════════════════════════════════════════════════
//  Mood enum
// ══════════════════════════════════════════════════════════════════════════════
enum class CatMood {
    LOUNGING,      // Home dashboard — resting, breathing
    SUSPICIOUS,    // Lockout landing — peeking, narrowed eyes
    EVALUATING,    // AI / Algo screens — leaning forward, eye tracking
    HAPPY_WAVE     // Access granted — curved eyes, waving paw
}

private val wittyRemarks = listOf(
    "Still focused? Good.",
    "Don't even think about Reels.",
    "Push a commit first.",
    "I'm watching that screen time.",
    "Eyes on the work.",
    "Meow... means no.",
)

// ══════════════════════════════════════════════════════════════════════════════
//  Public Composable
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun FocusCatMascot(
    mood: CatMood,
    modifier: Modifier = Modifier,
    /** For EVALUATING mood: horizontal eye-track offset -1f..1f */
    eyeLookOffsetX: Float = 0f,
    /** For HAPPY_WAVE: custom message */
    happyMessage: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // ── Breathing animation (LOUNGING) ──────────────────────────────────────
    val breathAnim = rememberInfiniteTransition(label = "breath")
    val breathY by breathAnim.animateFloat(
        initialValue = 0f, targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathY"
    )

    // ── Eye blink (LOUNGING) ─────────────────────────────────────────────────
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(mood) {
        if (mood == CatMood.LOUNGING) {
            while (true) {
                delay(4000L)
                isBlinking = true
                delay(130L)
                isBlinking = false
            }
        }
    }

    // ── Frustration swirl rotation (SUSPICIOUS) ─────────────────────────────
    val swirlAnim = rememberInfiniteTransition(label = "swirl")
    val swirlAngle by swirlAnim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "swirlAngle"
    )

    // ── Wave paw rotation (HAPPY_WAVE) ───────────────────────────────────────
    val waveAnim = rememberInfiniteTransition(label = "wave")
    val waveAngle by waveAnim.animateFloat(
        initialValue = -18f, targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveAngle"
    )

    // ── Sparkle pulse (HAPPY_WAVE) ───────────────────────────────────────────
    val sparkleAnim = rememberInfiniteTransition(label = "sparkle")
    val sparkleAlpha by sparkleAnim.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleAlpha"
    )

    // ── Tap interaction (ear twitch / head bounce) ───────────────────────────
    var tapBounce by remember { mutableFloatStateOf(0f) }
    var speechRemark by remember { mutableStateOf<String?>(null) }
    val tapBounceAnim by animateFloatAsState(
        targetValue = tapBounce,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tapBounce",
        finishedListener = { tapBounce = 0f }
    )

    var suspiciousSquintExtra by remember { mutableFloatStateOf(0f) }

    var remarkIndex by remember { mutableIntStateOf(0) }

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // Speech bubble (LOUNGING tap / EVALUATING)
        val bubbleText: String? = when (mood) {
            CatMood.LOUNGING    -> speechRemark
            CatMood.EVALUATING  -> happyMessage ?: "Hmm... let's hear a valid reason."
            CatMood.HAPPY_WAVE  -> happyMessage ?: "Access Granted! 🎉"
            else                -> null
        }
        if (bubbleText != null) {
            SpeechBubble(text = bubbleText, modifier = Modifier.align(Alignment.TopCenter))
        }

        Spacer(Modifier.height(if (bubbleText != null) 56.dp else 0.dp))

        Canvas(
            modifier = Modifier
                .size(130.dp, 110.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(mood) {
                    detectTapGestures {
                        when (mood) {
                            CatMood.LOUNGING -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                tapBounce = -10f
                                scope.launch {
                                    speechRemark = wittyRemarks[remarkIndex % wittyRemarks.size]
                                    remarkIndex++
                                    delay(2500L)
                                    speechRemark = null
                                }
                            }
                            CatMood.SUSPICIOUS -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    suspiciousSquintExtra = 0.5f
                                    delay(600L)
                                    suspiciousSquintExtra = 0f
                                }
                            }
                            else -> {}
                        }
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val verticalOffset = when (mood) {
                CatMood.LOUNGING    -> breathY + tapBounceAnim
                CatMood.SUSPICIOUS  -> 8f   // peeking up
                CatMood.EVALUATING  -> -4f  // leaning forward
                CatMood.HAPPY_WAVE  -> 0f
            }

            when (mood) {
                CatMood.LOUNGING    -> drawLoungingCat(cx, cy, verticalOffset, isBlinking)
                CatMood.SUSPICIOUS  -> drawSuspiciousCat(cx, cy, swirlAngle, suspiciousSquintExtra)
                CatMood.EVALUATING  -> drawEvaluatingCat(cx, cy, eyeLookOffsetX)
                CatMood.HAPPY_WAVE  -> drawHappyCat(cx, cy, waveAngle, sparkleAlpha)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Speech Bubble
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .widthIn(max = 220.dp)
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = SpeechBg,
        shadowElevation = 4.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = SpeechText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Drawing helpers — shared primitives
// ══════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawCatHead(cx: Float, cy: Float, radius: Float) {
    // Main head
    drawCircle(CatBody, radius = radius, center = Offset(cx, cy))
    // Ear left
    drawEar(cx - radius * 0.56f, cy - radius * 0.78f, left = true)
    // Ear right
    drawEar(cx + radius * 0.56f, cy - radius * 0.78f, left = false)
}

private fun DrawScope.drawEar(ex: Float, ey: Float, left: Boolean) {
    val path = Path().apply {
        moveTo(ex, ey - 18f)
        lineTo(ex + if (left) -13f else 13f, ey + 8f)
        lineTo(ex + if (left) 10f else -10f, ey + 8f)
        close()
    }
    drawPath(path, CatBody)
    val innerPath = Path().apply {
        moveTo(ex, ey - 11f)
        lineTo(ex + if (left) -8f else 8f, ey + 4f)
        lineTo(ex + if (left) 5f else -5f, ey + 4f)
        close()
    }
    drawPath(innerPath, EarInner)
}

private fun DrawScope.drawEyes(
    cx: Float, cy: Float,
    eyeY: Float,
    pupilOffsetX: Float = 0f,
    isBlinking: Boolean = false,
    isNarrowed: Boolean = false,
    isHappy: Boolean = false,
    narrowFactor: Float = 0f,
) {
    val lx = cx - 18f
    val rx = cx + 18f
    if (isBlinking) {
        drawLine(CatHighlight, Offset(lx - 8f, eyeY), Offset(lx + 8f, eyeY), strokeWidth = 3f)
        drawLine(CatHighlight, Offset(rx - 8f, eyeY), Offset(rx + 8f, eyeY), strokeWidth = 3f)
        return
    }
    if (isHappy) {
        // ^ ^ happy curved eyes
        val p1 = Path().apply {
            moveTo(lx - 9f, eyeY + 3f)
            quadraticBezierTo(lx, eyeY - 9f, lx + 9f, eyeY + 3f)
        }
        val p2 = Path().apply {
            moveTo(rx - 9f, eyeY + 3f)
            quadraticBezierTo(rx, eyeY - 9f, rx + 9f, eyeY + 3f)
        }
        drawPath(p1, AmberEye, style = Stroke(3f))
        drawPath(p2, AmberEye, style = Stroke(3f))
        return
    }
    val eyeW = 9f
    val eyeH = if (isNarrowed) (eyeW * (0.3f + narrowFactor * 0.3f)) else eyeW
    // Irises
    drawOval(AmberEye, topLeft = Offset(lx - eyeW, eyeY - eyeH), size = Size(eyeW * 2, eyeH * 2))
    drawOval(AmberEye, topLeft = Offset(rx - eyeW, eyeY - eyeH), size = Size(eyeW * 2, eyeH * 2))
    // Pupils
    val pupX = pupilOffsetX * 4f
    drawCircle(Color(0xFF0D0B09), radius = eyeW * 0.5f, center = Offset(lx + pupX, eyeY))
    drawCircle(Color(0xFF0D0B09), radius = eyeW * 0.5f, center = Offset(rx + pupX, eyeY))
    // Glow sparkles
    drawCircle(AmberEyeGlow, radius = 2.5f, center = Offset(lx - 3f + pupX, eyeY - 3f))
    drawCircle(AmberEyeGlow, radius = 2.5f, center = Offset(rx - 3f + pupX, eyeY - 3f))
}

private fun DrawScope.drawNose(cx: Float, ny: Float) {
    val p = Path().apply {
        moveTo(cx, ny)
        lineTo(cx - 4f, ny - 4f)
        lineTo(cx + 4f, ny - 4f)
        close()
    }
    drawPath(p, PeachSand)
}

private fun DrawScope.drawWhiskers(cx: Float, wy: Float) {
    val whiskerColor = CatHighlight
    val sw = 1.5f
    // Left
    drawLine(whiskerColor, Offset(cx - 12f, wy), Offset(cx - 32f, wy - 3f), sw)
    drawLine(whiskerColor, Offset(cx - 12f, wy + 2f), Offset(cx - 32f, wy + 4f), sw)
    // Right
    drawLine(whiskerColor, Offset(cx + 12f, wy), Offset(cx + 32f, wy - 3f), sw)
    drawLine(whiskerColor, Offset(cx + 12f, wy + 2f), Offset(cx + 32f, wy + 4f), sw)
}

// ══════════════════════════════════════════════════════════════════════════════
//  Mood drawing functions
// ══════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawLoungingCat(cx: Float, cy: Float, breathY: Float, isBlinking: Boolean) {
    val headY = cy - 10f + breathY
    // Body (oval, tucked paws)
    drawOval(CatBody, topLeft = Offset(cx - 42f, cy + 20f), size = Size(84f, 46f))
    // Paws peeking over (peach-tinted)
    drawRoundRect(
        color = PeachPaw.copy(alpha = 0.85f),
        topLeft = Offset(cx - 38f, cy + 14f),
        size = Size(28f, 14f),
        cornerRadius = CornerRadius(7f)
    )
    drawRoundRect(
        color = PeachPaw.copy(alpha = 0.85f),
        topLeft = Offset(cx + 10f, cy + 14f),
        size = Size(28f, 14f),
        cornerRadius = CornerRadius(7f)
    )
    // Head
    drawCatHead(cx, headY, 36f)
    // Face
    drawEyes(cx, headY, headY - 4f, isBlinking = isBlinking)
    drawNose(cx, headY + 10f)
    drawWhiskers(cx, headY + 10f)
}

private fun DrawScope.drawSuspiciousCat(cx: Float, cy: Float, swirlAngle: Float, squintExtra: Float) {
    // Only top of head / ears peeking above an invisible ledge
    val headY = cy + 24f
    // Head
    drawCatHead(cx, headY, 38f)
    // Narrowed suspicious eyes
    drawEyes(cx, headY, headY - 6f, isNarrowed = true, narrowFactor = squintExtra)
    drawNose(cx, headY + 11f)
    drawWhiskers(cx, headY + 11f)
    // Animated swirl "@" glyph above head
    rotate(swirlAngle, pivot = Offset(cx, cy - 18f)) {
        drawCircle(
            CrimsonX,
            radius = 10f,
            center = Offset(cx, cy - 18f),
            style = Stroke(2.5f)
        )
        drawLine(CrimsonX, Offset(cx + 7f, cy - 14f), Offset(cx + 10f, cy - 10f), 2.5f)
    }
}

private fun DrawScope.drawEvaluatingCat(cx: Float, cy: Float, eyeOffsetX: Float) {
    val headY = cy - 6f
    // Body leaning slightly forward
    drawOval(CatBody, topLeft = Offset(cx - 36f, cy + 22f), size = Size(72f, 40f))
    drawCatHead(cx, headY, 36f)
    drawEyes(cx, headY, headY - 4f, pupilOffsetX = eyeOffsetX)
    drawNose(cx, headY + 10f)
    drawWhiskers(cx, headY + 10f)
}

private fun DrawScope.drawHappyCat(cx: Float, cy: Float, waveAngle: Float, sparkleAlpha: Float) {
    val headY = cy - 10f
    // Body
    drawOval(CatBody, topLeft = Offset(cx - 38f, cy + 20f), size = Size(76f, 44f))
    // Waving paw (right side, rotates around shoulder point)
    rotate(waveAngle, pivot = Offset(cx + 36f, cy + 18f)) {
        drawOval(
            PeachSand,
            topLeft = Offset(cx + 28f, cy + 4f),
            size = Size(22f, 32f)
        )
        // Paw pads
        drawCircle(EarInner, radius = 5f, center = Offset(cx + 39f, cy + 32f))
        drawCircle(EarInner, radius = 3f, center = Offset(cx + 32f, cy + 28f))
        drawCircle(EarInner, radius = 3f, center = Offset(cx + 46f, cy + 28f))
    }
    drawCatHead(cx, headY, 36f)
    // Happy ^ ^ eyes
    drawEyes(cx, headY, headY - 2f, isHappy = true)
    drawNose(cx, headY + 10f)
    drawWhiskers(cx, headY + 10f)
    // Smile
    val smilePath = Path().apply {
        moveTo(cx - 10f, headY + 15f)
        quadraticBezierTo(cx, headY + 22f, cx + 10f, headY + 15f)
    }
    drawPath(smilePath, PeachSand, style = Stroke(2.5f))
    // Sparkle lines
    val sparkle = SparkleGold.copy(alpha = sparkleAlpha)
    listOf(
        Offset(cx - 48f, cy - 20f) to Offset(cx - 38f, cy - 32f),
        Offset(cx + 38f, cy - 20f) to Offset(cx + 48f, cy - 32f),
        Offset(cx - 52f, cy + 2f)  to Offset(cx - 42f, cy - 8f),
        Offset(cx + 42f, cy + 2f)  to Offset(cx + 52f, cy - 8f),
    ).forEach { (s, e) ->
        drawLine(sparkle, s, e, 2.5f)
    }
}
