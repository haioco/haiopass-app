package com.haio.bypass.network.haiocloud

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * HaioGate packages, accounts, quota sync, purchase/renewal intents and
 * multi-user member operations.
 */
class HaioGateRepository(private val client: PortalClient) {

    private val gson = Gson()

    suspend fun packages(): List<HaioGatePackage> = portalCall {
        client.api.packages()
    }.items()

    suspend fun accounts(): List<HaioGateAccount> = portalCall {
        client.api.accounts()
    }.items()

    suspend fun account(id: String): HaioGateAccountDetail = portalCall {
        client.api.account(id)
    }

    /** On-demand server-side quota sync (never called for every list item). */
    suspend fun syncQuota(id: String): HaioGateAccountDetail = portalCall {
        client.api.syncQuota(id)
    }

    suspend fun startBuy(packageId: String): PurchaseIntentResult = portalCall {
        client.api.createPurchaseIntent(PackageIntentBody(packageId = packageId))
    }

    suspend fun startRenew(
        accountId: String,
        packageId: String?,
        includedUsers: Int? = null
    ): PurchaseIntentResult = portalCall {
        client.api.createRenewIntent(
            accountId,
            RenewIntentBody(packageId = packageId, includedUsers = includedUsers)
        )
    }

    suspend fun addMember(
        accountId: String,
        displayName: String,
        mobile: String? = null,
        email: String? = null
    ): PortalMember = parseMember(
        portalCall {
            client.api.addMember(
                accountId,
                AddMemberBody(
                    displayName = displayName,
                    mobile = mobile?.takeIf { it.isNotBlank() },
                    email = email?.takeIf { it.isNotBlank() }
                )
            )
        }
    )

    suspend fun rotateMember(accountId: String, memberId: String): PortalMember = parseMember(
        portalCall { client.api.rotateMember(accountId, memberId) }
    )

    suspend fun revokeMember(accountId: String, memberId: String) {
        portalCall { client.api.revokeMember(accountId, memberId) }
    }

    private fun parseMember(payload: JsonObject): PortalMember {
        val candidate = sequenceOf("member", "credential")
            .mapNotNull { key -> payload.get(key)?.takeIf { it.isJsonObject }?.asJsonObject }
            .firstOrNull()
            ?: payload
        return try {
            gson.fromJson(candidate, PortalMember::class.java)
        } catch (exception: Exception) {
            throw PortalException(PortalError.Unknown("پاسخ مدیریت کاربر قابل خواندن نیست."))
        }
    }
}
