package dev.mobwriter.app.data

import android.content.Context
import android.content.SharedPreferences
import dev.mobwriter.app.core.model.Appearance
import dev.mobwriter.app.core.model.FontScale

/** 外观存在单独的偏好里，账号仓库清掉旧键时碰不到它。 */
internal class AppearanceStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
    )

    fun load(): Appearance = Appearance(
        ui = FontScale.fromStored(prefs.getString(UI, null)),
        article = FontScale.fromStored(prefs.getString(ARTICLE, null)),
    )

    fun save(appearance: Appearance) {
        prefs.edit()
            .putString(UI, appearance.ui.percent.toString())
            .putString(ARTICLE, appearance.article.percent.toString())
            .commit()
    }

    companion object {
        const val FILE = "mobwriter.appearance"
        private const val UI = "ui"
        private const val ARTICLE = "article"
    }
}
