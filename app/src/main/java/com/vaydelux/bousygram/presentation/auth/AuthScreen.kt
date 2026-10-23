package com.vaydelux.bousygram.presentation.auth

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.R
import com.vaydelux.bousygram.domain.model.AuthMode
import com.vaydelux.bousygram.domain.model.AuthStep
import com.vaydelux.bousygram.domain.model.AuthUiState
import com.vaydelux.bousygram.presentation.auth.vm.AuthViewModel

/**
 * Enterprise-grade, modern multi-screen authentication interface.
 * Engineered for Android TV (10-ft D-pad navigation), Tablets (dual pane),
 * and Mobile Phones (ergonomic single pane).
 *
 * Runs exclusively through direct TDLib client-side MTProto with zero third-party relays.
 */
@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        ),
                        radius = 1600f
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val isWideScreen = maxWidth >= 840.dp && maxHeight >= 480.dp
            val horizontalPadding = if (isWideScreen) 48.dp else 20.dp
            val verticalPadding = if (isWideScreen) 32.dp else 20.dp

            if (isWideScreen) {
                // Dual-pane layout for TV, landscape tablets, and large screens
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Branding, Enterprise Status, and Dynamic Form
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        EnterpriseBrandHeader(state = state)
                        Spacer(Modifier.height(20.dp))

                        if (state.step == AuthStep.ScanQrCode || state.step == AuthStep.EnterPhone) {
                            AuthModeSelector(
                                currentMode = state.preferredMode,
                                onSelectMode = { mode -> viewModel.selectMode(mode) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(18.dp))
                        }

                        AuthPrimaryCard(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))
                        EnterpriseSecurityFooter()
                    }

                    // Right Column: Dedicated QR Pairing Station
                    QrPairingStation(
                        state = state,
                        onRefresh = viewModel::requestQrCodeLogin,
                        onSwitchToPhone = viewModel::switchToPhoneLogin,
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    )
                }
            } else {
                // Ergonomic single-column scrollable layout for phones and compact tablets
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    EnterpriseBrandHeader(state = state, modifier = Modifier.fillMaxWidth())

                    if (state.step == AuthStep.ScanQrCode || state.step == AuthStep.EnterPhone) {
                        AuthModeSelector(
                            currentMode = state.preferredMode,
                            onSelectMode = { mode -> viewModel.selectMode(mode) },
                            modifier = Modifier.fillMaxWidth().widthInMax(520.dp)
                        )
                    }

                    val showQrPrimary = state.preferredMode == AuthMode.QR &&
                        (state.step == AuthStep.ScanQrCode || state.step == AuthStep.EnterPhone)

                    if (showQrPrimary) {
                        QrPairingStation(
                            state = state,
                            onRefresh = viewModel::requestQrCodeLogin,
                            onSwitchToPhone = viewModel::switchToPhoneLogin,
                            modifier = Modifier.fillMaxWidth().widthInMax(520.dp)
                        )
                    } else {
                        AuthPrimaryCard(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth().widthInMax(520.dp)
                        )
                    }

                    EnterpriseSecurityFooter(modifier = Modifier.widthInMax(520.dp))
                }
            }
        }
    }
}

/**
 * High-visibility enterprise branding header with live connection status.
 */
@Composable
private fun EnterpriseBrandHeader(
    state: AuthUiState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Enterprise Telegram Client",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Enterprise Security & Connection Status Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isReady = state.connectionStatus?.contains("Connected", ignoreCase = true) == true
            val dotColor = if (isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
            
            val transition = rememberInfiniteTransition(label = "pulse")
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alphaPulse"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = if (isReady) 1.0f else alpha))
            )

            Text(
                text = state.connectionStatus ?: stringResource(R.string.auth_connection_default),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Modern dual-mode authentication selector (QR Code vs Phone Number).
 */
@Composable
private fun AuthModeSelector(
    currentMode: AuthMode,
    onSelectMode: (AuthMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeTabItem(
                label = "📱 Instant QR Pair",
                selected = currentMode == AuthMode.QR,
                onClick = { onSelectMode(AuthMode.QR) },
                modifier = Modifier.weight(1f)
            )
            ModeTabItem(
                label = "📞 Phone Number",
                selected = currentMode == AuthMode.Phone,
                onClick = { onSelectMode(AuthMode.Phone) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            isFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            else -> Color.Transparent
        },
        label = "tabBg"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else if (selected) 1.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.secondary else if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
            color = if (selected || isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * The primary card containing dynamic auth steps (API Keys, Phone, Code, 2FA Password).
 */
@Composable
private fun AuthPrimaryCard(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), cardShape)
            .shadow(elevation = 12.dp, shape = cardShape),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = cardShape
    ) {
        Column(
            modifier = Modifier.padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                    )
                ) {
                    Text(
                        text = stepBadgeText(state.step),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                if (state.isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(R.string.auth_working),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = screenTitle(state.step),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            AuthStepFields(state = state, viewModel = viewModel)

            if (!state.message.isNullOrBlank()) {
                val isError = state.message.contains("error", ignoreCase = true) ||
                    state.message.contains("invalid", ignoreCase = true) ||
                    state.message.contains("wait", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message.orEmpty(),
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthStepFields(state: AuthUiState, viewModel: AuthViewModel) {
    when (state.step) {
        AuthStep.EnterApiKeys -> ApiKeysForm(state, viewModel)
        AuthStep.EnterPhone -> PhoneForm(state, viewModel)
        AuthStep.EnterCode -> CodeVerificationForm(state, viewModel)
        AuthStep.EnterPassword -> TwoFactorPasswordForm(state, viewModel)
        AuthStep.ScanQrCode -> {
            Text(
                text = stringResource(R.string.auth_qr_scan_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryAction(
                text = "Switch to Phone Number Login",
                loading = state.isLoading,
                onClick = viewModel::switchToPhoneLogin
            )
        }
        AuthStep.Loading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.auth_preparing_session),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        AuthStep.Authorized -> {
            Text(
                text = stringResource(R.string.auth_signed_in),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PhoneForm(state: AuthUiState, viewModel: AuthViewModel) {
    val phoneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { phoneFocus.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Enter your mobile phone number in international format to receive a one-time verification code from Telegram.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        ModernAuthTextField(
            label = stringResource(R.string.auth_phone_hint),
            placeholder = stringResource(R.string.auth_phone_placeholder),
            value = state.phone,
            onValueChange = viewModel::onPhoneChange,
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.focusRequester(phoneFocus)
        )

        PrimaryAction(
            text = stringResource(R.string.auth_submit_phone),
            loading = state.isLoading,
            onClick = viewModel::submitPhone
        )
    }
}

@Composable
private fun CodeVerificationForm(state: AuthUiState, viewModel: AuthViewModel) {
    val codeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { codeFocus.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "A verification code was sent to your official Telegram app on your phone or computer. Enter it below:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        ModernAuthTextField(
            label = stringResource(R.string.auth_code_hint),
            placeholder = stringResource(R.string.auth_code_placeholder),
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.focusRequester(codeFocus)
        )

        PrimaryAction(
            text = stringResource(R.string.auth_submit),
            loading = state.isLoading,
            onClick = viewModel::submitCode
        )

        SecondaryAction(
            text = "← Back / Change Phone Number",
            loading = state.isLoading,
            onClick = viewModel::switchToPhoneLogin
        )
    }
}

@Composable
private fun TwoFactorPasswordForm(state: AuthUiState, viewModel: AuthViewModel) {
    val passwordFocus = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { passwordFocus.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Your Telegram account is protected by Two-Step Verification. Enter your cloud password:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        ModernAuthTextField(
            label = stringResource(R.string.auth_password_hint),
            placeholder = stringResource(R.string.auth_password_placeholder),
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            isPassword = !passwordVisible,
            modifier = Modifier.focusRequester(passwordFocus)
        )

        PrimaryAction(
            text = stringResource(R.string.auth_submit),
            loading = state.isLoading,
            onClick = viewModel::submitPassword
        )
    }
}

@Composable
private fun ApiKeysForm(state: AuthUiState, viewModel: AuthViewModel) {
    val apiIdFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { apiIdFocus.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Enter your Telegram API credentials from my.telegram.org. These will be stored encrypted on this device using Android Keystore.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        ModernAuthTextField(
            label = stringResource(R.string.auth_api_id_label),
            placeholder = stringResource(R.string.auth_api_id_placeholder),
            value = state.apiId,
            onValueChange = viewModel::onApiIdChange,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.focusRequester(apiIdFocus)
        )

        ModernAuthTextField(
            label = stringResource(R.string.auth_api_hash_label),
            placeholder = stringResource(R.string.auth_api_hash_placeholder),
            value = state.apiHash,
            onValueChange = viewModel::onApiHashChange
        )

        PrimaryAction(
            text = stringResource(R.string.auth_submit_api),
            loading = state.isLoading,
            onClick = viewModel::submitApiKeys
        )
    }
}

/**
 * Dedicated QR Pairing Station for 1-tap instant linking with the official Telegram app.
 */
@Composable
private fun QrPairingStation(
    state: AuthUiState,
    onRefresh: () -> Unit,
    onSwitchToPhone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stationShape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), stationShape)
            .shadow(elevation = 12.dp, shape = stationShape),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = stationShape
    ) {
        Column(
            modifier = Modifier
                .padding(26.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_qr_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Quickly link your account without typing a password or phone number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            QrDisplayBox(state = state, onRefresh = onRefresh)

            // Step-by-step pairing guide
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QrStepRow(step = "1", instruction = "Open Telegram on your mobile phone")
                QrStepRow(step = "2", instruction = "Go to Settings → Devices → Link Desktop Device")
                QrStepRow(step = "3", instruction = "Point your camera at this QR code")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryAction(
                    text = "🔄 " + stringResource(R.string.auth_refresh_short),
                    loading = state.isLoading,
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                )

                SecondaryAction(
                    text = "📞 Phone Login",
                    loading = false,
                    onClick = onSwitchToPhone,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QrStepRow(step: String, instruction: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = instruction,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun QrDisplayBox(state: AuthUiState, onRefresh: () -> Unit) {
    val hasQr = state.qrCodeBitmap != null
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(260.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (hasQr) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = if (isFocused) 3.dp else 1.5.dp,
                color = if (isFocused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onRefresh)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.qrCodeBitmap != null -> {
                Image(
                    bitmap = state.qrCodeBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.auth_qr_content_description),
                    modifier = Modifier.size(230.dp)
                )
            }
            state.isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.auth_qr_status_generating),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Click or press OK to generate QR code",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Enterprise security footer guaranteeing local-only TDLib execution.
 */
@Composable
private fun EnterpriseSecurityFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🛡️ 100% Local TDLib Engine · Android Keystore AES-256 · Zero Third-Party Relays",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ModernAuthTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.secondary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.secondary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun PrimaryAction(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ),
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SecondaryAction(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    OutlinedButton(
        modifier = modifier
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun screenTitle(step: AuthStep): String = when (step) {
    AuthStep.EnterApiKeys -> "Telegram API Setup"
    AuthStep.ScanQrCode -> "Scan QR Code"
    AuthStep.EnterPhone -> "Phone Sign-In"
    AuthStep.EnterCode -> "Enter Login Code"
    AuthStep.EnterPassword -> "Cloud Password (2FA)"
    AuthStep.Authorized -> "Authorized"
    AuthStep.Loading -> "Initializing TDLib..."
}

private fun stepBadgeText(step: AuthStep): String = when (step) {
    AuthStep.EnterApiKeys -> "STEP 1 · API CREDENTIALS"
    AuthStep.EnterPhone -> "PHONE NUMBER AUTH"
    AuthStep.ScanQrCode -> "INSTANT QR PAIRING"
    AuthStep.EnterCode -> "STEP 2 · VERIFICATION CODE"
    AuthStep.EnterPassword -> "STEP 3 · 2FA CLOUD SECURITY"
    AuthStep.Authorized -> "TDLIB AUTHORIZED"
    AuthStep.Loading -> "STARTING TDLIB ENGINE"
}

private fun Modifier.widthInMax(max: Dp): Modifier =
    then(Modifier.widthIn(max = max))
