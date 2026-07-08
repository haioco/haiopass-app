package com.haio.bypass.config

import kotlinx.serialization.Serializable

@Serializable
data class TrojanConfig(
    val password: String,
    val server: String,
    val port: Int,
    val sni: String
)
