package com.vaydelux.bousygram.presentation.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.R
import com.vaydelux.bousygram.domain.model.AuthMode
import com.vaydelux.bousygram.domain.model.AuthStep
import com.vaydelux.bousygram.domain.model.AuthUiState
import com.vaydelux.bousygram.presentation.auth.vm.AuthViewModel
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.components.BousygramDynamicIsland
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIconRef
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramInlineStatus
import com.vaydelux.bousygram.presentation.components.BousygramStatusTone
import com.vaydelux.bousygram.presentation.components.DynamicIslandActivityState
import com.vaydelux.bousygram.presentation.components.DynamicIslandTone
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import org.drinkless.tdlib.Client

/**
 * Telegram sign-in flow for Android TV (D-pad dual pane), tablets, and phones.
 * Runs exclusively through direct TDLib client-side MTProto with no third-party relays.
 */
@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    PremiumGridBackdrop(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
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
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        QrPairingStation(
                            state = state,
                            onRefresh = viewModel::requestQrCodeLogin,
                            onSwitchToPhone = viewModel::switchToPhoneLogin,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
 * Brand header with a calm static TDLib connection indicator.
 */
@Composable
private fun EnterpriseBrandHeader(
    state: AuthUiState,
    modifier: Modifier = Modifier
) {
    val isReady = state.connectionStatus?.contains("Connected", ignoreCase = true) == true
    val statusText = state.connectionStatus ?: stringResource(R.string.auth_connection_default)
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BousygramBrandMark(size = 46.dp, squircleFrame = true)

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Telegram Media",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        BousygramDynamicIsland(
            compact = true,
            state = DynamicIslandActivityState(
                title = statusText,
                badge = if (isReady) "Encrypted" else "TDLib",
                tone = if (isReady) DynamicIslandTone.ONLINE else DynamicIslandTone.SYNCING,
                isBusy = !isReady,
                liveHeadline = "Telegram Sign-In",
                liveDetail = "Direct client-side MTProto connection. No third-party relay servers.",
                statusTag = if (isReady) "TDLib Connected" else "Connecting"
            )
        )
    }
}

/**
 * Dual-mode authentication selector (QR Code vs Phone Number).
 */
@Composable
private fun AuthModeSelector(
    currentMode: AuthMode,
    onSelectMode: (AuthMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val halo = LocalBousygramHaloColors.current
    val outerShape = RoundedCornerShape(BousygramUiTokens.cardCorner)
    Surface(
        modifier = modifier
            .clip(outerShape)
            .then(
                if (halo.isHighContrast) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, outerShape)
                else Modifier
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeTabItem(
                label = "QR code",
                icon = BousygramIcons.QrCode,
                selected = currentMode == AuthMode.QR,
                onClick = { onSelectMode(AuthMode.QR) },
                modifier = Modifier.weight(1f)
            )
            ModeTabItem(
                label = "Phone number",
                icon = BousygramIcons.Phone,
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
    icon: BousygramIconRef,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(BousygramUiTokens.tabCorner)
    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            isFocused -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        label = "tabBg"
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else if (isFocused) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .then(
                if (isFocused) Modifier.border(
                    width = BousygramUiTokens.tvFocusOutline,
                    color = MaterialTheme.colorScheme.primary,
                    shape = shape
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BousygramIcon(
                icon = icon,
                contentDescription = null,
                tint = if (isFocused && !selected) MaterialTheme.colorScheme.primary else contentColor,
                size = 17.dp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    EnterprisePanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stepBadgeText(state.step),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                            color = MaterialTheme.colorScheme.primary
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
                BousygramInlineStatus(
                    message = state.message.orEmpty(),
                    tone = if (isError) BousygramStatusTone.ERROR else BousygramStatusTone.NEUTRAL
                )
            }
        }
    }
}

@Composable
private fun AuthStepFields(state: AuthUiState, viewModel: AuthViewModel) {
    when (state.step) {
        AuthStep.Unavailable -> {
            Text(
                text = state.message ?: "Native TDLib is unavailable for this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            PrimaryAction(text = "Retry connection", loading = state.isLoading,
                onClick = viewModel::retryInitialization)
        }
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
    val isNative = Client.isNativeAvailable()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Enter your mobile phone number with international country code (e.g. +15551234567) to receive a secure Telegram verification code.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        ModernAuthTextField(
            label = stringResource(R.string.auth_phone_hint),
            placeholder = "+15551234567",
            value = state.phone,
            onValueChange = viewModel::onPhoneChange,
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.focusRequester(phoneFocus)
        )

        if (!isNative) {
            OutlinedButton(
                onClick = { viewModel.onPhoneChange("+9996621111") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Test phone controls are unavailable without native TDLib",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

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
    val isNative = Client.isNativeAvailable()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val targetPhone = if (state.phone.isNotBlank()) " to ${state.phone}" else ""
        Text(
            text = "A 5-digit verification code was sent$targetPhone in your official Telegram app. Enter it below:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        ModernAuthTextField(
            label = stringResource(R.string.auth_code_hint),
            placeholder = "5-digit code",
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.focusRequester(codeFocus)
        )

        if (!isNative) {
            OutlinedButton(
                onClick = { viewModel.onCodeChange("22222") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Test code controls are unavailable without native TDLib",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        PrimaryAction(
            text = stringResource(R.string.auth_submit),
            loading = state.isLoading,
            onClick = viewModel::submitCode
        )

        SecondaryAction(
            text = "Back / Change Phone Number",
            loading = state.isLoading,
            onClick = viewModel::switchToPhoneLogin,
            leadingIcon = BousygramIcons.Back,
            modifier = Modifier.fillMaxWidth()
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
 * Dedicated QR pairing panel for linking with the official Telegram app.
 */
@Composable
private fun QrPairingStation(
    state: AuthUiState,
    onRefresh: () -> Unit,
    onSwitchToPhone: () -> Unit,
    modifier: Modifier = Modifier
) {
    EnterprisePanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BousygramIcon(
                    icon = BousygramIcons.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 22.dp
                )
                Text(
                    text = stringResource(R.string.auth_qr_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Link your account with an official Telegram QR code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            QrDisplayBox(
                state = state,
                onRefresh = onRefresh,
            )

            // Step-by-step pairing guide for real native MTProto session
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BousygramUiTokens.cardCorner))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QrStepRow(step = "1", instruction = "Open Telegram on your mobile phone")
                QrStepRow(step = "2", instruction = "Go to Settings → Devices → Link Desktop Device")
                QrStepRow(step = "3", instruction = "Point your camera at this QR code")
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 310.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryAction(
                            text = stringResource(R.string.auth_refresh_short),
                            loading = state.isLoading,
                            onClick = onRefresh,
                            leadingIcon = BousygramIcons.Refresh,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SecondaryAction(
                            text = "Phone Login",
                            loading = false,
                            onClick = onSwitchToPhone,
                            leadingIcon = BousygramIcons.Phone,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryAction(
                            text = stringResource(R.string.auth_refresh_short),
                            loading = state.isLoading,
                            onClick = onRefresh,
                            leadingIcon = BousygramIcons.Refresh,
                            modifier = Modifier.weight(1f)
                        )

                        SecondaryAction(
                            text = "Phone Login",
                            loading = false,
                            onClick = onSwitchToPhone,
                            leadingIcon = BousygramIcons.Phone,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
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
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
private fun QrDisplayBox(
    state: AuthUiState,
    onRefresh: () -> Unit,
) {
    val hasQr = state.qrCodeBitmap != null
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(260.dp)
            .clip(RoundedCornerShape(BousygramUiTokens.cardCorner))
            .background(if (hasQr) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = if (isFocused) BousygramUiTokens.tvFocusOutline else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(BousygramUiTokens.cardCorner)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onRefresh),
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
                    BousygramIcon(
                        icon = BousygramIcons.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 36.dp
                    )
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
 * Factual privacy footer confirming direct local TDLib execution.
 */
@Composable
private fun EnterpriseSecurityFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BousygramIcon(
            icon = BousygramIcons.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 14.dp
        )
        Text(
            text = "Direct local TDLib connection · Credentials encrypted on-device",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
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
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(BousygramUiTokens.cardCorner)
    )
}

@Composable
private fun PrimaryAction(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: BousygramIconRef? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val halo = LocalBousygramHaloColors.current
    val shape = RoundedCornerShape(BousygramUiTokens.cardCorner)

    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) halo.focusRing else Color.Transparent,
                shape = shape
            ),
        onClick = onClick,
        enabled = !loading,
        shape = shape,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (leadingIcon != null) {
                    BousygramIcon(
                        icon = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        size = 18.dp
                    )
                }
                Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SecondaryAction(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: BousygramIconRef? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val halo = LocalBousygramHaloColors.current
    val shape = RoundedCornerShape(BousygramUiTokens.cardCorner)
    val contentColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    OutlinedButton(
        modifier = modifier
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) Modifier.border(
                    width = BousygramUiTokens.tvFocusOutline,
                    color = MaterialTheme.colorScheme.primary,
                    shape = shape
                ) else if (halo.isHighContrast) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = shape
                ) else Modifier
            ),
        onClick = onClick,
        enabled = !loading,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) {
                BousygramIcon(
                    icon = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    size = 17.dp
                )
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun screenTitle(step: AuthStep): String = when (step) {
    AuthStep.Unavailable -> "TDLib Unavailable"
    AuthStep.EnterApiKeys -> "Telegram API Setup"
    AuthStep.ScanQrCode -> "Scan QR Code"
    AuthStep.EnterPhone -> "Phone Sign-In"
    AuthStep.EnterCode -> "Enter Login Code"
    AuthStep.EnterPassword -> "Cloud Password (2FA)"
    AuthStep.Authorized -> "Authorized"
    AuthStep.Loading -> "Initializing TDLib..."
}

private fun stepBadgeText(step: AuthStep): String = when (step) {
    AuthStep.Unavailable -> "Native runtime required"
    AuthStep.EnterApiKeys -> "Step 1 · API credentials"
    AuthStep.EnterPhone -> "Phone sign-in"
    AuthStep.ScanQrCode -> "QR sign-in"
    AuthStep.EnterCode -> "Step 2 · Verification code"
    AuthStep.EnterPassword -> "Step 3 · Cloud password"
    AuthStep.Authorized -> "Signed in"
    AuthStep.Loading -> "Starting TDLib"
}

private fun Modifier.widthInMax(max: Dp): Modifier =
    then(Modifier.widthIn(max = max))
