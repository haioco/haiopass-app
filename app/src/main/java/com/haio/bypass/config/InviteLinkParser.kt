package com.haio.bypass.config

import java.net.URI

/**
 * Parser for HaioGate device-enrollment invite links.
 *
 * Accepted inputs (the token itself is always validated first):
 *  - `hgi_<43 base64url chars>` (raw token paste)
 *  - `haio://join/hgi_...`
 *  - `https://my.haio.ir/join/hgi_...` (App Link / QR landing page)
 *
 * The token regex mirrors the server contract (`^hgi_[A-Za-z0-9_-]{43}$`).
 * Anything else is rejected locally before a network call: an invalid token
 * must never be sent to the preview/enroll endpoints.
 *
 * Tokens are bearer capabilities until redeemed. Do not log the input or the
 * parsed token.
 */
object InviteLinkParser {

    const val TOKEN_PATTERN = "^hgi_[A-Za-z0-9_-]{43}$"
    const val JOIN_SCHEME = "haio"
    const val JOIN_HOST = "join"
    const val WEB_HOST = "my.haio.ir"
    const val WEB_PATH_PREFIX = "/join/"

    private val tokenRegex = Regex(TOKEN_PATTERN)

    fun isValidToken(token: String?): Boolean {
        val value = token?.trim().orEmpty()
        return tokenRegex.matches(value)
    }

    /**
     * Extracts a valid token from a pasted link or raw token, or returns null.
     * Surrounding whitespace/quotes (common when copying from a chat app) are
     * ignored.
     */
    fun parse(input: String?): String? {
        val trimmed = input
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        if (isValidToken(trimmed)) return trimmed

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()

        return when {
            scheme == JOIN_SCHEME && host == JOIN_HOST -> {
                val segments = uri.path.orEmpty()
                    .split('/')
                    .filter { it.isNotEmpty() }
                // `haio://join/<token>` -> exactly one path segment.
                if (segments.size != 1) null else segments.first().takeIf(::isValidToken)
            }

            scheme == "https" && host == WEB_HOST -> parseWebPath(uri.path.orEmpty())

            // Tolerate the landing page being opened without the scheme by a
            // clipboard manager (rare); the host/path check is still strict.
            scheme == "http" && host == WEB_HOST -> parseWebPath(uri.path.orEmpty())

            else -> null
        }
    }

    fun buildDeepLink(token: String): String? =
        token.takeIf(::isValidToken)?.let { "$JOIN_SCHEME://$JOIN_HOST/$it" }

    fun buildWebLink(token: String): String? =
        token.takeIf(::isValidToken)?.let { "https://$WEB_HOST$WEB_PATH_PREFIX$it" }

    private fun parseWebPath(path: String): String? {
        val clean = path.trim('/')
        val segments = clean.split('/').filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "join") return null
        return segments[1].takeIf(::isValidToken)
    }
}
