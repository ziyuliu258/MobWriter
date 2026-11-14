package dev.mobwriter.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.mobwriter.app.core.model.BackgroundChoice
import dev.mobwriter.app.data.BackgroundImageStore

/** 自定义背景铺在页面底下，淡一些，文字仍以纸色为主。没有图时什么都不画。 */
@Composable
internal fun AppearanceBackground(choice: BackgroundChoice, modifier: Modifier = Modifier) {
    if (choice.isPlain) return
    val context = LocalContext.current
    val bitmap = remember(choice.key) {
        BackgroundImageStore(context).file(choice)?.let { file ->
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize().alpha(0.18f),
    )
}

@Composable
internal fun WithAppearanceBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AppearanceBackground(LocalAppearance.current.background)
        content()
    }
}
