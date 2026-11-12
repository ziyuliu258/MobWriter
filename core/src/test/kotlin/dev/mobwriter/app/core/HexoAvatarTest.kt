package dev.mobwriter.app.core

import dev.mobwriter.app.core.blog.HexoAvatar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HexoAvatarTest {
    @Test
    fun themeAvatarUsesAvatarImgThenRootAndSiteUrl() {
        val site = HexoAvatar.parseSite(
            """
            title: Example
            url: https://example.com/blog
            root: /blog/
            theme: liquid
            source_dir: source
            """.trimIndent(),
        )
        val theme = """
            avatar:
              img: /img/liquid-icon.svg
              effect: false
            aside:
              card_newest_comments:
                avatar: true
            valine:
              avatar: monsterid
        """.trimIndent()
        val override = """
            avatar:
              img: /images/avatar.jpg # 站点头像
              effect: false
        """.trimIndent()
        val avatar = HexoAvatar.chooseAvatar(override, theme)
        assertEquals("/images/avatar.jpg", avatar)
        val resolved = HexoAvatar.resolve(site, avatar!!)
        assertEquals("https://example.com/blog/images/avatar.jpg", resolved?.publicUrl)
        assertEquals("source/images/avatar.jpg", resolved?.sourcePath)
        assertEquals("themes/liquid/source/images/avatar.jpg", resolved?.themeSourcePath)
    }

    @Test
    fun urlWithoutTheRootPathStillPublishesUnderRoot() {
        val site = HexoAvatar.parseSite(
            """
            url: https://example.com
            root: /blog
            theme: "liquid"
            """.trimIndent(),
        )
        val resolved = HexoAvatar.resolve(site, "/images/avatar.jpg")
        assertEquals("https://example.com/blog/images/avatar.jpg", resolved?.publicUrl)
        assertEquals("source/images/avatar.jpg", resolved?.sourcePath)
    }

    @Test
    fun scalarAvatarAndAbsoluteUrl() {
        val site = HexoAvatar.parseSite("theme: liquid\nurl: https://example.com\nroot: /\n")
        assertEquals("/images/avatar.jpg", HexoAvatar.parseAvatar("avatar: '/images/avatar.jpg'\n"))
        val local = HexoAvatar.resolve(site, "/images/avatar.jpg")
        assertEquals("https://example.com/images/avatar.jpg", local?.publicUrl)
        assertEquals("source/images/avatar.jpg", local?.sourcePath)
        val remote = HexoAvatar.resolve(site, "https://cdn.example/images/avatar.jpg")
        assertEquals("https://cdn.example/images/avatar.jpg", remote?.publicUrl)
        assertNull(remote?.sourcePath)
    }

    @Test
    fun missingOrNestedAvatarIsNotAPicture() {
        val yaml = """
            theme: liquid
            aside:
              card_newest_comments:
                avatar: true
            avatar:
              effect: false
        """.trimIndent()
        assertNull(HexoAvatar.parseAvatar(yaml))
        assertNull(HexoAvatar.chooseAvatar(null, null))
        assertNull(HexoAvatar.resolve(HexoAvatar.parseSite("theme: liquid\n"), "true"))
    }

    @Test
    fun overrideWithoutAvatarFallsBackToTheThemeConfig() {
        val override = "footer:\n  since: 2020\n"
        val theme = "avatar:\n  img: /images/avatar.jpg\n"
        assertEquals("/images/avatar.jpg", HexoAvatar.chooseAvatar(override, theme))
    }

    @Test
    fun avatarImgWinsAndAuthorNameComesFromTheSiteConfig() {
        val yaml = """
            avatar:
              url: /images/from-url.jpg
              img: /images/avatar.jpg
            author:
              name: Example Author
              avatar: /images/author.jpg
            about:
              avatar: /images/about.jpg
            aside:
              card_newest_comments:
                avatar: true
        """.trimIndent()
        assertEquals("/images/avatar.jpg", HexoAvatar.parseAvatar(yaml))
        assertEquals("Example Author", HexoAvatar.parseAuthor(yaml))
        assertEquals("Example Author", HexoAvatar.parseAuthor("author: Example Author\n"))
    }

    @Test
    fun otherAvatarFieldsAreUsedOnlyWhenImgIsAbsent() {
        assertEquals("/images/from-url.jpg", HexoAvatar.parseAvatar("avatar:\n  url: /images/from-url.jpg\n"))
        assertEquals("/images/author.jpg", HexoAvatar.parseAvatar("author:\n  name: Example Author\n  avatar: /images/author.jpg\n"))
        assertEquals("/images/about.jpg", HexoAvatar.parseAvatar("about:\n  avatar: /images/about.jpg\n"))
        val site = HexoAvatar.parseSite("url: https://example.com/blog\nroot: /blog/\ntheme: liquid\n")
        val resolved = HexoAvatar.resolve(site, "/images/avatar.jpg")
        assertEquals("https://example.com/blog/images/avatar.jpg", resolved?.publicUrl)
        assertEquals("source/images/avatar.jpg", resolved?.sourcePath)
    }
}
