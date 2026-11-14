package dev.mobwriter.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.BackgroundChoice
import dev.mobwriter.app.core.model.FontScale
import dev.mobwriter.app.core.model.Palette
import dev.mobwriter.app.core.model.PaletteChoice
import dev.mobwriter.app.data.BackgroundImageStore
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            PaletteControl(appearance.palette) { onChange(appearance.copy(palette = it)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            BackgroundControl(appearance.background) { onChange(appearance.copy(background = it)) }
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

@Composable
private fun PaletteControl(selected: PaletteChoice, onSelect: (PaletteChoice) -> Unit) {
    var customOpen by remember { mutableStateOf(false) }
    Text("配色", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Text(
        "当前 ${selected.label}",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Palette.entries.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { palette ->
                val choice = PaletteChoice.preset(palette)
                PaletteSwatch(
                    color = opaque(palette.accent),
                    label = palette.label,
                    chosen = selected.key == choice.key,
                    modifier = Modifier.weight(1f),
                ) { onSelect(choice) }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val custom = selected.customAccent
        PaletteSwatch(
            color = opaque(custom ?: 0x888888),
            label = "自定义",
            chosen = custom != null,
            modifier = Modifier.weight(1f),
        ) { customOpen = true }
        if (custom != null) {
            Text(
                "#%06X".format(custom),
                color = Muted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
    Text(
        "主色会用在按钮、选中项和强调文字上，纸色和文字不变。",
        color = Muted,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )
    if (customOpen) {
        CustomColorDialog(
            initial = selected.customAccent ?: 0xC96442,
            onDismiss = { customOpen = false },
            onPick = {
                onSelect(PaletteChoice.custom(it))
                customOpen = false
            },
        )
    }
}

@Composable
private fun PaletteSwatch(
    color: Color,
    label: String,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = chosen
                contentDescription = "配色 $label"
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (chosen) 2.dp else 0.dp,
                    color = if (chosen) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                    shape = CircleShape,
                ),
        )
        Text(
            label,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var red by remember { mutableStateOf((initial shr 16) and 0xFF) }
    var green by remember { mutableStateOf((initial shr 8) and 0xFF) }
    var blue by remember { mutableStateOf(initial and 0xFF) }
    val mixed = (red shl 16) or (green shl 8) or blue
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(mixed) }) { Text("使用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("自定义主色") },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(opaque(mixed)),
                )
                ColorChannel("红", red) { red = it }
                ColorChannel("绿", green) { green = it }
                ColorChannel("蓝", blue) { blue = it }
            }
        },
    )
}

@Composable
private fun ColorChannel(name: String, value: Int, onChange: (Int) -> Unit) {
    Text("$name $value", modifier = Modifier.padding(top = 8.dp))
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..255f,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "自定义主色 $name" },
    )
}

@Composable
private fun BackgroundControl(selected: BackgroundChoice, onSelect: (BackgroundChoice) -> Unit) {
    val context = LocalContext.current
    val store = remember { BackgroundImageStore(context) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = store.importFrom(uri)
        if (imported == null) {
            failed = true
        } else {
            failed = false
            onSelect(imported)
        }
    }
    Text("背景", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BackgroundOption(
            label = "默认",
            chosen = selected.isPlain,
            modifier = Modifier.weight(1f),
            onClick = {
                store.clear()
                failed = false
                onSelect(BackgroundChoice.plain())
            },
        ) {
            Box(Modifier.fillMaxSize().background(Paper))
        }
        BackgroundOption(
            label = "自定义图片",
            chosen = !selected.isPlain,
            modifier = Modifier.weight(1f),
            onClick = { picker.launch("image/*") },
        ) {
            val preview = remember(selected.key) {
                store.file(selected)?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
            }
            if (preview != null) {
                androidx.compose.foundation.Image(
                    preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = Muted)
                }
            }
        }
    }
    Text(
        if (failed) "这张图读不出来，换一张试试。" else "默认是现在的纸色。自定义图片只存在这台手机上，淡淡铺在页面底下。",
        color = if (failed) MaterialTheme.colorScheme.primary else Muted,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )
}

@Composable
private fun BackgroundOption(
    label: String,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = chosen
                contentDescription = "背景 $label"
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (chosen) 2.dp else 1.dp,
                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) { preview() }
        Text(
            label,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
