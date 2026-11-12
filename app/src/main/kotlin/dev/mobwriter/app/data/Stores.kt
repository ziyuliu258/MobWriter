package dev.mobwriter.app.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dev.mobwriter.app.core.model.AccountSession
import dev.mobwriter.app.core.model.BlogAccount
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

internal class AccountStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(openPrefs(context))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AccountSession {
        val raw = prefs.getString(ACCOUNTS, null)
        if (raw != null) {
            return runCatching { json.decodeFromString<AccountSession>(raw) }.getOrDefault(AccountSession())
        }
        val legacy = readLegacy()
        if (legacy.owner.isBlank() && legacy.repo.isBlank() && legacy.token.isBlank()) {
            return AccountSession()
        }
        val id = UUID.randomUUID().toString()
        val session = AccountSession(accounts = listOf(BlogAccount(id, legacy)), activeId = id)
        save(session)
        return session
    }

    fun save(session: AccountSession) {
        prefs.edit().putString(ACCOUNTS, json.encodeToString(session)).commit()
    }

    private fun readLegacy(): BlogSettings = BlogSettings(
        owner = prefs.getString("owner", "") ?: "",
        repo = prefs.getString("repo", "") ?: "",
        branch = prefs.getString("branch", "main") ?: "main",
        postsPath = prefs.getString("posts", "source/_posts") ?: "source/_posts",
        imageMode = if (prefs.getString("images", "post") == "source") ImageMode.SourceImages else ImageMode.PostFolder,
        autoBuild = prefs.getBoolean("auto", true),
        workflow = prefs.getString("workflow", "publish.yml") ?: "publish.yml",
        token = prefs.getString("token", "") ?: "",
    )

    companion object {
        private const val ACCOUNTS = "accounts"

        private fun openPrefs(context: Context): SharedPreferences = runCatching {
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
    }
}

class DraftStore(context: Context, accountId: String = "") {
    private val directory = File(context.filesDir, "drafts/${draftFolderName(accountId)}").apply { mkdirs() }
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

        /** 只删除这一个账号的草稿目录。 */
        fun deleteAccountFiles(context: Context, accountId: String) {
            val folder = draftFolderName(accountId)
            if (folder == SIGNED_OUT) return
            File(context.filesDir, "drafts/$folder").deleteRecursively()
        }

        /** 升级前的草稿直接放在 drafts 目录。迁到当时正在使用的账号下面，别的账号碰不到。 */
        fun migrateLooseDrafts(context: Context, accountId: String?) {
            val folder = accountId?.let(::draftFolderName) ?: return
            if (folder == SIGNED_OUT) return
            val root = File(context.filesDir, "drafts")
            if (!root.isDirectory) return
            val loose = root.listFiles { file -> file.isFile && file.extension == "json" }.orEmpty()
            if (loose.isEmpty()) return
            val destination = File(root, folder)
            destination.mkdirs()
            loose.forEach { file ->
                val target = File(destination, file.name)
                if (!target.exists() && !file.renameTo(target)) {
                    file.copyTo(target, overwrite = false)
                }
                if (target.exists()) file.delete()
            }
        }

        private fun draftFolderName(accountId: String): String {
            val cleaned = accountId.trim()
            if (cleaned.isEmpty() || cleaned.contains('/') || cleaned.contains('\\') || cleaned.contains("..")) {
                return SIGNED_OUT
            }
            return cleaned
        }

        private const val SIGNED_OUT = "_signed_out"
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
