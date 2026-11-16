package com.haio.bypass.network.haiocloud

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Sealed error model for the portal API. UI copy and retry policy are derived
 * from this type instead of scraping raw DRF payloads.
 */
sealed class PortalError {
    data object Unauthorized : PortalError()
    data object ShahkarRequired : PortalError()
    data object MobileRequired : PortalError()
    data class OrganizationRequired(val organizations: List<OrganizationOption>) : PortalError()
    data class RateLimited(val retryAfterSeconds: Int?) : PortalError()
    data class Validation(
        val detail: String?,
        val fieldErrors: Map<String, String> = emptyMap()
    ) : PortalError()
    data class ProviderUnavailable(val detail: String?) : PortalError()
    data class Server(val code: Int, val detail: String?) : PortalError()
    data class Http(val code: Int, val detail: String?) : PortalError()
    /** Typed device-enrollment / invite error from `{detail, code}`. */
    data class DeviceEnrollment(val code: String, val detail: String?) : PortalError()
    data class Network(val detail: String?) : PortalError()
    data class Unknown(val detail: String?) : PortalError()
}

class PortalException(val error: PortalError) : Exception(error.toString())

fun PortalError.userMessage(): String = when (this) {
    is PortalError.Unauthorized -> "نشست شما منقضی شده است. دوباره وارد شوید."
    is PortalError.ShahkarRequired -> "برای ادامه، احراز هویت شاهکار لازم است."
    is PortalError.MobileRequired -> "برای خرید، شماره موبایل معتبر روی حساب لازم است."
    is PortalError.OrganizationRequired -> "حساب سازمانی مورد نظر را انتخاب کنید."
    is PortalError.RateLimited -> "تعداد تلاش‌ها بیش از حد مجاز است. لطفاً کمی بعد تلاش کنید."
    is PortalError.Validation ->
        detail?.takeIf { it.isNotBlank() }
            ?: fieldErrors.values.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "اطلاعات ارسال‌شده معتبر نیست."
    is PortalError.ProviderUnavailable -> "سرویس مورد نظر در دسترس نیست. کمی بعد دوباره تلاش کنید."
    is PortalError.Server -> "خطای موقت سرور. دوباره تلاش کنید."
    is PortalError.Http -> detail?.takeIf { it.isNotBlank() } ?: "درخواست انجام نشد."
    is PortalError.DeviceEnrollment -> enrollmentMessage(this)
    is PortalError.Network -> "اتصال به سرور برقرار نشد. اینترنت خود را بررسی کنید."
    is PortalError.Unknown -> detail?.takeIf { it.isNotBlank() } ?: "خطای نامشخص رخ داد."
}

/**
 * Persian copy for device-enrollment/invite errors (§15.3).
 * Used by JoinScreen, member dialogs and the owner device panel.
 */
fun enrollmentMessage(error: PortalError): String = when (error) {
    is PortalError.DeviceEnrollment -> when (error.code) {
        "invite_not_found" -> "لینک دعوت پیدا نشد."
        "invite_expired" ->
            "این لینک منقضی شده است. از صاحب کانفیگ لینک جدید بگیرید."
        "invite_used" ->
            "این لینک قبلاً استفاده شده است. برای دستگاه جدید، از صاحب کانفیگ لینک تازه بگیرید."
        "invite_revoked" -> "این لینک لغو شده است."
        "member_revoked" -> "این کاربر حذف شده است."
        "member_not_found" -> "این کاربر پیدا نشد."
        "slot_taken", "slots_full" -> "ظرفیت این پلن تکمیل است."
        "csr_invalid" -> "ساخت کلید امن ناموفق بود؛ دوباره تلاش کنید."
        "device_already_bound" ->
            "این کاربر روی دستگاه دیگری ثبت شده است. برای تغییر دستگاه، ابتدا دستگاه فعلی را حذف کنید."
        "not_manageable" -> "این اکانت برای ثبت دستگاه در دسترس نیست."
        "ca_unavailable" ->
            "ثبت دستگاه موقتاً در دسترس نیست؛ بعداً تلاش کنید."
        "enrollment_disabled" -> "ثبت دستگاه در حال حاضر غیرفعال است."
        "invalid_request_id" -> "درخواست معتبر نیست؛ دوباره تلاش کنید."
        "invite_ttl_invalid" -> "مدت اعتبار لینک دعوت معتبر نیست."
        "legacy_unlimited" -> "این اکانت قدیمی است و از لینک دعوت پشتیبانی نمی‌کند."
        "proxy_error" -> "اتصال به سرور برقرار نشد. اینترنت را بررسی کنید و دوباره تلاش کنید."
        else -> error.detail?.takeIf { it.isNotBlank() } ?: "ثبت دستگاه انجام نشد."
    }
    is PortalError.Network ->
        "اتصال به سرور برقرار نشد. اینترنت را بررسی کنید و دوباره تلاش کنید."
    is PortalError.RateLimited ->
        "تعداد تلاش‌ها زیاد بود؛ چند دقیقه بعد دوباره تلاش کنید."
    is PortalError.Unauthorized -> "نشست شما منقضی شده است. دوباره وارد شوید."
    else -> error.userMessage()
}

/** Maps any throwable from the invite/enroll calls to Persian copy. */
fun inviteErrorMessage(throwable: Throwable): String =
    when (throwable) {
        is PortalException -> enrollmentMessage(throwable.error)
        is IllegalArgumentException, is IllegalStateException ->
            throwable.message?.takeIf { it.isNotBlank() }
                ?: "ثبت دستگاه انجام نشد؛ دوباره تلاش کنید."
        else -> "اتصال به سرور برقرار نشد. اینترنت را بررسی کنید و دوباره تلاش کنید."
    }

internal object PortalErrors {

    /**
     * Codes returned by the invite/enroll endpoints (`{detail, code}`).
     * Kept in one place so error mapping stays in sync with the server
     * contract and the Persian copy in §15.3.
     */
    private val ENROLLMENT_CODES = setOf(
        "invite_not_found",
        "invite_expired",
        "invite_used",
        "invite_revoked",
        "member_revoked",
        "member_not_found",
        "slot_taken",
        "slots_full",
        "csr_invalid",
        "device_already_bound",
        "not_manageable",
        "proxy_error",
        "ca_unavailable",
        "enrollment_disabled",
        "invalid_request_id",
        "invite_ttl_invalid",
        "legacy_unlimited"
    )

    private val gson = Gson()

    private data class ErrorEnvelope(
        @SerializedName("detail") val detail: JsonElement? = null,
        @SerializedName("code") val code: String? = null,
        @SerializedName("organizations") val organizations: List<OrganizationOption>? = null,
        @SerializedName("retry_after") val retryAfter: Int? = null
    )

    fun fromResponse(response: Response<*>): PortalError {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val envelope = parseEnvelope(raw)
        val code = envelope?.code.orEmpty()
        val detail = extractDetail(envelope, raw)

        if (code.isNotBlank() && code in ENROLLMENT_CODES) {
            return PortalError.DeviceEnrollment(code, detail)
        }

        return when (response.code()) {
            400 -> when {
                code == "organization_required" ->
                    PortalError.OrganizationRequired(envelope?.organizations.orEmpty())
                code == "mobile_required" -> PortalError.MobileRequired
                code == "shahkar_required" -> PortalError.ShahkarRequired
                code == "rate_limited" -> PortalError.RateLimited(envelope?.retryAfter)
                else -> PortalError.Validation(detail, fieldErrors(raw))
            }
            401 -> PortalError.Unauthorized
            403 -> when {
                code == "shahkar_required" -> PortalError.ShahkarRequired
                detail?.contains("shahkar", ignoreCase = true) == true -> PortalError.ShahkarRequired
                else -> PortalError.Http(403, detail)
            }
            404 -> PortalError.Http(404, detail ?: "موردی یافت نشد.")
            409 -> PortalError.Http(409, detail)
            429 -> PortalError.RateLimited(
                envelope?.retryAfter ?: response.headers()["Retry-After"]?.toIntOrNull()
            )
            500, 501, 502, 503, 504 -> PortalError.Server(response.code(), detail)
            else -> PortalError.Http(response.code(), detail)
        }
    }

    fun fromThrowable(throwable: Throwable): PortalError = when (throwable) {
        is PortalException -> throwable.error
        is UnknownHostException -> PortalError.Network(throwable.message)
        is SocketTimeoutException -> PortalError.Network(throwable.message)
        is IOException -> PortalError.Network(throwable.message)
        else -> PortalError.Unknown(throwable.message)
    }

    private fun parseEnvelope(raw: String?): ErrorEnvelope? {
        if (raw.isNullOrBlank()) return null
        return runCatching { gson.fromJson(raw, ErrorEnvelope::class.java) }.getOrNull()
    }

    private fun extractDetail(envelope: ErrorEnvelope?, raw: String?): String? {
        val element = envelope?.detail
        if (element != null && !element.isJsonNull) {
            if (element.isJsonPrimitive) return element.asString.takeIf { it.isNotBlank() }
            if (element.isJsonArray) {
                val first = element.asJsonArray.firstOrNull()
                if (first != null && first.isJsonPrimitive) return first.asString
            }
            if (element.isJsonObject) {
                val first = element.asJsonObject.entrySet().firstOrNull()?.value
                if (first != null && first.isJsonPrimitive) return first.asString
            }
        }
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = gson.fromJson(raw, JsonObject::class.java)
            obj.entrySet().firstOrNull { it.value.isJsonPrimitive }?.value?.asString
        }.getOrNull()
    }

    private fun fieldErrors(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val obj = runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull()
            ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        obj.entrySet().forEach { (key, value) ->
            if (key == "detail" || key == "code") return@forEach
            val text = when {
                value.isJsonPrimitive -> value.asString
                value.isJsonArray -> value.asJsonArray.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString
                else -> null
            }
            if (!text.isNullOrBlank()) result[key] = text
        }
        return result
    }
}

internal suspend fun <T> portalCall(block: suspend () -> Response<T>): T {
    val response = try {
        block()
    } catch (throwable: Throwable) {
        throw PortalException(PortalErrors.fromThrowable(throwable))
    }
    if (!response.isSuccessful) throw PortalException(PortalErrors.fromResponse(response))
    return response.body() ?: throw PortalException(PortalError.Unknown("پاسخ خالی از سرور"))
}

internal suspend fun portalCallUnit(block: suspend () -> Response<Unit>): Unit {
    val response = try {
        block()
    } catch (throwable: Throwable) {
        throw PortalException(PortalErrors.fromThrowable(throwable))
    }
    if (!response.isSuccessful) throw PortalException(PortalErrors.fromResponse(response))
}
