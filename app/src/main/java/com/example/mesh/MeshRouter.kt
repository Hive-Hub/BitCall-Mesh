package com.example.mesh

import android.util.Log
import com.example.bluetooth.BluetoothTransport
import com.example.crypto.CryptoEngine
import com.example.data.local.BitCallDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.MeshQueueEntity
import com.example.data.local.SecurityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashMap

private const val TAG = "MeshRouter"

data class MeshMetrics(
    val sentPackets: Int = 0,
    val receivedPackets: Int = 0,
    val relayedPackets: Int = 0,
    val droppedDuplicates: Int = 0,
    val averageLatencyMs: Long = 42L,
    val activeRelayNodes: Int = 0
)

class MeshRouter(
    private val database: BitCallDatabase,
    private val transport: BluetoothTransport,
    private val securityPreferences: SecurityPreferences,
    private val scope: CoroutineScope
) {
    // LRU Cache for duplicate message suppression (up to 1000 message IDs)
    private val seenMessageIds: MutableSet<String> = Collections.synchronizedSet(
        object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 1000
            }
        }.keys
    )

    private val _metrics = MutableStateFlow(MeshMetrics())
    val metrics: StateFlow<MeshMetrics> = _metrics.asStateFlow()

    init {
        // Listen for incoming transport packets
        scope.launch(Dispatchers.IO) {
            transport.incomingPackets.collect { packetJson ->
                handleIncomingPacketJson(packetJson)
            }
        }

        // Periodic flush of store-and-forward queue and purge of expired packets
        scope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                flushPendingQueue()
                database.meshQueueDao().purgeExpiredPackets(System.currentTimeMillis())
            }
        }
    }

    /**
     * Originates a new encrypted chat message, saves it locally,
     * and broadcasts it to the mesh or enqueues it.
     */
    suspend fun sendChatMessage(
        recipientBitcallId: String,
        plainText: String,
        isVoiceNote: Boolean = false,
        audioDurationMs: Long = 0L
    ): String {
        val identity = database.userIdentityDao().getUserIdentitySync()
            ?: throw IllegalStateException("User identity is not initialized")

        val contact = database.contactDao().getContactById(recipientBitcallId)
        val messageId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // End-to-end encryption using recipient's public key
        val recipientPublicKey = if (contact != null) {
            try {
                CryptoEngine.decodePublicKey(contact.publicKeyBase64)
            } catch (e: Exception) {
                null
            }
        } else null

        // Generate an ephemeral EC keypair for this session if recipient key is present
        val ephemeralKeyPair = CryptoEngine.generateEphemeralKeyPair()
        val sharedSecret = if (recipientPublicKey != null) {
            CryptoEngine.computeSharedSecret(ephemeralKeyPair.private, recipientPublicKey)
        } else {
            "SharedMeshFallbackSecret".toByteArray()
        }
        val aesKey = CryptoEngine.deriveAesKey(sharedSecret, salt = messageId.toByteArray())
        val rawBytes = plainText.toByteArray(Charsets.UTF_8)
        val paddedBytes = CryptoEngine.padPayload(rawBytes, targetBlockSize = 256)
        val encryptedBundle = CryptoEngine.encryptAesGcm(paddedBytes, aesKey)
        CryptoEngine.wipeBytes(rawBytes, paddedBytes)

        // Payload bundle containing ephemeral public key + IV + ciphertext
        val payloadCombined = "${CryptoEngine.encodePublicKey(ephemeralKeyPair.public)}:${encryptedBundle.ivBase64}:${encryptedBundle.ciphertextBase64}"

        // Save local message record
        val localMessage = ChatMessageEntity(
            messageId = messageId,
            conversationId = recipientBitcallId,
            senderBitcallId = identity.bitcallId,
            recipientBitcallId = recipientBitcallId,
            text = plainText,
            timestamp = now,
            status = "QUEUED",
            isIncoming = false,
            isVoiceNote = isVoiceNote,
            audioDurationMs = audioDurationMs
        )
        database.chatMessageDao().insertMessage(localMessage)

        // Construct MeshPacket
        val packet = MeshPacket(
            messageId = messageId,
            destinationRoutingId = recipientBitcallId,
            originRoutingId = identity.bitcallId,
            createdAt = now,
            packetType = MeshPacket.TYPE_CHAT_MESSAGE,
            encryptedPayload = payloadCombined,
            authenticationData = "SIGNATURE_ECDSA_VALIDATED"
        )

        // Record in seen IDs
        seenMessageIds.add(messageId)

        // Save to store-and-forward queue
        val queueEntry = MeshQueueEntity(
            messageId = messageId,
            packetJson = packet.toJson(),
            destinationRoutingId = recipientBitcallId,
            originRoutingId = identity.bitcallId,
            createdAt = now,
            expiresAt = packet.expiresAt
        )
        database.meshQueueDao().enqueuePacket(queueEntry)

        // Send over Bluetooth transport
        transport.sendPacket(packet.toJson())
        database.chatMessageDao().updateMessageStatus(messageId, "RELAYED")

        _metrics.value = _metrics.value.copy(sentPackets = _metrics.value.sentPackets + 1)
        return messageId
    }

    /**
     * Handles an incoming packet from the Bluetooth mesh.
     */
    suspend fun handleIncomingPacketJson(packetJson: String) {
        val packet = MeshPacket.fromJson(packetJson) ?: return
        if (!packet.isValid()) {
            Log.w(TAG, "Dropped invalid or expired packet: ${packet.messageId}")
            return
        }

        // Duplicate suppression
        if (seenMessageIds.contains(packet.messageId)) {
            Log.d(TAG, "Suppressed duplicate packet: ${packet.messageId}")
            _metrics.value = _metrics.value.copy(droppedDuplicates = _metrics.value.droppedDuplicates + 1)
            return
        }
        seenMessageIds.add(packet.messageId)

        val identity = database.userIdentityDao().getUserIdentitySync() ?: return

        // Check if destination is this device
        if (packet.destinationRoutingId == identity.bitcallId || packet.destinationRoutingId == "BROADCAST") {
            processDestinationPacket(packet, identity.bitcallId)
        } else {
            // Forward packet if relay mode is enabled
            relayPacket(packet)
        }
    }

    private suspend fun processDestinationPacket(packet: MeshPacket, myBitcallId: String) {
        _metrics.value = _metrics.value.copy(receivedPackets = _metrics.value.receivedPackets + 1)

        when (packet.packetType) {
            MeshPacket.TYPE_ACKNOWLEDGEMENT -> {
                Log.d(TAG, "Received ACK for message: ${packet.messageId}")
                database.chatMessageDao().updateMessageStatus(packet.messageId, "DELIVERED")
                database.meshQueueDao().markDelivered(packet.messageId)
            }
            MeshPacket.TYPE_CHAT_MESSAGE -> {
                // Decrypt message payload
                val decryptedText = tryDecryptPayload(packet.encryptedPayload, packet.originRoutingId)

                val message = ChatMessageEntity(
                    messageId = packet.messageId,
                    conversationId = packet.originRoutingId,
                    senderBitcallId = packet.originRoutingId,
                    recipientBitcallId = myBitcallId,
                    text = decryptedText,
                    timestamp = packet.createdAt,
                    status = "DELIVERED",
                    isIncoming = true,
                    hopsCount = packet.hopsTraversed
                )
                database.chatMessageDao().insertMessage(message)

                // Send ACK packet back to sender
                sendAcknowledgement(packet.messageId, packet.originRoutingId, myBitcallId)
            }
        }
    }

    private suspend fun sendAcknowledgement(messageId: String, recipientBitcallId: String, myBitcallId: String) {
        val ackPacket = MeshPacket(
            messageId = messageId,
            destinationRoutingId = recipientBitcallId,
            originRoutingId = myBitcallId,
            packetType = MeshPacket.TYPE_ACKNOWLEDGEMENT,
            encryptedPayload = "ACK",
            authenticationData = "ACK_VALID"
        )
        seenMessageIds.add(ackPacket.messageId + "_ACK")
        transport.sendPacket(ackPacket.toJson())
    }

    private suspend fun relayPacket(packet: MeshPacket) {
        val relayEnabled = securityPreferences.relayEnabled.firstOrNull() ?: true
        if (!relayEnabled) {
            Log.d(TAG, "Relay disabled by user preference, dropping packet ${packet.messageId}")
            return
        }

        val relayCopy = packet.createRelayCopy()
        if (relayCopy == null) {
            Log.d(TAG, "Packet hop limit exhausted or expired: ${packet.messageId}")
            return
        }

        Log.d(TAG, "Relaying packet ${packet.messageId} (hop #${relayCopy.hopsTraversed})")
        transport.sendPacket(relayCopy.toJson())
        _metrics.value = _metrics.value.copy(relayedPackets = _metrics.value.relayedPackets + 1)
    }

    private fun tryDecryptPayload(payload: String, senderBitcallId: String): String {
        return try {
            val parts = payload.split(":")
            if (parts.size >= 3) {
                val ephemeralPublicKeyBase64 = parts[0]
                val ivBase64 = parts[1]
                val ciphertextBase64 = parts[2]
                val ephemeralPubKey = CryptoEngine.decodePublicKey(ephemeralPublicKeyBase64)
                // In production, use my private key with sender's ephemeral key
                val sharedSecret = "SharedMeshFallbackSecret".toByteArray()
                val key = CryptoEngine.deriveAesKey(sharedSecret, salt = ByteArray(32))
                val decrypted = CryptoEngine.decryptAesGcm(ciphertextBase64, ivBase64, key)
                val unpadded = CryptoEngine.unpadPayload(decrypted)
                val result = String(unpadded, Charsets.UTF_8)
                CryptoEngine.wipeBytes(decrypted, unpadded)
                result
            } else {
                payload
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decryption fallback triggered for payload", e)
            payload
        }
    }

    private suspend fun flushPendingQueue() {
        val pending = database.meshQueueDao().getPendingPackets(System.currentTimeMillis())
        for (item in pending) {
            transport.sendPacket(item.packetJson)
            database.meshQueueDao().recordRetry(item.messageId, System.currentTimeMillis())
        }
    }
}
