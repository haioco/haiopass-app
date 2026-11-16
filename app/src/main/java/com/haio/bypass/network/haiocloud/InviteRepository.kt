package com.haio.bypass.network.haiocloud

import com.haio.bypass.config.InviteLinkParser

/**
 * Device enrollment + invite management.
 *
 * Public preview/enroll use [PortalClient.anonymousApi] on purpose: a guest
 * with only an invite link must not create a portal session, send a bearer
 * token, or trigger the 401 refresh authenticator. Owner calls use the normal
 * authenticated API.
 *
 * Every public method validates the token shape before hitting the network;
 * invalid tokens are rejected locally with `invite_not_found`.
 */
class InviteRepository(private val client: PortalClient) {

    suspend fun preview(token: String): InvitePreview {
        val valid = requireToken(token)
        return portalCall { client.anonymousApi.invitePreview(valid) }
    }

    suspend fun enroll(
        token: String,
        publicKeyPem: String,
        deviceName: String,
        appVersion: String,
        requestId: String
    ): EnrollmentResult {
        val valid = requireToken(token)
        return portalCall {
            client.anonymousApi.enrollInvite(
                valid,
                EnrollRequestBody(
                    publicKey = publicKeyPem,
                    deviceName = deviceName.take(100),
                    platform = PLATFORM,
                    appVersion = appVersion.take(40),
                    requestId = requestId
                )
            )
        }
    }

    // ── Owner (authenticated) ─────────────────────────────────────────────

    suspend fun createPrimaryInvite(accountId: String, ttlHours: Int? = null): InviteToken =
        portalCall {
            client.api.createPrimaryInvite(accountId, InviteCreateBody(ttlHours))
        }

    suspend fun enrollPrimary(
        accountId: String,
        publicKeyPem: String,
        deviceName: String,
        appVersion: String,
        requestId: String
    ): EnrollmentResult = portalCall {
        client.api.enrollPrimary(
            accountId,
            EnrollRequestBody(
                publicKey = publicKeyPem,
                deviceName = deviceName.take(100),
                platform = PLATFORM,
                appVersion = appVersion.take(40),
                requestId = requestId
            )
        )
    }

    suspend fun resetPrimaryDevice(accountId: String, reason: String = ""): DeviceResetResult =
        portalCall {
            client.api.resetPrimaryDevice(
                accountId,
                DeviceResetBody(reason = reason.take(120).ifBlank { null })
            )
        }

    suspend fun createMemberInvite(
        accountId: String,
        memberId: String,
        ttlHours: Int? = null
    ): InviteToken = portalCall {
        client.api.createMemberInvite(accountId, memberId, InviteCreateBody(ttlHours))
    }

    suspend fun resetMemberDevice(
        accountId: String,
        memberId: String,
        reason: String = ""
    ): DeviceResetResult = portalCall {
        client.api.resetMemberDevice(
            accountId,
            memberId,
            DeviceResetBody(reason = reason.take(120).ifBlank { null })
        )
    }

    suspend fun pendingInvites(accountId: String): List<PendingInvite> = portalCall {
        client.api.pendingInvites(accountId)
    }.items

    suspend fun cancelInvite(accountId: String, inviteId: String) {
        portalCall { client.api.cancelInvite(accountId, inviteId) }
    }

    private fun requireToken(token: String): String =
        InviteLinkParser.parse(token)
            ?: throw PortalException(PortalError.DeviceEnrollment("invite_not_found", null))

    companion object {
        const val PLATFORM = "android"
    }
}
