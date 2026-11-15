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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
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
import dev.mobwriter.app.core.model.FontFace
import dev.mobwriter.app.core.model.FontScale
import dev.mobwriter.app.core.model.Palette
import dev.mobwriter.app.core.model.PaletteChoice
import dev.mobwriter.app.data.BackgroundImageStore
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearanceScreen(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    onClose: () -> Unit,
) {
    InkPage(title = "外观", onBack = onClose) {
        // 整页放进一个条目：内容不长，测试和无障碍滚动都能找到每一组。
        item {
            Column {
                InkGroup("字体") {
                    FontFaceControl("界面字体", appearance.uiFont) { onChange(appearance.copy(uiFont = it)) }
                    FontFaceControl("文章字体", appearance.articleFont) { onChange(appearance.copy(articleFont = it)) }
                }
                InkGroup("字号") {
                    FontScaleControl(
                        title = "界面字体大小",
                        scale = appearance.ui,
                        sample = "界面上的文字会按这个大小显示。",
                        sampleStyle = MiuixTheme.textStyles.body1.copy(color = Ink),
                        onSelect = { onChange(appearance.copy(ui = it)) },
                    )
                    FontScaleControl(
                        title = "文章字体大小",
                        scale = appearance.article,
                        sample = "文章里的句子会按这个大小显示。",
                        sampleStyle = articleBodyStyle(appearance.article, appearance.articleFont).copy(color = Ink),
                        onSelect = { onChange(appearance.copy(article = it)) },
                    )
                }
                InkGroup("配色") {
                    PaletteControl(appearance.palette) { onChange(appearance.copy(palette = it)) }
                }
                InkGroup("背景") {
                    BackgroundControl(appearance.background) { onChange(appearance.copy(background = it)) }
                }
            }
        }
    }
}

/** 一副字的二选一：宋体（衬线）或黑体（无衬线），立即生效。 */
@Composable
private fun FontFaceControl(title: String, face: FontFace, onSelect: (FontFace) -> Unit) {
    Text(title, style = MiuixTheme.textStyles.headline1, color = Ink)
    InkSegment(
        options = FontFace.entries.map { it.label },
        selected = FontFace.entries.indexOf(face),
        onSelect = { onSelect(FontFace.entries[it]) },
    )
}

@Composable
private fun FontScaleControl(
    title: String,
    scale: FontScale,
    sample: String,
    sampleStyle: TextStyle,
    onSelect: (FontScale) -> Unit,
) {
    Text(title, style = MiuixTheme.textStyles.headline1, color = Ink, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
    Text(scale.label, color = Cinnabar, style = MiuixTheme.textStyles.footnote1)
    InkSlider(
        value = scale.percent.toFloat(),
        onValueChange = { onSelect(FontScale.of(it.roundToInt())) },
        valueRange = FontScale.MIN.toFloat()..FontScale.MAX.toFloat(),
        description = "$title ${scale.label}",
    )
    Text(sample, style = sampleStyle, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
}

@Composable
private fun PaletteControl(selected: PaletteChoice, onSelect: (PaletteChoice) -> Unit) {
    var customOpen by remember { mutableStateOf(false) }
    Text(
        "当前 ${selected.label}" + (selected.customAccent?.let { "  #%06X".format(it) } ?: ""),
        color = Cinnabar,
        style = MiuixTheme.textStyles.footnote1,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    // 预设和「自定义」排在同一张四列网格里，每格等宽，行与行对齐
    val custom = selected.customAccent
    val cells: List<Pair<String, @Composable (Modifier) -> Unit>> = Palette.entries.map { palette ->
        val choice = PaletteChoice.preset(palette)
        palette.key to @Composable { modifier: Modifier ->
            PaletteSwatch(
                color = opaque(palette.accent),
                label = palette.label,
                chosen = selected.key == choice.key,
                modifier = modifier,
            ) { onSelect(choice) }
        }
    } + ("custom" to @Composable { modifier: Modifier ->
        PaletteSwatch(
            color = opaque(custom ?: 0x888888),
            label = "自定义",
            chosen = custom != null,
            modifier = modifier,
        ) { customOpen = true }
    })
    cells.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (_, cell) -> cell(Modifier.weight(1f)) }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Text(
        "按钮、选中项、页面底色都会换成这个颜色。",
        color = Muted,
        style = MiuixTheme.textStyles.footnote1,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
                    color = if (chosen) Ink else Color.Transparent,
                    shape = CircleShape,
                ),
        )
        Text(
            label,
            color = if (chosen) Cinnabar else Ink,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var color by remember { mutableStateOf(opaque(initial)) }
    InkSheet(
        onDismiss = onDismiss,
        title = "自定义主色",
        primary = "使用",
        onPrimary = { onPick(color.toArgb() and 0xFFFFFF) },
    ) {
        ColorPicker(color = color, onColorChanged = { color = it.copy(alpha = 1f) })
    }
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
        color = if (failed) Cinnabar else Muted,
        style = MiuixTheme.textStyles.footnote1,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
                    color = if (chosen) Cinnabar else MiuixTheme.colorScheme.dividerLine,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) { preview() }
        Text(
            label,
            color = if (chosen) Cinnabar else Ink,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
