package dev.mobwriter.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.robolectric.Shadows

/**
 * `createComposeRule()` 会启动 [ComponentActivity]。
 * 这个 Activity 只由 `androidx.compose.ui:ui-test-manifest` 写进 debug 清单，
 * release 单测读的是 release 清单，Robolectric 因此拒绝启动它。
 * 在测试进程里补上同一个宿主，不改 release 安装包。
 */
class ComposeHostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val component = ComponentName(packageName, ComponentActivity::class.java.name)
        val info = Shadows.shadowOf(packageManager).addActivityIfNotPresent(component)
        info.theme = android.R.style.Theme_Material_Light_NoActionBar
        info.exported = true
    }
}
