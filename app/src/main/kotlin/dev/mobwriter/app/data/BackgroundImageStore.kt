package dev.mobwriter.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.mobwriter.app.core.model.BackgroundChoice
import java.io.File

/**
 * 自定义背景图存在应用自己的目录里，只记一个文件名，不进仓库，也不进账号。
 * 解码前先看尺寸，超大的图按比例缩小，避免一张图撑爆内存。
 */
internal class BackgroundImageStore(private val context: Context) {
    private val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun file(choice: BackgroundChoice): File? {
        val name = choice.imageName ?: return null
        val candidate = File(directory, name)
        return candidate.takeIf { it.isFile && it.parentFile == directory }
    }

    /** 把选中的图存下来，返回新的背景；读不到或不是图片时返回 null，原来的背景不动。 */
    fun importFrom(uri: Uri): BackgroundChoice? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val name = "background-${System.currentTimeMillis()}.webp"
        val target = File(directory, name)
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
        bitmap.recycle()
        directory.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        return BackgroundChoice.image(name)
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_EDGE || height / sample > MAX_EDGE) sample *= 2
        return sample
    }

    companion object {
        const val DIRECTORY = "appearance"
        private const val MAX_EDGE = 2048
    }
}
