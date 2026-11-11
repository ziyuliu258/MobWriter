package dev.mobwriter.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppIconButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    icon: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        if (tint == Color.Unspecified) {
            IconButton(onClick = onClick, enabled = enabled) { icon() }
        } else {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = tint,
                    disabledContentColor = tint.copy(alpha = 0.38f),
                ),
            ) { icon() }
        }
    }
}
