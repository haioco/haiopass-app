package com.haio.bypass.network.haiocloud

import com.haio.bypass.ui.util.JalaliDate
import java.time.Duration
import java.time.Instant

/** Formatting helpers shared by the HaioGate screens. */
object PortalFormat {

    private const val GB = 1024.0 * 1024.0 * 1024.0
    private const val MB = 1024.0 * 1024.0

    fun traffic(bytes: Long?): String {
        val value = bytes ?: 0L
        if (value <= 0L) return "0 GB"
        return when {
            value >= GB -> String.format("%.1f GB", value / GB)
            value >= MB -> String.format("%.0f MB", value / MB)
            else -> "$value B"
        }
    }

    fun usageDisplay(account: HaioGateAccount): String {
        val explicit = account.usageDisplay?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit
        return "${traffic(account.usageBytes)} از ${traffic(account.totalTrafficBytes)}"
    }

    fun usageDisplay(detail: HaioGateAccountDetail): String {
        val explicit = detail.usageDisplay?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit
        return "${traffic(detail.usageBytes)} از ${traffic(detail.totalTrafficBytes)}"
    }

    fun price(amount: Long?): String {
        val value = amount ?: 0L
        return if (value == 0L) "رایگان" else "${String.format("%,d", value)} تومان"
    }

    fun date(iso: String?): String {
        val value = iso?.takeIf { it.isNotBlank() } ?: return "—"
        return runCatching { JalaliDate.format(value) }.getOrDefault(value.take(10))
    }

    /** Queued renewals move the projected date; show it as a date when present. */
    fun projectedDate(iso: String?): String? =
        iso?.takeIf { it.isNotBlank() }?.let { date(it) }

    fun remainingDays(expiredAt: String?, now: Instant = Instant.now()): String? {
        val expiry = AccountClassifier.parseTimestamp(expiredAt) ?: return null
        val days = Duration.between(now, expiry).toDays()
        return when {
            days < 0 -> null
            days == 0L -> "امروز منقضی می‌شود"
            days == 1L -> "۱ روز مانده"
            days <= 10L -> "$days روز مانده"
            else -> null
        }
    }

    fun statusLabel(account: HaioGateAccount): String =
        statusLabel(account.status, account.category)

    fun statusLabel(detail: HaioGateAccountDetail): String =
        statusLabel(detail.status, detail.category)

    fun statusLabel(status: String?, category: AccountCategory): String = when (category) {
        AccountCategory.ACTIVE -> "فعال"
        AccountCategory.EXPIRED -> when (status) {
            "quota_exhausted" -> "اتمام حجم"
            else -> "منقضی شده"
        }
        AccountCategory.SUPPORT -> when (status) {
            "suspended" -> "تعلیق شده"
            else -> "نیازمند پشتیبانی"
        }
    }

    fun membersLabel(includedUsers: Int?): String? =
        includedUsers?.takeIf { it > 0 }?.let { "$it کاربر" }
}
