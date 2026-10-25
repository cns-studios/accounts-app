package com.cns.accounts.data.update

import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/**
 * A release version, from a tag like `v1.2.3`. Releases are tagged MAJOR.MINOR.PATCH with
 * MINOR and PATCH below 100, so [code] (the Android versionCode CI builds with) always grows.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    val code: Int get() = major * 10_000 + minor * 100 + patch

    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val Pattern = Regex("""^v?(\d{1,4})(?:\.(\d{1,2}))?(?:\.(\d{1,2}))?$""")

        /** `1`, `1.2`, `1.2.3`, with or without a leading `v`; null for anything else. */
        fun parse(raw: String?): AppVersion? {
            val match = Pattern.matchEntire(raw?.trim().orEmpty()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(major.toInt(), minor.ifEmpty { "0" }.toInt(), patch.ifEmpty { "0" }.toInt())
        }
    }
}

/**
 * A published release with an APK this app can install. [sha256] is the APK's digest as
 * GitHub reports it, checked against the downloaded file before it goes to the installer;
 * without it [sha256Url] names a `.sha256` file to read it from.
 */
data class AppRelease(
    val version: AppVersion,
    val tag: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?,
    val sha256Url: String?,
    val pageUrl: String?,
) {
    companion object {
        /** Hosts a release asset may be served from: GitHub itself and its asset CDN. */
        private val TrustedHosts = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

        /**
         * A GitHub "latest release" response. Null when it is a draft or pre-release, has no
         * parseable version tag, or carries no APK served over https from GitHub.
         */
        fun fromGitHub(json: JSONObject): AppRelease? {
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val tag = json.optString("tag_name")
            val version = AppVersion.parse(tag) ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            val all = List(assets.length()) { assets.getJSONObject(it) }
            val apk = all.firstOrNull { it.optString("name").endsWith(".apk") && isTrusted(it.optString("browser_download_url")) } ?: return null
            val apkName = apk.optString("name")
            val digest = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.takeIf(::isSha256)
            val checksumFile = all.firstOrNull { it.optString("name") == "$apkName.sha256" }
                ?.optString("browser_download_url")?.takeIf(::isTrusted)
            if (digest == null && checksumFile == null) return null
            return AppRelease(
                version = version,
                tag = tag,
                apkUrl = apk.optString("browser_download_url"),
                apkSize = apk.optLong("size"),
                sha256 = digest?.lowercase(),
                sha256Url = checksumFile,
                pageUrl = json.optString("html_url").takeIf(::isTrusted),
            )
        }

        fun isTrusted(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.host in TrustedHosts
        }.getOrDefault(false)

        private fun isSha256(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        /** The digest in a `sha256sum`-style file (`<hex>  <file name>`), or null. */
        fun parseChecksumFile(text: String): String? =
            text.trim().split(Regex("\\s+")).firstOrNull()?.takeIf(::isSha256)?.lowercase()
    }
}

/** Hex SHA-256 of everything [feed] hands to the digest. */
fun sha256Hex(feed: (MessageDigest) -> Unit): String =
    MessageDigest.getInstance("SHA-256").also(feed).digest().joinToString("") { "%02x".format(it) }
