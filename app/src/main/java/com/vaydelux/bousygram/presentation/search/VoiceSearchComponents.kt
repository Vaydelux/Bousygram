package com.vaydelux.bousygram.presentation.search

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import androidx.core.content.ContextCompat
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import java.util.Locale

fun buildVoiceSearchPrompt(searchMode: SearchMode, inChatTitle: String): String {
    return if (searchMode == SearchMode.InChat) {
        "Search in ${inChatTitle.ifBlank { "current source" }}"
    } else {
        "Speak a video or audio title"
    }
}

/** Maps device language to recognizer tags explicitly supported by Bousygram search. */
fun voiceSearchLanguageTag(locale: Locale = Locale.getDefault()): String =
    when (locale.language.lowercase(Locale.ROOT)) {
        "fil", "tl" -> "fil-PH"
        else -> if (locale.country.equals("PH", ignoreCase = true)) "en-PH" else "en-US"
    }

/**
 * Extracts and sanitizes the top recognized speech query from a [RecognizerIntent] or
 * [SpeechRecognizer] results list.
 */
fun extractRecognizedVoiceQuery(candidates: List<String>?): String? {
    return candidates
        ?.asSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() }
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
 * Builds a configured [Intent] for [SpeechRecognizer] or [RecognizerIntent.ACTION_RECOGNIZE_SPEECH].
 */
fun createSpeechRecognizerIntent(
    prompt: String,
    languageTag: String = voiceSearchLanguageTag()
): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
}

@Stable
class VoiceSearchController internal constructor(
    val isListening: Boolean,
    val partialTranscript: String,
    private val onToggleAction: () -> Unit,
    private val onStopAction: () -> Unit
) {
    fun toggleVoiceSearch() = onToggleAction()
    fun startVoiceSearch() = onToggleAction()
    fun stopVoiceSearch() = onStopAction()
}

/**
 * Remembers a [VoiceSearchController] that captures spoken search queries from the device
 * microphone, handles runtime `RECORD_AUDIO` permission requests, streams live partial transcripts,
 * and converts spoken audio to text using Android's [SpeechRecognizer] with automatic fallback to
 * [RecognizerIntent.ACTION_RECOGNIZE_SPEECH].
 */
@Composable
fun rememberVoiceSearchController(
    promptProvider: () -> String,
    onPartialText: (String) -> Unit = {},
    onQueryRecognized: (String) -> Unit
): VoiceSearchController {
    val context = LocalContext.current
    val currentPromptProvider by rememberUpdatedState(promptProvider)
    val currentOnPartialText by rememberUpdatedState(onPartialText)
    val currentOnQueryRecognized by rememberUpdatedState(onQueryRecognized)

    var isListening by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val intentFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        partialTranscript = ""
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = extractRecognizedVoiceQuery(matches)
            if (!recognized.isNullOrBlank()) {
                currentOnPartialText(recognized)
                currentOnQueryRecognized(recognized)
            }
        }
    }

    fun launchIntentFallback() {
        val intent = createSpeechRecognizerIntent(prompt = currentPromptProvider())
        try {
            isListening = true
            intentFallbackLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            isListening = false
            Toast.makeText(context, "Voice search is not available on this device", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            isListening = false
            Toast.makeText(context, "Unable to activate voice search", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopListeningInternal() {
        isListening = false
        partialTranscript = ""
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        }
    }

    fun startMicrophoneCapture() {
        val prompt = currentPromptProvider()
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                    speechRecognizer = it
                }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        partialTranscript = ""
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                Toast.makeText(context, "Didn't catch that. Try speaking again.", Toast.LENGTH_SHORT).show()
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                Toast.makeText(context, "Microphone permission is required for voice search", Toast.LENGTH_SHORT).show()
                            }
                            SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                // Fallback to system RecognizerIntent dialog
                                launchIntentFallback()
                            }
                            else -> {
                                launchIntentFallback()
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        partialTranscript = ""
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val recognized = extractRecognizedVoiceQuery(matches)
                        if (!recognized.isNullOrBlank()) {
                            currentOnPartialText(recognized)
                            currentOnQueryRecognized(recognized)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = extractRecognizedVoiceQuery(matches)
                        if (!partial.isNullOrBlank()) {
                            partialTranscript = partial
                            currentOnPartialText(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                isListening = true
                partialTranscript = ""
                recognizer.startListening(createSpeechRecognizerIntent(prompt = prompt))
            } catch (_: Exception) {
                launchIntentFallback()
            }
        } else {
            launchIntentFallback()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMicrophoneCapture()
        } else {
            // Even if direct RECORD_AUDIO was denied, system RecognizerIntent activity may handle its own mic permission
            Toast.makeText(context, "Microphone permission is needed for direct voice search", Toast.LENGTH_SHORT).show()
            launchIntentFallback()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
        }
    }

    val toggleAction = {
        if (isListening) {
            stopListeningInternal()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                startMicrophoneCapture()
            } else {
                try {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } catch (_: Exception) {
                    launchIntentFallback()
                }
            }
        }
    }

    return remember(isListening, partialTranscript) {
        VoiceSearchController(
            isListening = isListening,
            partialTranscript = partialTranscript,
            onToggleAction = toggleAction,
            onStopAction = ::stopListeningInternal
        )
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
            initialValue = 0.92f,
            targetValue = 1.24f,
            animationSpec = infiniteRepeatable(
                animation = tween(550),
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
 * Embedded microphone button for inside a search bar (`trailingIcon`), providing a 48.dp minimum
 * touch target, visual listening ring, D-pad focus support, and TalkBack accessibility semantics.
 */
@Composable
fun SearchBarVoiceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isListening: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val description = if (isListening) {
        "Stop voice search"
    } else {
        "Voice search using microphone"
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(end = 4.dp)
            .clip(CircleShape)
            .background(
                when {
                    isListening -> colors.error.copy(alpha = 0.20f)
                    focused -> colors.primary.copy(alpha = 0.20f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isListening || focused) 1.5.dp else 0.dp,
                color = when {
                    isListening -> colors.error
                    focused -> colors.primary
                    else -> Color.Transparent
                },
                shape = CircleShape
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = colors.primary),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                stateDescription = if (isListening) "Listening" else "Ready"
                onClick(label = description) {
                    onClick()
                    true
                }
            }
            .testTag("search_bar_voice_button"),
        contentAlignment = Alignment.Center
    ) {
        VoiceMicIcon(
            tint = when {
                isListening -> colors.error
                focused -> colors.primary
                else -> colors.primary
            },
            isListening = isListening,
            size = 20.dp
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
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                stateDescription = if (isListening) "Listening" else "Ready"
                onClick(label = "Start voice search") {
                    onClick()
                    true
                }
            }
            .testTag("voice_search_button")
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
