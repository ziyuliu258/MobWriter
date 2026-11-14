package dev.mobwriter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.FontStep

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
            FontStepControl(
                title = "界面字体大小",
                selected = appearance.ui,
                sample = "界面上的文字会按这个大小显示。",
                sampleStyle = MaterialTheme.typography.bodyLarge,
                onSelect = { onChange(appearance.copy(ui = it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            FontStepControl(
                title = "文章字体大小",
                selected = appearance.article,
                sample = "文章里的句子会按这个大小显示。",
                sampleStyle = articleBodyStyle(appearance.article).copy(color = MaterialTheme.colorScheme.onBackground),
                onSelect = { onChange(appearance.copy(article = it)) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FontStepControl(
    title: String,
    selected: FontStep,
    sample: String,
    sampleStyle: TextStyle,
    onSelect: (FontStep) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FontStep.entries.forEach { step ->
            val chosen = step == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (chosen) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(step) }
                    .semantics {
                        this.selected = chosen
                        contentDescription = "$title ${step.label}"
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    step.label,
                    color = if (chosen) Cinnabar else Ink,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
    Text(sample, style = sampleStyle, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
}
