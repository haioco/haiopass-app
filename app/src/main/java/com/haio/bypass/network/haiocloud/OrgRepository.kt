package com.haio.bypass.network.haiocloud

/** Organizations are only needed when the buy response says so. */
class OrgRepository(private val client: PortalClient) {

    suspend fun organizations(): List<Organization> = portalCall {
        client.api.organizations()
    }.items()
}
