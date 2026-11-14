package dev.mobwriter.app.ui

import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.mobwriter.app.BuildConfig
import dev.mobwriter.app.core.update.AppUpdate
import dev.mobwriter.app.core.update.DirectInstallStep
import dev.mobwriter.app.core.update.UpdateChoice
import dev.mobwriter.app.core.update.UpdateException
import dev.mobwriter.app.update.AppUpdater
import dev.mobwriter.app.update.InstalledVersion
import dev.mobwriter.app.update.UpdateMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal val LocalUpdateCheck = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun UpdateHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { AppUpdater(context) }
    val memory = remember { UpdateMemory(context) }
    val installed = remember {
        runCatching { updater.installedVersion() }.getOrElse {
            InstalledVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        }
    }
    val owner = BuildConfig.UPDATE_OWNER
    val repo = BuildConfig.UPDATE_REPO
    var offer by remember { mutableStateOf<UpdateChoice.Install?>(null) }
    var changelog by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun configured(): Boolean = AppUpdate.isRepositorySlug(owner) && AppUpdate.isRepositorySlug(repo)

    fun checkNow() {
        if (!configured()) {
            message = "未配置更新源"
            return
        }
        scope.launch {
            val choice = try {
                withContext(Dispatchers.IO) { updater.check(owner, repo, installed.code, installed.name) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message = (error as? UpdateException)?.message ?: "检查失败"
                return@launch
            }
            when (choice) {
                is UpdateChoice.Install -> {
                    memory.rememberNotes(choice.versionName, choice.notes)
                    offer = choice
                }
                is UpdateChoice.UpToDate -> message = "已是最新"
                is UpdateChoice.OpenPage -> message = "这一版需要到发布页安装"
            }
        }
    }

    LaunchedEffect(Unit) {
        val seen = memory.lastSeenVersion()
        if (seen.isEmpty()) {
            memory.markSeen(installed.name)
        } else if (seen != installed.name) {
            changelog = memory.notesFor(installed.name).ifBlank { "这一版已经装好。" }
        }
        if (!memory.shouldAutoCheck() || !configured()) return@LaunchedEffect
        memory.markChecked()
        val choice = runCatching {
            withContext(Dispatchers.IO) { updater.check(owner, repo, installed.code, installed.name) }
        }.getOrNull()
        if (choice is UpdateChoice.Install && choice.versionName != memory.dismissedVersion() && changelog == null) {
            memory.rememberNotes(choice.versionName, choice.notes)
            offer = choice
        }
    }

    CompositionLocalProvider(LocalUpdateCheck provides ::checkNow) {
        content()
    }
    changelog?.let { notes ->
        NotesSheet(
            title = "墨哺 ${installed.name}".trim(),
            body = notes,
            confirm = "知道了",
            onConfirm = {
                memory.markSeen(installed.name)
                changelog = null
            },
        )
    }
    offer?.let { choice ->
        UpdateOfferSheet(
            updater = updater,
            choice = choice,
            onDismiss = {
                memory.dismiss(choice.versionName)
                offer = null
            },
        )
    }
    message?.let { text ->
        NotesSheet(title = "检查更新", body = text, confirm = "知道了", onConfirm = { message = null })
    }
}

@Composable
private fun UpdateOfferSheet(
    updater: AppUpdater,
    choice: UpdateChoice.Install,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var askedPermission by remember { mutableStateOf(false) }
    var waitingPermission by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, file, waitingPermission) {
        if (!waitingPermission) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val ready = file ?: return@repeatOnLifecycle
            if (updater.canInstallPackages()) {
                waitingPermission = false
                updater.install(ready)
            }
        }
    }
    InkBottomSheet(
        show = true,
        title = "发现新版本 ${choice.versionName}",
        onDismiss = onDismiss,
        allowDismiss = !downloading,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text("来源：GitHub", color = Muted)
            if (choice.notes.isNotBlank()) {
                Text(choice.notes.trim(), modifier = Modifier.padding(top = 12.dp))
            }
            if (downloading) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
            error?.let { Text(it, color = Cinnabar, modifier = Modifier.padding(top = 8.dp)) }
            val ready = file
            TextButton(
                text = if (ready != null) "安装" else if (downloading) "正在下载" else "立即更新",
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    when (AppUpdate.directInstallStep(updater.canInstallPackages(), askedPermission)) {
                        DirectInstallStep.AskPermission -> {
                            askedPermission = true
                            waitingPermission = true
                            updater.openInstallPermissionSettings()
                        }
                        DirectInstallStep.OpenPage -> updater.openPage(choice.pageUrl)
                        DirectInstallStep.Download -> {
                            if (ready != null) {
                                updater.install(ready)
                                return@TextButton
                            }
                            downloading = true
                            error = null
                            scope.launch {
                                val saved = try {
                                    withContext(Dispatchers.IO) { updater.downloadTo(choice) { progress = it } }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = (failure as? UpdateException)?.message ?: "下载失败"
                                    null
                                }
                                downloading = false
                                if (saved != null) {
                                    file = saved
                                    if (updater.canInstallPackages()) updater.install(saved)
                                    else {
                                        waitingPermission = true
                                        updater.openInstallPermissionSettings()
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            TextButton(
                text = "打开发布页",
                onClick = { updater.openPage(choice.pageUrl) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            TextButton(
                text = "稍后",
                onClick = onDismiss,
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NotesSheet(title: String, body: String, confirm: String, onConfirm: () -> Unit) {
    InkBottomSheet(show = true, title = title, onDismiss = onConfirm) {
        Column(Modifier.fillMaxWidth()) {
            Text(body)
            TextButton(
                text = confirm,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
