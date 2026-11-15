package dev.mobwriter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Miuix 原装输入框，下方可带一行淡墨说明。 */
@Composable
internal fun InkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supporting: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = placeholder.ifEmpty { label },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            trailingIcon = trailing,
        )
        if (supporting.isNotEmpty()) {
            Text(
                supporting,
                color = Muted,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}

@Composable
internal fun InkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    description: String,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { next ->
                    onValueChange(next)
                    true
                }
            },
    )
}

/** 二选一、三选一用 Miuix 的分段标签。 */
@Composable
internal fun InkSegment(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRowWithContour(
        tabs = options,
        selectedTabIndex = selected,
        onTabSelected = onSelect,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 全应用统一的底部弹窗：Miuix 原装 [WindowBottomSheet]，内容超高时在弹窗里滚动，
 * 底部让出导航栏（Miuix 只让输入法，不让导航栏，内容会压到手势条下面）。
 */
@Composable
internal fun InkBottomSheet(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    allowDismiss: Boolean = true,
    content: @Composable () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    WindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = { if (allowDismiss) onDismiss() },
        allowDismiss = allowDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** 确认类的底部弹窗：标题、内容、主按钮，外加可选的次按钮和取消。 */
@Composable
internal fun InkSheet(
    onDismiss: () -> Unit,
    title: String,
    primary: String,
    onPrimary: () -> Unit,
    dismiss: String? = "取消",
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    show: Boolean = true,
    primaryEnabled: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    InkBottomSheet(show = show, title = title, onDismiss = onDismiss) {
        content()
        Spacer(Modifier.height(4.dp))
        TextButton(
            text = primary,
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
        if (secondary != null) {
            TextButton(text = secondary, onClick = onSecondary, modifier = Modifier.fillMaxWidth())
        }
        if (dismiss != null) {
            TextButton(text = dismiss, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
