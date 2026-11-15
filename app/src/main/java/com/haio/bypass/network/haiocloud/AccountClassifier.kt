package com.haio.bypass.network.haiocloud

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

enum class AccountCategory { ACTIVE, EXPIRED, SUPPORT }

/**
 * Client-side classification of HaioGate accounts.
 *
 * The backend list endpoint returns every status, so the app groups accounts
 * itself (see PLAN-OTP-HAIOGATE-ANDROID-FOCUSED.md §9.2).
 */
object AccountClassifier {

    fun parseTimestamp(raw: String?): Instant? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun categoryOf(
        status: String?,
        expiredAt: String?,
        trafficIsOver: Boolean,
        now: Instant = Instant.now()
    ): AccountCategory {
        val normalized = status?.trim()?.lowercase()
        if (normalized == "suspended") return AccountCategory.SUPPORT
        if (normalized == "expired" || normalized == "quota_exhausted") {
            return AccountCategory.EXPIRED
        }
        if (trafficIsOver) return AccountCategory.EXPIRED

        val expiry = parseTimestamp(expiredAt)
        // Unknown expiry is never treated as active.
        if (expiry == null) return AccountCategory.SUPPORT
        if (!expiry.isAfter(now)) return AccountCategory.EXPIRED
        return if (normalized == "active") AccountCategory.ACTIVE else AccountCategory.SUPPORT
    }

    fun categoryOf(account: HaioGateAccount, now: Instant = Instant.now()): AccountCategory =
        categoryOf(account.status, account.expiredAt, account.trafficIsOver == true, now)

    /** Active: soonest expiry first. Expired: most recently ended first. */
    fun sortForDisplay(
        accounts: List<HaioGateAccount>,
        category: AccountCategory
    ): List<HaioGateAccount> = when (category) {
        AccountCategory.ACTIVE -> accounts.sortedBy {
            parseTimestamp(it.expiredAt) ?: Instant.MAX
        }
        AccountCategory.EXPIRED -> accounts.sortedByDescending {
            parseTimestamp(it.expiredAt) ?: Instant.MIN
        }
        AccountCategory.SUPPORT -> accounts.sortedBy { it.packageName.orEmpty() }
    }
}
