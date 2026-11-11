package dev.mobwriter.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dev.mobwriter.app.core.model.BlogSettings
import dev.mobwriter.app.core.model.ImageMode
import dev.mobwriter.app.core.model.PostDocument
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.UUID

class SettingsStore(context: Context) {
    private val prefs = runCatching {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "mobwriter.settings",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("mobwriter.settings.plain", Context.MODE_PRIVATE)
    }

    fun load(): BlogSettings = BlogSettings(
        owner = prefs.getString("owner", "") ?: "",
        repo = prefs.getString("repo", "") ?: "",
        branch = prefs.getString("branch", "main") ?: "main",
        postsPath = prefs.getString("posts", "source/_posts") ?: "source/_posts",
        imageMode = if (prefs.getString("images", "post") == "source") ImageMode.SourceImages else ImageMode.PostFolder,
        autoBuild = prefs.getBoolean("auto", true),
        workflow = prefs.getString("workflow", "publish.yml") ?: "publish.yml",
        token = prefs.getString("token", "") ?: "",
    )

    fun save(settings: BlogSettings) {
        prefs.edit()
            .putString("owner", settings.owner.trim())
            .putString("repo", settings.repo.trim())
            .putString("branch", settings.branch.trim())
            .putString("posts", settings.postsPath.trim())
            .putString("images", if (settings.imageMode == ImageMode.SourceImages) "source" else "post")
            .putBoolean("auto", settings.autoBuild)
            .putString("workflow", settings.workflow.trim().ifBlank { "publish.yml" })
            .putString("token", settings.token.trim())
            .apply()
    }
}

class DraftStore(context: Context) {
    private val directory = File(context.filesDir, "drafts").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun save(key: String, document: PostDocument) {
        val stored = StoredDraft(System.currentTimeMillis(), document, key)
        File(directory, "$key.json").writeText(json.encodeToString(stored))
    }

    fun load(key: String): StoredDraft? {
        val file = File(directory, "$key.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<StoredDraft>(file.readText()) }.getOrNull()
    }

    fun delete(key: String) {
        File(directory, "$key.json").delete()
    }

    fun list(): List<StoredDraft> =
        directory.listFiles { file -> file.extension == "json" }.orEmpty()
            .mapNotNull { file ->
                runCatching { json.decodeFromString<StoredDraft>(file.readText()) }.getOrNull()
            }
            .sortedByDescending { it.savedAt }

    companion object {
        fun keyForRemote(path: String): String =
            "p-" + Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray())

        fun newKey(): String = "n-" + UUID.randomUUID()
    }
}

@Serializable
data class StoredDraft(val savedAt: Long, val document: PostDocument, val key: String = "")

object ImageIntake {
    fun save(context: Context, uri: Uri): String? {
        val directory = File(context.filesDir, "images").apply { mkdirs() }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            val file = File(directory, "img-$stamp.bin")
            file.writeBytes(bytes)
            return file.absolutePath
        }
        val rotated = rotate(bitmap, bytes)
        val maxSide = maxOf(rotated.width, rotated.height)
        val scale = minOf(1f, 1920f / maxSide.toFloat())
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * scale).toInt().coerceAtLeast(1),
                (rotated.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            rotated
        }
        val file = File(directory, "img-$stamp.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        if (scaled !== rotated) scaled.recycle()
        if (rotated !== bitmap) rotated.recycle()
        bitmap.recycle()
        return file.absolutePath
    }

    private fun rotate(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
