package dev.mobwriter.app.ui

import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mobwriter.app.BuildConfig
import dev.mobwriter.app.core.update.AppUpdate
import dev.mobwriter.app.core.update.DirectInstallStep
import dev.mobwriter.app.core.update.UpdateChoice
import dev.mobwriter.app.core.update.UpdateException
import dev.mobwriter.app.update.AppUpdater
import dev.mobwriter.app.update.InstalledVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UpdateSettingRow() {
    val context = LocalContext.current
    val updater = remember { AppUpdater(context) }
    val owner = BuildConfig.UPDATE_OWNER
    val repo = BuildConfig.UPDATE_REPO
    val installed = remember {
        runCatching { updater.installedVersion() }.getOrElse {
            InstalledVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        }
    }
    var status by remember { mutableStateOf("当前 ${installed.name.ifBlank { installed.code.toString() }}") }
    var pageUrl by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var askedPermission by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun openKnownPage(url: String): Boolean {
        val target = url.takeIf { AppUpdate.isAllowedPageUrl(it) } ?: updater.releasesPage(owner, repo) ?: return false
        pageUrl = target
        return updater.openPage(target)
    }

    fun check() {
        if (busy) return
        if (!AppUpdate.isRepositorySlug(owner) || !AppUpdate.isRepositorySlug(repo)) {
            status = "未配置更新源"
            return
        }
        busy = true
        status = "正在检查"
        scope.launch {
            try {
                val choice = try {
                    withContext(Dispatchers.IO) {
                        updater.check(owner, repo, installed.code, installed.name)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    status = (error as? UpdateException)?.message ?: "检查失败"
                    updater.releasesPage(owner, repo)?.let { fallback ->
                        pageUrl = fallback
                        updater.openPage(fallback)
                        status = "请在浏览器安装"
                    }
                    return@launch
                }
                if (choice.pageUrl.isNotBlank()) pageUrl = choice.pageUrl
                when (choice) {
                    is UpdateChoice.UpToDate -> status = "已是最新"
                    is UpdateChoice.OpenPage -> {
                        status = "请在浏览器安装"
                        openKnownPage(choice.pageUrl)
                    }
                    is UpdateChoice.Install -> {
                        status = "可更新 ${choice.versionName}"
                        when (AppUpdate.directInstallStep(updater.canInstallPackages(), askedPermission)) {
                            DirectInstallStep.AskPermission -> {
                                askedPermission = true
                                status = "请允许安装"
                                if (!updater.openInstallPermissionSettings()) {
                                    status = "请在浏览器安装"
                                    openKnownPage(choice.pageUrl)
                                }
                            }
                            DirectInstallStep.OpenPage -> {
                                status = "请在浏览器安装"
                                openKnownPage(choice.pageUrl)
                            }
                            DirectInstallStep.Download -> {
                                status = "正在下载"
                                val installedPackage = try {
                                    val file = withContext(Dispatchers.IO) { updater.downloadTo(choice) {} }
                                    updater.install(file)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    false
                                }
                                if (installedPackage) {
                                    status = "请确认安装"
                                } else {
                                    status = "请在浏览器安装"
                                    openKnownPage(choice.pageUrl)
                                }
                            }
                        }
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    val checkForUpdate = LocalUpdateCheck.current
    BasicComponent(
        title = "检查更新",
        summary = status.ifBlank { null },
        enabled = !busy,
        onClick = { checkForUpdate?.invoke() ?: check() },
        endActions = {
            if (busy) InfiniteProgressIndicator(color = Cinnabar)
            pageUrl?.let { link ->
                AppIconButton("打开发布页", onClick = { openKnownPage(link) }, tint = Muted) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "打开发布页")
                }
            }
        },
    )
}
