package com.haio.bypass.network.haiocloud

/**
 * Pure validation/normalisation helpers for the HaioCloud portal flow.
 *
 * Kept free of Android types so they can be covered by plain JVM unit tests.
 */
object PortalValidators {

    private val MOBILE_REGEX = Regex("^09\\d{9}$")
    private val DIGIT_MAP = buildMap {
        // Persian (Extended Arabic-Indic) digits.
        "۰۱۲۳۴۵۶۷۸۹".forEachIndexed { index, c -> put(c, ('0' + index)) }
        // Arabic-Indic digits.
        "٠١٢٣٤٥٦٧٨٩".forEachIndexed { index, c -> put(c, ('0' + index)) }
    }

    /** Converts Persian/Arabic digits to ASCII digits. */
    fun normalizeDigits(input: String): String = buildString(input.length) {
        input.forEach { c -> append(DIGIT_MAP[c] ?: c) }
    }

    /**
     * Normalises an Iranian mobile number to `09xxxxxxxxx`.
     *
     * Accepts spaces, dashes, parentheses, `+98`, `0098`, and Persian/Arabic
     * digits. Returns an empty string when nothing usable remains.
     */
    fun normalizeMobile(raw: String): String {
        val digits = normalizeDigits(raw).filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ""
            digits.startsWith("0098") && digits.length >= 14 -> "0" + digits.drop(4)
            digits.startsWith("98") && digits.length == 12 -> "0" + digits.drop(2)
            digits.startsWith("9") && digits.length == 10 -> "0$digits"
            else -> digits
        }
    }

    fun isValidMobile(raw: String): Boolean = MOBILE_REGEX.matches(normalizeMobile(raw))

    /** Keeps digits only and caps the national code at 10 characters. */
    fun normalizeNationalCode(raw: String): String =
        normalizeDigits(raw).filter { it.isDigit() }.take(10)

    /**
     * Basic national-code validation: ten digits and not a repeated digit.
     * The official checksum is intentionally not enforced so a valid customer
     * code is never rejected client-side; the Shahkar provider is authoritative.
     */
    fun isValidNationalCode(raw: String): Boolean {
        val code = normalizeNationalCode(raw)
        if (code.length != 10) return false
        if (code.all { it == code.first() }) return false
        return true
    }

    fun isValidOtp(raw: String): Boolean {
        val digits = normalizeDigits(raw).filter { it.isDigit() }
        return digits.length == 6
    }

    fun normalizeOtp(raw: String): String =
        normalizeDigits(raw).filter { it.isDigit() }.take(6)
}
