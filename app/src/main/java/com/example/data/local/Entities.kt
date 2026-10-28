package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_identity")
data class UserIdentityEntity(
    @PrimaryKey val bitcallId: String,
    val displayName: String,
    val publicKeyBase64: String,
    val keyStoreAlias: String,
    val fingerprint: String,
    val createdAt: Long,
    val isConfigured: Boolean
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val bitcallId: String,
    val displayName: String,
    val phoneNumber: String? = null,
    val publicKeyBase64: String,
    val fingerprint: String,
    val isVerified: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastSeen: Long? = null,
    // Encryption & Mesh Network Metadata
    val keyAlgorithm: String = "ECDH-P256",
    val encryptionScheme: String = "AES-256-GCM",
    val keyVersion: Int = 1,
    val sessionCounter: Long = 0L,
    val lastHandshakeTimestamp: Long = 0L,
    val hopLimitPreference: Int = 7,
    val meshRoutingTrust: Int = 1, // 0 = Untrusted, 1 = Direct Peer, 2 = Verified Multi-Hop Peer
    // Local Access Protection
    val isProtected: Boolean = false,
    val passwordSalt: String? = null,
    val passwordHash: String? = null,
    val isBlocked: Boolean = false
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val senderBitcallId: String,
    val recipientBitcallId: String,
    val text: String,
    val timestamp: Long,
    val status: String, // QUEUED, SENDING, RELAYED, DELIVERED, FAILED
    val isIncoming: Boolean,
    val isVoiceNote: Boolean = false,
    val audioDurationMs: Long = 0L,
    val hopsCount: Int = 0
)

@Entity(tableName = "mesh_queue")
data class MeshQueueEntity(
    @PrimaryKey val messageId: String,
    val packetJson: String,
    val destinationRoutingId: String,
    val originRoutingId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val retryCount: Int = 0,
    val lastAttemptAt: Long = 0L,
    val isDelivered: Boolean = false
)

@Entity(tableName = "device_peers")
data class DevicePeerEntity(
    @PrimaryKey val deviceAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val lastSeen: Long,
    val isConnected: Boolean,
    val hopDistance: Int = 1,
    val bitcallId: String? = null
)

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey val callId: String,
    val peerBitcallId: String,
    val peerDisplayName: String,
    val callType: String, // INCOMING, OUTGOING, MISSED
    val durationSeconds: Int,
    val timestamp: Long,
    val qualityRating: Int = 5 // 1-5 scale
)
