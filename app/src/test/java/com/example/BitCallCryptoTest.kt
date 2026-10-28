package com.example

import com.example.crypto.CryptoEngine
import com.example.crypto.QrCodeUtil
import com.example.mesh.MeshPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BitCallCryptoTest {

    @Test
    fun testKeyPairAndFingerprint() {
        val keyPair = CryptoEngine.generateEphemeralKeyPair()
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)

        val fp = CryptoEngine.computeFingerprint(keyPair.public)
        assertTrue(fp.contains(":"))
        assertEquals(16, fp.split(":").size)
    }

    @Test
    fun testEcdhAndAesGcmRoundtrip() {
        val aliceKeys = CryptoEngine.generateEphemeralKeyPair()
        val bobKeys = CryptoEngine.generateEphemeralKeyPair()

        // Alice computes shared secret using Bob's public key
        val secretAlice = CryptoEngine.computeSharedSecret(aliceKeys.private, bobKeys.public)
        // Bob computes shared secret using Alice's public key
        val secretBob = CryptoEngine.computeSharedSecret(bobKeys.private, aliceKeys.public)

        val salt = "test-salt-12345".toByteArray()
        val keyAlice = CryptoEngine.deriveAesKey(secretAlice, salt)
        val keyBob = CryptoEngine.deriveAesKey(secretBob, salt)

        val plaintext = "Hello from BitCall secure mesh!"
        val bundle = CryptoEngine.encryptAesGcm(plaintext.toByteArray(Charsets.UTF_8), keyAlice)

        val decryptedBytes = CryptoEngine.decryptAesGcm(bundle.ciphertextBase64, bundle.ivBase64, keyBob)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals(plaintext, decryptedText)
    }

    @Test
    fun testPbkdf2PasswordHashing() {
        val pin = "8492"
        val result = CryptoEngine.hashPassword(pin)

        assertTrue(CryptoEngine.verifyPassword("8492", result.saltBase64, result.hashBase64))
        assertFalse(CryptoEngine.verifyPassword("0000", result.saltBase64, result.hashBase64))
    }

    @Test
    fun testContactPasswordProtection() {
        val secretData = "Super confidential conversation message"
        val password = "StrongContactPassword!#1"
        val salt = CryptoEngine.generateRandomSalt()

        val encrypted = CryptoEngine.encryptWithPassword(secretData, password, salt)
        val decrypted = CryptoEngine.decryptWithPassword(encrypted, password, salt)

        assertEquals(secretData, decrypted)
    }

    @Test
    fun testMeshPacketSerializationAndRelay() {
        val packet = MeshPacket(
            messageId = "msg-001",
            destinationRoutingId = "BC-BOB-01",
            originRoutingId = "BC-ALICE-01",
            hopLimit = 5,
            packetType = MeshPacket.TYPE_CHAT_MESSAGE,
            encryptedPayload = "encrypted-bytes",
            authenticationData = "sig-123"
        )

        val json = packet.toJson()
        val deserialized = MeshPacket.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(packet.messageId, deserialized!!.messageId)
        assertEquals(packet.hopLimit, deserialized.hopLimit)

        val relayCopy = packet.createRelayCopy()
        assertNotNull(relayCopy)
        assertEquals(4, relayCopy!!.hopLimit)
        assertEquals(1, relayCopy.hopsTraversed)
    }

    @Test
    fun testQrPayloadSerialization() {
        val payload = QrCodeUtil.ContactQrPayload(
            bitcallId = "BC-987654",
            displayName = "Charlie",
            publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
            fingerprint = "AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00"
        )

        val json = payload.toJson()
        val parsed = QrCodeUtil.ContactQrPayload.fromJson(json)

        assertNotNull(parsed)
        assertEquals(payload.bitcallId, parsed!!.bitcallId)
        assertEquals(payload.displayName, parsed.displayName)
        assertEquals(payload.fingerprint, parsed.fingerprint)
    }

    @Test
    fun testContactEntityEncryptionMetadata() {
        val contact = com.example.data.local.ContactEntity(
            bitcallId = "BC-TEST-01",
            displayName = "Alice Mesh Node",
            phoneNumber = "+1-555-0199",
            publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
            fingerprint = "AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00",
            keyAlgorithm = "ECDH-P256",
            encryptionScheme = "AES-256-GCM",
            keyVersion = 1,
            sessionCounter = 42L,
            lastHandshakeTimestamp = 1700000000L,
            hopLimitPreference = 7,
            meshRoutingTrust = 2
        )

        assertEquals("ECDH-P256", contact.keyAlgorithm)
        assertEquals("AES-256-GCM", contact.encryptionScheme)
        assertEquals(1, contact.keyVersion)
        assertEquals(42L, contact.sessionCounter)
        assertEquals(7, contact.hopLimitPreference)
        assertEquals(2, contact.meshRoutingTrust)
    }

    @Test
    fun testTrafficAnalysisPaddingAndUnpadding() {
        val original = "Secret mesh message payload".toByteArray(Charsets.UTF_8)
        val padded = CryptoEngine.padPayload(original, targetBlockSize = 256)

        assertEquals(256, padded.size)
        val unpadded = CryptoEngine.unpadPayload(padded)
        assertEquals("Secret mesh message payload", String(unpadded, Charsets.UTF_8))
    }

    @Test
    fun testBufferZeroization() {
        val secret = byteArrayOf(1, 2, 3, 4, 5)
        CryptoEngine.wipeBytes(secret)
        assertTrue(secret.all { it == 0.toByte() })
    }

    @Test
    fun testRatchetStepDerivation() {
        val rootKey = ByteArray(32) { 1 }
        val aliceDH = CryptoEngine.generateEphemeralKeyPair()
        val bobDH = CryptoEngine.generateEphemeralKeyPair()

        val (nextRootAlice, msgKeyAlice) = CryptoEngine.ratchetStep(rootKey, aliceDH, bobDH.public)
        val (nextRootBob, msgKeyBob) = CryptoEngine.ratchetStep(rootKey, bobDH, aliceDH.public)

        assertEquals(32, nextRootAlice.size)
        assertEquals(32, nextRootBob.size)
        // Both derive the identical root key and message encryption key
        assertTrue(nextRootAlice.contentEquals(nextRootBob))
        assertTrue(msgKeyAlice.encoded.contentEquals(msgKeyBob.encoded))
    }
}
