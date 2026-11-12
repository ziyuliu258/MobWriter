package dev.mobwriter.app.data

import android.content.Context
import java.io.File

/** 按账号缓存已经加载成功的头像，切换时不会拿另一个博客的图来顶上。 */
internal class AvatarCache(private val root: File) {
    constructor(context: Context) : this(File(context.cacheDir, "avatars"))

    fun read(accountId: String): ByteArray? {
        val file = fileFor(accountId) ?: return null
        if (!file.isFile || file.length() == 0L) return null
        return runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun write(accountId: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val file = fileFor(accountId) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    fun delete(accountId: String) {
        fileFor(accountId)?.delete()
    }

    private fun fileFor(accountId: String): File? {
        val cleaned = accountId.trim()
        if (cleaned.isEmpty() || cleaned.contains('/') || cleaned.contains('\\') || cleaned.contains("..")) return null
        return File(root, cleaned)
    }
}
