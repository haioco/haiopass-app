package com.haio.bypass.network.haiocloud

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * HaioGate packages, accounts, quota sync, purchase/renewal intents and
 * multi-user member operations.
 *
 * Member create/rotate responses are shape-polymorphic:
 *  - legacy mode returns `MemberCredentialSerializer` (member + password/URL);
 *  - device-lock mode returns `{member, invite_required: true, invite?}`.
 * Both are normalised into [AddMemberResult] so the UI never shows a
 * password/URL for a locked credential.
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
        email: String? = null,
        issueInvite: Boolean = false
    ): AddMemberResult = parseMemberMutation(
        portalCall {
            client.api.addMember(
                accountId,
                AddMemberBody(
                    displayName = displayName,
                    mobile = mobile?.takeIf { it.isNotBlank() },
                    email = email?.takeIf { it.isNotBlank() },
                    issueInvite = issueInvite
                )
            )
        }
    )

    suspend fun rotateMember(accountId: String, memberId: String): AddMemberResult =
        parseMemberMutation(portalCall { client.api.rotateMember(accountId, memberId) })

    suspend fun revokeMember(accountId: String, memberId: String) {
        portalCall { client.api.revokeMember(accountId, memberId) }
    }

    private fun parseMemberMutation(payload: JsonObject): AddMemberResult {
        val inviteNode = payload.get("invite")?.takeIf { it.isJsonObject }?.asJsonObject
        val invite = inviteNode?.let {
            runCatching { gson.fromJson(it, InviteToken::class.java) }.getOrNull()
        }
        val inviteRequired = runCatching {
            payload.get("invite_required")?.asBoolean
        }.getOrNull() ?: (invite != null)

        val memberNode = sequenceOf("member", "credential")
            .mapNotNull { key -> payload.get(key)?.takeIf { it.isJsonObject }?.asJsonObject }
            .firstOrNull()
            ?: payload
        val member = try {
            gson.fromJson(memberNode, PortalMember::class.java)
        } catch (exception: Exception) {
            throw PortalException(PortalError.Unknown("پاسخ مدیریت کاربر قابل خواندن نیست."))
        }
        return AddMemberResult(
            member = member,
            inviteRequired = inviteRequired || invite != null,
            invite = invite
        )
    }
}
