package dev.mobwriter.app.update

import android.content.Context

/** 更新检查的间隔、已忽略的版本，以及装上新版本后要展示的说明。 */
internal class UpdateMemory(context: Context) {
    private val prefs = context.getSharedPreferences("mobwriter.update", Context.MODE_PRIVATE)

    fun shouldAutoCheck(now: Long = System.currentTimeMillis()): Boolean =
        now - prefs.getLong(CHECKED_AT, 0L) >= INTERVAL_MS

    fun markChecked(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(CHECKED_AT, now).apply()
    }

    fun dismissedVersion(): String = prefs.getString(DISMISSED, "").orEmpty()

    fun dismiss(versionName: String) {
        prefs.edit().putString(DISMISSED, versionName).apply()
    }

    fun rememberNotes(versionName: String, notes: String) {
        prefs.edit().putString(notesKey(versionName), notes.take(4000)).apply()
    }

    fun notesFor(versionName: String): String = prefs.getString(notesKey(versionName), "").orEmpty()

    fun lastSeenVersion(): String = prefs.getString(SEEN, "").orEmpty()

    fun markSeen(versionName: String) {
        prefs.edit().putString(SEEN, versionName).apply()
    }

    private fun notesKey(versionName: String) = "notes:$versionName"

    private companion object {
        const val CHECKED_AT = "checkedAt"
        const val DISMISSED = "dismissed"
        const val SEEN = "seen"
        const val INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
