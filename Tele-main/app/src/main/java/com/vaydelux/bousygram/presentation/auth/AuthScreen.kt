package com.vaydelux.bousygram.presentation.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.R
import com.vaydelux.bousygram.domain.model.AuthStep
import com.vaydelux.bousygram.domain.model.AuthUiState
import com.vaydelux.bousygram.presentation.auth.vm.AuthViewModel

private val ScreenBackground = Brush.linearGradient(
    listOf(Color(0xFF050814), Color(0xFF0A1528), Color(0xFF060B18))
)

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
                .background(ScreenBackground)
        ) {
            val compact = maxWidth < 900.dp || maxHeight < 520.dp
            val horizontalPadding = if (compact) 24.dp else 72.dp
            val verticalPadding = if (compact) 24.dp else 48.dp
            val showQr = state.step == AuthStep.ScanQrCode

            if (compact || !showQr) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BrandHeader(state, Modifier.fillMaxWidth().widthInMax(720.dp))
                    Spacer(Modifier.height(22.dp))
                    AuthCard(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth().widthInMax(720.dp)
                    )
                    if (showQr) {
                        Spacer(Modifier.height(18.dp))
                        QrPanel(
                            state = state,
                            onRefresh = viewModel::requestQrCodeLogin,
                            onPhone = viewModel::switchToPhoneLogin,
                            modifier = Modifier.fillMaxWidth().widthInMax(420.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        BrandHeader(state, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(28.dp))
                        AuthCard(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(44.dp))
                    QrPanel(
                        state = state,
                        onRefresh = viewModel::requestQrCodeLogin,
                        onPhone = viewModel::switchToPhoneLogin,
                        modifier = Modifier.width(360.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(state: AuthUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.connectionStatus ?: stringResource(R.string.auth_connection_default),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
private fun AuthCard(
    state: AuthUiState,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = screenTitle(state.step),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            AuthStepFields(state = state, viewModel = viewModel)
            if (!state.message.isNullOrBlank()) {
                Text(
                    text = state.message.orEmpty(),
                    color = if (state.message.contains("error", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.isLoading && state.step != AuthStep.ScanQrCode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.auth_working),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        AuthStep.EnterCode -> {
            val codeFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { codeFocus.requestFocus() } }
            AuthTextField(
                label = stringResource(R.string.auth_code_hint),
                placeholder = stringResource(R.string.auth_code_placeholder),
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.focusRequester(codeFocus)
            )
            PrimaryAction(stringResource(R.string.auth_submit), state.isLoading, viewModel::submitCode)
        }
        AuthStep.EnterPassword -> {
            val passwordFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { passwordFocus.requestFocus() } }
            AuthTextField(
                label = stringResource(R.string.auth_password_hint),
                placeholder = stringResource(R.string.auth_password_placeholder),
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                isPassword = true,
                modifier = Modifier.focusRequester(passwordFocus)
            )
            PrimaryAction(stringResource(R.string.auth_submit), state.isLoading, viewModel::submitPassword)
        }
        AuthStep.ScanQrCode -> {
            Text(
                text = stringResource(R.string.auth_qr_scan_instructions),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AuthStep.Loading -> Text(
            stringResource(R.string.auth_preparing_session),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AuthStep.Authorized -> Text(
            stringResource(R.string.auth_signed_in),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ApiKeysForm(state: AuthUiState, viewModel: AuthViewModel) {
    val apiIdFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { apiIdFocus.requestFocus() }
    }

    AuthTextField(
        label = stringResource(R.string.auth_api_id_label),
        placeholder = stringResource(R.string.auth_api_id_placeholder),
        value = state.apiId,
        onValueChange = viewModel::onApiIdChange,
        keyboardType = KeyboardType.Number,
        modifier = Modifier.focusRequester(apiIdFocus)
    )
    AuthTextField(
        label = stringResource(R.string.auth_api_hash_label),
        placeholder = stringResource(R.string.auth_api_hash_placeholder),
        value = state.apiHash,
        onValueChange = viewModel::onApiHashChange
    )
    PrimaryAction(stringResource(R.string.auth_submit_api), state.isLoading, viewModel::submitApiKeys)
}

@Composable
private fun PhoneForm(state: AuthUiState, viewModel: AuthViewModel) {
    val phoneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { phoneFocus.requestFocus() }
    }
    AuthTextField(
        label = stringResource(R.string.auth_phone_hint),
        placeholder = stringResource(R.string.auth_phone_placeholder),
        value = state.phone,
        onValueChange = viewModel::onPhoneChange,
        keyboardType = KeyboardType.Phone,
        modifier = Modifier.focusRequester(phoneFocus)
    )
    BoxWithConstraints {
        if (maxWidth < 440.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryAction(stringResource(R.string.auth_submit_phone), state.isLoading, viewModel::submitPhone)
                SecondaryAction(
                    stringResource(R.string.auth_phone_to_qr_button),
                    false,
                    viewModel::requestQrCodeLogin,
                    Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryAction(
                    stringResource(R.string.auth_submit_phone),
                    state.isLoading,
                    viewModel::submitPhone,
                    Modifier.weight(1f)
                )
                SecondaryAction(
                    stringResource(R.string.auth_phone_to_qr_button),
                    false,
                    viewModel::requestQrCodeLogin,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AuthTextField(
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
            .heightIn(min = 64.dp)
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
            focusedContainerColor = Color(0xFF0F172A),
            unfocusedContainerColor = Color(0xFF0F172A),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.secondary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun PrimaryAction(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryAction(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        modifier = modifier.height(52.dp),
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun QrPanel(
    state: AuthUiState,
    onRefresh: () -> Unit,
    onPhone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_qr_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            QrCodeBox(state, onRefresh)
            Text(
                text = qrStatusText(state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.auth_qr_refresh_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 360.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryAction(stringResource(R.string.auth_refresh_short), state.isLoading, onRefresh, Modifier.fillMaxWidth())
                        SecondaryAction(stringResource(R.string.auth_qr_fallback_button), false, onPhone, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryAction(
                            stringResource(R.string.auth_refresh_short),
                            state.isLoading,
                            onRefresh,
                            Modifier.weight(1f)
                        )
                        SecondaryAction(
                            stringResource(R.string.auth_qr_fallback_button),
                            false,
                            onPhone,
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCodeBox(state: AuthUiState, onRefresh: () -> Unit) {
    val hasRealQr = state.qrCodeBitmap != null
    Box(
        modifier = Modifier
            .size(if (hasRealQr) 280.dp else 220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hasRealQr) Color.White else Color(0xFF0F172A))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .clickable(onClick = onRefresh)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.qrCodeBitmap != null -> {
                Image(
                    bitmap = state.qrCodeBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.auth_qr_content_description),
                    modifier = Modifier.size(244.dp)
                )
            }
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(36.dp))
            else -> Text(
                text = stringResource(R.string.auth_qr_preparing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun qrStatusText(state: AuthUiState): String = when {
    state.qrCodeBitmap != null -> stringResource(R.string.auth_qr_status_ready)
    state.isLoading -> stringResource(R.string.auth_qr_status_generating)
    state.qrCodeLink.isNullOrBlank() -> stringResource(R.string.auth_qr_status_connecting)
    else -> stringResource(R.string.auth_qr_status_refreshing)
}

@Composable
private fun screenTitle(step: AuthStep): String = when (step) {
    AuthStep.EnterApiKeys -> stringResource(R.string.auth_api_title)
    AuthStep.ScanQrCode -> stringResource(R.string.auth_screen_scan)
    AuthStep.EnterPhone -> stringResource(R.string.auth_screen_phone)
    AuthStep.EnterCode -> stringResource(R.string.auth_screen_code)
    AuthStep.EnterPassword -> stringResource(R.string.auth_screen_password)
    AuthStep.Authorized -> stringResource(R.string.auth_screen_ready)
    AuthStep.Loading -> stringResource(R.string.auth_screen_starting)
}

private fun Modifier.widthInMax(max: androidx.compose.ui.unit.Dp): Modifier =
    then(Modifier.widthIn(max = max))
