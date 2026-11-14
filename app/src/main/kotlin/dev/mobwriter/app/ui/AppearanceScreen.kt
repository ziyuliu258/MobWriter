package dev.mobwriter.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.FontScale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceScreen(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("外观") },
                navigationIcon = {
                    AppIconButton("返回", onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            FontScaleControl(
                title = "界面字体大小",
                scale = appearance.ui,
                sample = "界面上的文字会按这个大小显示。",
                sampleStyle = MaterialTheme.typography.bodyLarge,
                onSelect = { onChange(appearance.copy(ui = it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            FontScaleControl(
                title = "文章字体大小",
                scale = appearance.article,
                sample = "文章里的句子会按这个大小显示。",
                sampleStyle = articleBodyStyle(appearance.article).copy(color = MaterialTheme.colorScheme.onBackground),
                onSelect = { onChange(appearance.copy(article = it)) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FontScaleControl(
    title: String,
    scale: FontScale,
    sample: String,
    sampleStyle: TextStyle,
    onSelect: (FontScale) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Text(scale.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = scale.percent.toFloat(),
        onValueChange = { onSelect(FontScale.of(it.roundToInt())) },
        valueRange = FontScale.MIN.toFloat()..FontScale.MAX.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$title ${scale.label}" },
    )
    Text(sample, style = sampleStyle, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
}
