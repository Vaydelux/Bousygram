package com.vaydelux.bousygram.presentation.search

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.TvFocusSurface

fun buildVoiceSearchPrompt(searchMode: SearchMode, inChatTitle: String): String {
    return if (searchMode == SearchMode.InChat) {
        "Search in ${inChatTitle.ifBlank { "current source" }}"
    } else {
        "Speak a video or audio title"
    }
}

/**
 * Checks whether speech recognition services or voice-search handlers
 * are available on the active Android device.
 */
fun isVoiceSearchAvailable(context: Context): Boolean {
    return try {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            true
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            activities.isNotEmpty()
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * A vector-rendered microphone icon designed for crisp rendering at any display density.
 * Pulses gently when [isListening] is true for clear visual status feedback.
 */
@Composable
fun VoiceMicIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    isListening: Boolean = false,
    size: Dp = 20.dp
) {
    val scale = if (isListening) {
        val transition = rememberInfiniteTransition(label = "micPulse")
        val animatedScale by transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        animatedScale
    } else {
        1f
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Capsule (main microphone body)
        val capsuleWidth = w * 0.38f
        val capsuleHeight = h * 0.50f
        val capsuleLeft = (w - capsuleWidth) / 2f
        val capsuleTop = h * 0.10f
        val cornerRadius = CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f)

        drawRoundRect(
            color = tint,
            topLeft = Offset(capsuleLeft, capsuleTop),
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = cornerRadius
        )

        // Arc around capsule
        val strokeWidth = 2.dp.toPx()
        val arcRadius = w * 0.30f
        val arcRect = Rect(
            left = (w / 2f) - arcRadius,
            top = capsuleTop + (capsuleHeight * 0.22f),
            right = (w / 2f) + arcRadius,
            bottom = capsuleTop + (capsuleHeight * 0.22f) + (arcRadius * 2f)
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Vertical stem
        val arcBottom = arcRect.bottom
        val stemBottom = h * 0.88f
        drawLine(
            color = tint,
            start = Offset(w / 2f, arcBottom),
            end = Offset(w / 2f, stemBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Base bar
        val baseHalfWidth = w * 0.22f
        drawLine(
            color = tint,
            start = Offset((w / 2f) - baseHalfWidth, stemBottom),
            end = Offset((w / 2f) + baseHalfWidth, stemBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Accessible Voice Search button built for Google Android TV and touch devices.
 * Integrates [TvFocusSurface] for full D-pad remote focus, animated glow, and TalkBack semantics.
 */
@Composable
fun VoiceSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    isCompact: Boolean = false,
    enabled: Boolean = true
) {
    val description = if (isListening) {
        "Listening for voice query"
    } else {
        "Voice search. Speak a video or audio title"
    }

    TvFocusSurface(
        onClick = onClick,
        selected = isListening,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 44.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                stateDescription = if (isListening) "Listening" else "Ready"
                onClick(label = "Start voice search") {
                    onClick()
                    true
                }
            }
    ) { focused ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val iconTint = when {
                    isListening -> MaterialTheme.colorScheme.error
                    focused -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                VoiceMicIcon(
                    tint = iconTint,
                    isListening = isListening,
                    size = 18.dp
                )
                Text(
                    text = if (isListening) "Listening..." else if (isCompact) "Voice" else "Voice Search",
                    color = when {
                        isListening -> MaterialTheme.colorScheme.error
                        focused -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
