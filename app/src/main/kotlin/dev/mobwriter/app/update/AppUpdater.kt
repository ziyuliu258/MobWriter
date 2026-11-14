package dev.mobwriter.app.update

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.mobwriter.app.core.github.OkHttpTransport
import dev.mobwriter.app.core.update.AppUpdate
import dev.mobwriter.app.core.update.ReleaseLookup
import dev.mobwriter.app.core.update.UpdateChoice
import java.io.File

data class InstalledVersion(val code: Int, val name: String)

/** Blocking network calls. Invoke them off the main thread. */
class AppUpdater(
    private val context: Context,
    private val lookup: ReleaseLookup = ReleaseLookup(OkHttpTransport()),
) {
    fun installedVersion(): InstalledVersion {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
        val code = info.longVersionCode.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return InstalledVersion(code, info.versionName.orEmpty())
    }

    fun releasesPage(owner: String, repo: String): String? =
        if (AppUpdate.isRepositorySlug(owner) && AppUpdate.isRepositorySlug(repo)) {
            AppUpdate.releasesPage(owner, repo)
        } else {
            null
        }

    fun check(owner: String, repo: String, versionCode: Int, versionName: String): UpdateChoice {
        val release = lookup.latest(owner, repo)
        return AppUpdate.choose(versionCode, versionName, release, lookup.manifest(release))
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun downloadTo(choice: UpdateChoice.Install, onProgress: (Float) -> Unit): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val safe = choice.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "update" }
        val file = File(directory, "MobWriter-$safe.apk")
        lookup.downloadTo(choice.downloadUrl, file, onProgress)
        return file
    }

    fun install(file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("MobWriter", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openInstallPermissionSettings(): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openPage(url: String): Boolean {
        if (!AppUpdate.isAllowedPageUrl(url)) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
