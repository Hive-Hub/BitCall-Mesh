package com.example.mesh

import org.json.JSONObject
import java.util.UUID

/**
 * Standard Multi-Hop Mesh Packet for BitCall.
 * Encapsulates routing metadata and an end-to-end encrypted payload.
 */
data class MeshPacket(
    val protocolVersion: Int = 1,
    val messageId: String = UUID.randomUUID().toString(),
    val destinationRoutingId: String, // Recipient BitCall ID, or "BROADCAST"
    val originRoutingId: String,      // Sender BitCall ID
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000L), // 24hr default TTL
    val hopLimit: Int = 7,
    val hopsTraversed: Int = 0,
    val packetType: Int, // 1: CHAT, 2: ACK, 3: PING, 4: PONG, 5: VOICE
    val encryptedPayload: String, // Base64 ciphertext with IV
    val authenticationData: String // Signature or MAC
) {
    companion object {
        const val TYPE_CHAT_MESSAGE = 1
        const val TYPE_ACKNOWLEDGEMENT = 2
        const val TYPE_PING_DISCOVERY = 3
        const val TYPE_PONG_DISCOVERY = 4
        const val TYPE_VOICE_STREAM = 5

        fun fromJson(jsonString: String): MeshPacket? {
            return try {
                val json = JSONObject(jsonString)
                MeshPacket(
                    protocolVersion = json.optInt("pv", 1),
                    messageId = json.getString("mid"),
                    destinationRoutingId = json.getString("dst"),
                    originRoutingId = json.getString("orig"),
                    createdAt = json.getLong("cat"),
                    expiresAt = json.getLong("eat"),
                    hopLimit = json.getInt("hl"),
                    hopsTraversed = json.optInt("ht", 0),
                    packetType = json.getInt("pt"),
                    encryptedPayload = json.getString("ep"),
                    authenticationData = json.optString("ad", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("pv", protocolVersion)
        json.put("mid", messageId)
        json.put("dst", destinationRoutingId)
        json.put("orig", originRoutingId)
        json.put("cat", createdAt)
        json.put("eat", expiresAt)
        json.put("hl", hopLimit)
        json.put("ht", hopsTraversed)
        json.put("pt", packetType)
        json.put("ep", encryptedPayload)
        json.put("ad", authenticationData)
        return json.toString()
    }

    /**
     * Decrements hop limit and increments traversed hops for relay forwarding.
     */
    fun createRelayCopy(): MeshPacket? {
        if (hopLimit <= 1) return null // Drop packet if hop limit exhausted
        if (System.currentTimeMillis() > expiresAt) return null // Drop packet if expired
        return copy(
            hopLimit = hopLimit - 1,
            hopsTraversed = hopsTraversed + 1
        )
    }

    /**
     * Checks if this packet is valid and not expired.
     */
    fun isValid(): Boolean {
        if (protocolVersion != 1) return false
        if (messageId.isBlank() || destinationRoutingId.isBlank() || originRoutingId.isBlank()) return false
        if (hopLimit < 0 || hopLimit > 32) return false
        if (System.currentTimeMillis() > expiresAt) return false
        return true
    }
}
