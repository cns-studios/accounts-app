package com.cns.accounts.data.qr

import java.net.URI

/**
 * A login QR code from the accounts website (auth-v2 `POST /api/auth/qr`): the code's id and
 * secret. The secret proves the code was read off that page; it can't log anyone in by itself,
 * only this app's session can confirm it (see [QrLogins]).
 */
data class QrLoginCode(val id: String, val secret: String) {
    // Never in logs or crash reports.
    override fun toString(): String = "QrLoginCode($id)"
}

/**
 * Reads the URL in a login QR code, `<accounts site>/qr#<id>.<secret>`, as the in-app scanner
 * sees it or Android hands it over from the camera app. The accounts site's "Open in CNS
 * Accounts" link carries the same as `?c=<id>.<secret>`. Only links to [accountsWebUrl] (the
 * site this build talks to) count: a code pointing anywhere else is not a CNS login.
 */
object QrLoginLink {
    private val Code = Regex("^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.([A-Za-z0-9_-]{16,128})$", RegexOption.IGNORE_CASE)
    private const val Path = "/qr"

    fun parse(link: String, accountsWebUrl: String): QrLoginCode? {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
        val site = runCatching { URI(accountsWebUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals(site.scheme, ignoreCase = true)) return null
        if (!uri.host.equals(site.host, ignoreCase = true) || uri.port != site.port) return null
        if (uri.rawPath?.trimEnd('/') != Path) return null
        val value = uri.rawFragment?.takeIf { it.isNotEmpty() }
            ?: uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("c=") }?.removePrefix("c=")
            ?: return null
        val match = Code.matchEntire(value) ?: return null
        return QrLoginCode(id = match.groupValues[1].lowercase(), secret = match.groupValues[2])
    }
}
