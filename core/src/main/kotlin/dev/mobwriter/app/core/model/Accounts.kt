package dev.mobwriter.app.core.model

import kotlinx.serialization.Serializable

/** 一个账号是一整套连接：令牌、仓库和路径。草稿按 id 分开存放。 */
@Serializable
data class BlogAccount(
    val id: String,
    val settings: BlogSettings,
)

@Serializable
data class AccountSession(
    val accounts: List<BlogAccount> = emptyList(),
    val activeId: String? = null,
) {
    val active: BlogAccount? get() = accounts.firstOrNull { it.id == activeId }

    fun add(settings: BlogSettings, id: String): AccountSession =
        copy(accounts = accounts + BlogAccount(id, settings), activeId = id)

    /** 修改当前连接。没有当前账号时，按新账号保存，不覆盖列表里的其他账号。 */
    fun replaceActive(settings: BlogSettings, idForNew: String): AccountSession {
        val current = active ?: return add(settings, idForNew)
        return copy(
            accounts = accounts.map { account ->
                if (account.id == current.id) account.copy(settings = settings) else account
            },
        )
    }

    fun switchTo(id: String): AccountSession =
        if (accounts.none { it.id == id }) this else copy(activeId = id)

    /** 只取消当前登录。已保存的令牌和草稿都还在。 */
    fun signOut(): AccountSession = if (activeId == null) this else copy(activeId = null)

    /** 只删除这一个账号。其他账号的令牌原样保留。 */
    fun remove(id: String): AccountSession = copy(
        accounts = accounts.filterNot { it.id == id },
        activeId = if (activeId == id) null else activeId,
    )
}

fun BlogSettings.connectionLabel(): String {
    val who = owner.trim()
    val name = repo.trim()
    return listOf(who, name).filter { it.isNotEmpty() }.joinToString("/").ifBlank { "未命名账号" }
}
