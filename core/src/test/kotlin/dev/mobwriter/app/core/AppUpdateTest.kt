package dev.mobwriter.app.core

import dev.mobwriter.app.core.github.HttpRequest
import dev.mobwriter.app.core.github.HttpResponse
import dev.mobwriter.app.core.github.HttpTransport
import dev.mobwriter.app.core.update.AppUpdate
import dev.mobwriter.app.core.update.DirectInstallStep
import dev.mobwriter.app.core.update.PublishedRelease
import dev.mobwriter.app.core.update.ReleaseAsset
import dev.mobwriter.app.core.update.ReleaseLookup
import dev.mobwriter.app.core.update.UpdateChoice
import dev.mobwriter.app.core.update.UpdateException
import dev.mobwriter.app.core.update.UpdateManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateTest {
    private val page = "https://github.com/example-owner/example-app/releases/tag/v1.2.0"
    private val apk = "https://github.com/example-owner/example-app/releases/download/v1.2.0/MobWriter.apk"

    @Test
    fun repositorySlugRejectsPathTricks() {
        assertTrue(AppUpdate.isRepositorySlug("example-owner"))
        assertTrue(AppUpdate.isRepositorySlug("example.app"))
        assertFalse(AppUpdate.isRepositorySlug("owner/repo"))
        assertFalse(AppUpdate.isRepositorySlug("../etc"))
        assertFalse(AppUpdate.isRepositorySlug(".."))
        assertFalse(AppUpdate.isRepositorySlug(""))
        assertFalse(AppUpdate.isRepositorySlug(".hidden"))
    }

    @Test
    fun higherManifestVersionInstallsTheApk() {
        val choice = AppUpdate.choose(1, "1.0.0", release(), UpdateManifest(4, "1.2.0", "MobWriter.apk"))
        val install = assertIs<UpdateChoice.Install>(choice)
        assertEquals("1.2.0", install.versionName)
        assertEquals(4, install.versionCode)
        assertEquals(apk, install.downloadUrl)
        assertEquals(page, install.pageUrl)
    }

    @Test
    fun sameOrOlderVersionCodeIsCurrent() {
        assertIs<UpdateChoice.UpToDate>(
            AppUpdate.choose(4, "1.2.0", release(), UpdateManifest(4, "9.0.0", "MobWriter.apk")),
        )
        assertIs<UpdateChoice.UpToDate>(
            AppUpdate.choose(5, "1.2.0", release(), UpdateManifest(4, "1.2.0", "MobWriter.apk")),
        )
    }

    @Test
    fun missingApkOpensTheReleasePage() {
        val choice = AppUpdate.choose(
            1,
            "1.0.0",
            release().copy(assets = listOf(ReleaseAsset("update.json", "$apk.json"))),
            UpdateManifest(3, "1.1.0", "MobWriter.apk"),
        )
        assertEquals(page, assertIs<UpdateChoice.OpenPage>(choice).pageUrl)
    }

    @Test
    fun untrustedApkHostFallsBackToThePage() {
        val hostile = release().copy(
            assets = listOf(ReleaseAsset("MobWriter.apk", "https://evil.example/MobWriter.apk")),
        )
        val choice = AppUpdate.choose(1, "1.0.0", hostile, UpdateManifest(3, "1.1.0", "MobWriter.apk"))
        assertIs<UpdateChoice.OpenPage>(choice)
    }

    @Test
    fun versionNameIsUsedWhenTheManifestIsMissing() {
        val newer = AppUpdate.choose(1, "1.0.0", release().copy(versionName = "1.2.0"), null)
        assertIs<UpdateChoice.Install>(newer)
        val same = AppUpdate.choose(1, "1.2.0", release().copy(versionName = "1.2"), null)
        assertIs<UpdateChoice.UpToDate>(same)
        val unknown = AppUpdate.choose(1, "1.0.0", release().copy(versionName = "build-4"), null)
        assertIs<UpdateChoice.OpenPage>(unknown)
    }

    @Test
    fun directInstallAsksOnceThenOpensThePage() {
        assertEquals(DirectInstallStep.Download, AppUpdate.directInstallStep(canInstallPackages = true, alreadyAskedPermission = false))
        assertEquals(DirectInstallStep.AskPermission, AppUpdate.directInstallStep(false, false))
        assertEquals(DirectInstallStep.OpenPage, AppUpdate.directInstallStep(false, true))
    }

    @Test
    fun artifactUrlsStayOnGitHub() {
        assertTrue(AppUpdate.isAllowedArtifactUrl(apk))
        assertTrue(AppUpdate.isAllowedArtifactUrl("https://release-assets.githubusercontent.com/path"))
        assertTrue(AppUpdate.isAllowedPageUrl(page))
        assertFalse(AppUpdate.isAllowedArtifactUrl("http://github.com/example-owner/example-app/MobWriter.apk"))
        assertFalse(AppUpdate.isAllowedArtifactUrl("https://evil.example/MobWriter.apk"))
        assertFalse(AppUpdate.isAllowedPageUrl("https://github.com.evil.example/releases"))
        assertFalse(AppUpdate.looksLikeApk("html".toByteArray()))
        assertTrue(AppUpdate.looksLikeApk(byteArrayOf(0x50, 0x4B, 3, 4, 0)))
    }

    @Test
    fun parsesReleaseAndManifest() {
        val parsed = AppUpdate.parseRelease(
            """
            {"tag_name":"v1.2.0","html_url":"$page","assets":[
              {"name":"notes.txt","browser_download_url":"https://github.com/example-owner/example-app/notes.txt"},
              {"name":"MobWriter.apk","browser_download_url":"$apk"},
              {"name":"update.json","browser_download_url":"https://github.com/example-owner/example-app/releases/download/v1.2.0/update.json"}
            ]}
            """.trimIndent(),
        )
        assertEquals("v1.2.0", parsed.tag)
        assertEquals("1.2.0", parsed.versionName)
        assertEquals(3, parsed.assets.size)
        val manifest = AppUpdate.parseManifest("""{"versionCode":4,"versionName":"1.2.0","apkName":"MobWriter.apk"}""")
        assertEquals(4, manifest.versionCode)
        assertFailsWith<UpdateException> { AppUpdate.parseManifest("""{"versionName":"1.2.0"}""") }
        assertFailsWith<UpdateException> { AppUpdate.parseRelease("""{"message":"Not Found"}""") }
    }

    @Test
    fun lookupUsesThePublicLatestReleaseAndThenTheManifest() {
        val transport = UpdateTransport(
            listOf(
                HttpResponse(200, releaseJson().toByteArray()),
                HttpResponse(200, """{"versionCode":"4","versionName":"1.2.0","apkName":"MobWriter.apk"}""".toByteArray()),
            ),
        )
        val lookup = ReleaseLookup(transport)
        val found = lookup.latest("example-owner", "example-app")
        val manifest = lookup.manifest(found)
        assertEquals("https://api.github.com/repos/example-owner/example-app/releases/latest", transport.seen[0].url)
        assertNull(transport.seen[0].headers["Authorization"])
        assertEquals("MobWriter", transport.seen[0].headers["User-Agent"])
        assertEquals("application/vnd.github+json", transport.seen[0].headers["Accept"])
        assertEquals("*/*", transport.seen[1].headers["Accept"])
        val choice = AppUpdate.choose(1, "1.0.0", found, manifest)
        assertIs<UpdateChoice.Install>(choice)
    }

    @Test
    fun missingLatestReleaseFallsBackToTheReleaseList() {
        val transport = UpdateTransport(
            listOf(
                HttpResponse(404, """{"message":"Not Found"}""".toByteArray()),
                HttpResponse(200, "[${releaseJson()}]".toByteArray()),
            ),
        )
        val found = ReleaseLookup(transport).latest("example-owner", "example-app")
        assertEquals("v1.2.0", found.tag)
        assertTrue(transport.seen[1].url.contains("/releases?per_page=10"))
    }

    @Test
    fun downloadRejectsUntrustedOrOversizedPayloads() {
        val transport = UpdateTransport(emptyList())
        assertFailsWith<UpdateException> {
            ReleaseLookup(transport).download("http://github.com/example-owner/example-app/MobWriter.apk")
        }
        assertEquals(0, transport.seen.size)
        val bytes = byteArrayOf(0x50, 0x4B, 1, 2, 3)
        val lookup = ReleaseLookup(UpdateTransport(listOf(HttpResponse(200, bytes))))
        assertFailsWith<UpdateException> { lookup.download(apk, maxBytes = 4) }
        val again = ReleaseLookup(UpdateTransport(listOf(HttpResponse(200, bytes))))
        assertEquals(5, again.download(apk, maxBytes = 5).size)
    }

    private fun release() = PublishedRelease(
        tag = "v1.2.0",
        versionName = "1.2.0",
        htmlUrl = page,
        assets = listOf(
            ReleaseAsset("MobWriter.apk", apk),
            ReleaseAsset("update.json", "https://github.com/example-owner/example-app/releases/download/v1.2.0/update.json"),
        ),
    )

    private fun releaseJson(): String = """
        {"tag_name":"v1.2.0","html_url":"$page","assets":[
          {"name":"MobWriter.apk","browser_download_url":"$apk"},
          {"name":"update.json","browser_download_url":"https://github.com/example-owner/example-app/releases/download/v1.2.0/update.json"}
        ]}
    """.trimIndent()
}

private class UpdateTransport(private val responses: List<HttpResponse>) : HttpTransport {
    val seen = mutableListOf<HttpRequest>()
    private var index = 0
    override fun execute(request: HttpRequest): HttpResponse {
        seen += request
        return responses[index++]
    }
}
