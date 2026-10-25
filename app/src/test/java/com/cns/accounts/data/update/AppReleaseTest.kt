package com.cns.accounts.data.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    private val digest = "a".repeat(64)
    private val apkUrl = "https://github.com/cns-studios/accounts-app/releases/download/v1.3.0/cns-accounts-1.3.0.apk"

    private fun asset(name: String, url: String, digest: String? = null, size: Long = 1234) = JSONObject()
        .put("name", name)
        .put("browser_download_url", url)
        .put("size", size)
        .apply { if (digest != null) put("digest", digest) }

    private fun release(tag: String = "v1.3.0", vararg assets: JSONObject = arrayOf(asset("cns-accounts-1.3.0.apk", apkUrl, "sha256:$digest"))) = JSONObject()
        .put("tag_name", tag)
        .put("draft", false)
        .put("prerelease", false)
        .put("html_url", "https://github.com/cns-studios/accounts-app/releases/tag/$tag")
        .put("assets", JSONArray(assets.toList()))

    @Test
    fun readsVersionApkAndDigest() {
        val parsed = AppRelease.fromGitHub(release())!!
        assertEquals(AppVersion(1, 3, 0), parsed.version)
        assertEquals("v1.3.0", parsed.tag)
        assertEquals(apkUrl, parsed.apkUrl)
        assertEquals(1234L, parsed.apkSize)
        assertEquals(digest, parsed.sha256)
        assertNull(parsed.sha256Url)
    }

    @Test
    fun digestIsLowercased() {
        val upper = "ABCDEF0123456789".repeat(4)
        val parsed = AppRelease.fromGitHub(release(assets = arrayOf(asset("app.apk", apkUrl, "sha256:$upper"))))!!
        assertEquals(upper.lowercase(), parsed.sha256)
    }

    @Test
    fun fallsBackToTheChecksumFileWithoutADigest() {
        val sumUrl = "$apkUrl.sha256"
        val parsed = AppRelease.fromGitHub(release(assets = arrayOf(asset("cns-accounts-1.3.0.apk", apkUrl), asset("cns-accounts-1.3.0.apk.sha256", sumUrl))))!!
        assertNull(parsed.sha256)
        assertEquals(sumUrl, parsed.sha256Url)
    }

    @Test
    fun noChecksumAtAllMeansNoRelease() =
        assertNull(AppRelease.fromGitHub(release(assets = arrayOf(asset("cns-accounts-1.3.0.apk", apkUrl)))))

    @Test
    fun malformedDigestIsIgnored() {
        val bad = arrayOf(asset("app.apk", apkUrl, "sha256:xyz"), asset("app.apk", apkUrl, "md5:$digest"), asset("app.apk", apkUrl, "sha256:${"a".repeat(63)}"))
        bad.forEach { assertNull(AppRelease.fromGitHub(release(assets = arrayOf(it)))) }
    }

    @Test
    fun draftsPrereleasesAndBadTagsAreSkipped() {
        assertNull(AppRelease.fromGitHub(release().put("draft", true)))
        assertNull(AppRelease.fromGitHub(release().put("prerelease", true)))
        assertNull(AppRelease.fromGitHub(release(tag = "nightly")))
        assertNull(AppRelease.fromGitHub(release().put("assets", JSONArray())))
        assertNull(AppRelease.fromGitHub(release().apply { remove("assets") }))
    }

    @Test
    fun apkMustComeFromGitHubOverHttps() {
        listOf(
            "http://github.com/cns-studios/accounts-app/releases/download/v1.3.0/app.apk",
            "https://evil.example/app.apk",
            "https://github.com.evil.example/app.apk",
            "file:///sdcard/app.apk",
            "not a url",
        ).forEach { url ->
            assertNull(url, AppRelease.fromGitHub(release(assets = arrayOf(asset("app.apk", url, "sha256:$digest")))))
        }
    }

    @Test
    fun picksTheApkAmongOtherAssets() {
        val parsed = AppRelease.fromGitHub(
            release(assets = arrayOf(asset("notes.txt", "https://github.com/x/notes.txt"), asset("app.apk", apkUrl, "sha256:$digest"))),
        )
        assertNotNull(parsed)
        assertEquals(apkUrl, parsed!!.apkUrl)
    }

    @Test
    fun trustedHosts() {
        assertTrue(AppRelease.isTrusted("https://github.com/a/b"))
        assertTrue(AppRelease.isTrusted("https://objects.githubusercontent.com/a"))
        assertTrue(AppRelease.isTrusted("https://release-assets.githubusercontent.com/a"))
        assertFalse(AppRelease.isTrusted("https://api.github.com/repos/a/b/releases/latest"))
        assertFalse(AppRelease.isTrusted("https://raw.githubusercontent.com/a"))
    }

    @Test
    fun readsSha256sumFiles() {
        assertEquals(digest, AppRelease.parseChecksumFile("$digest  cns-accounts-1.3.0.apk\n"))
        assertEquals(digest, AppRelease.parseChecksumFile(digest.uppercase()))
        assertNull(AppRelease.parseChecksumFile(""))
        assertNull(AppRelease.parseChecksumFile("not-a-digest  file.apk"))
    }

    @Test
    fun sha256HexMatchesKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex { })
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex { it.update("abc".toByteArray()) })
        // Fed in chunks, as the download does.
        assertEquals(sha256Hex { it.update("abc".toByteArray()) }, sha256Hex { d -> "abc".forEach { d.update(it.code.toByte()) } })
    }
}
