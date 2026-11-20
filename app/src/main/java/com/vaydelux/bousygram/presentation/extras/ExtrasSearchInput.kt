package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
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
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChanged(it.take(120)) },
        shape = RoundedCornerShape(LocalBousygramStyleSpec.current.inputCorner),
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
        keyboardActions = KeyboardActions(onSearch = onSearch),
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
