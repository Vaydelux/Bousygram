package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramReduceTransparency
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec

/**
 * One border, one hint. A floating Material label plus a TV-focus outline
 * was drawn across the field border on landscape phones and large text sizes.
 * Leave the outline to Material3 and keep the hint entirely inside the field.
 */
@Composable
internal fun ExtrasSearchInput(
    type: ExtrasType,
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val styleSpec = LocalBousygramStyleSpec.current
    val halo = LocalBousygramHaloColors.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val colors = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(
        if (styleSpec.isLiquidGlass) BousygramUiTokens.pillCorner else styleSpec.inputCorner
    )
    val glassAlpha = if (styleSpec.isLiquidGlass && !halo.isHighContrast && !reduceTransparency) {
        if (halo.isLight) 0.86f else 0.78f
    } else 0.96f
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChanged(it.take(120)) },
        shape = fieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            focusedContainerColor = colors.surfaceContainerHigh.copy(alpha = maxOf(glassAlpha, 0.90f)),
            unfocusedContainerColor = (if (halo.isLight) colors.surface else colors.surfaceContainer).copy(alpha = glassAlpha),
            cursorColor = colors.primary,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = if (styleSpec.isLiquidGlass && !halo.isHighContrast) {
                if (halo.isLight) Color(0xFF0F172A).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.16f)
            } else colors.outlineVariant
        ),
        leadingIcon = {
            BousygramIcon(
                icon = BousygramIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 18.dp
            )
        },
        placeholder = {
            Text(
                text = "Search " + type.label + " · " + type.provider,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.extrasFocusOutline()
                        .testTag("extras-search-clear")
                ) {
                    BousygramIcon(
                        icon = BousygramIcons.Close,
                        contentDescription = "Clear search",
                        size = 18.dp
                    )
                }
            }
        },
        modifier = modifier.testTag("extras-search-field")
    )
}
