package com.example.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard, secure cryptographic engine for BitCall.
 * Uses NIST P-256 (secp256r1) with hardware Keystore protection where available,
 * ECDH key agreement, HKDF-SHA256, AES-256-GCM authenticated encryption,
 * and PBKDF2WithHmacSHA256 for password-based protection.
 */
object CryptoEngine {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val EC_CURVE = "secp256r1"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    // ---------------------------------------------------------------------------------------------
    // KEY GENERATION & KEYSTORE MANAGEMENT
    // ---------------------------------------------------------------------------------------------

    /**
     * Generates a new hardware-backed or software-backed EC KeyPair for BitCall Identity.
     */
    fun generateIdentityKeyPair(alias: String): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(parameterSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Fallback standard EC keypair generation for peer sessions and testing.
     */
    fun generateEphemeralKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        return kpg.generateKeyPair()
    }

    fun getPublicKeyFromKeystore(alias: String): PublicKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = keyStore.getCertificate(alias) ?: return null
        return cert.publicKey
    }

    fun getPrivateKeyFromKeystore(alias: String): PrivateKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? PrivateKey
    }

    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun decodePublicKey(base64String: String): PublicKey {
        val keyBytes = Base64.decode(base64String, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(spec)
    }

    // ---------------------------------------------------------------------------------------------
    // FINGERPRINT GENERATION
    // ---------------------------------------------------------------------------------------------

    /**
     * Computes a SHA-256 fingerprint formatted into 8 hex pairs (e.g., "7A:B4:8C:...").
     */
    fun computeFingerprint(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyBytes)
        return hash.take(16).joinToString(":") { "%02X".format(it) }
    }

    fun computeFingerprint(publicKey: PublicKey): String {
        return computeFingerprint(publicKey.encoded)
    }

    // ---------------------------------------------------------------------------------------------
    // KEY AGREEMENT (ECDH) & HKDF KEY DERIVATION
    // ---------------------------------------------------------------------------------------------

    /**
     * Performs ECDH to compute a shared secret.
     */
    fun computeSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * Derives an AES-256 symmetric key using HKDF-SHA256 from a shared secret and salt.
     */
    fun deriveAesKey(sharedSecret: ByteArray, salt: ByteArray, info: ByteArray = "BitCall-SessionKey".toByteArray()): SecretKey {
        // HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
        val mac = Mac.getInstance("HmacSHA256")
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(sharedSecret)

        // HKDF-Expand: OKM = HMAC-Hash(PRK, info || 0x01)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        val okm = mac.doFinal()

        // 256-bit AES key
        val keyBytes = okm.copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }

    // ---------------------------------------------------------------------------------------------
    // AUTHENTICATED ENCRYPTION (AES-256-GCM)
    // ---------------------------------------------------------------------------------------------

    data class EncryptedBundle(val ciphertextBase64: String, val ivBase64: String)

    fun encryptAesGcm(plaintext: ByteArray, key: SecretKey, aad: ByteArray? = null): EncryptedBundle {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        if (aad != null && aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedBundle(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decryptAesGcm(ciphertextBase64: String, ivBase64: String, key: SecretKey, aad: ByteArray? = null): ByteArray {
        val ciphertext = Base64.decode(ciphertextBase64, Base64.DEFAULT)
        val iv = Base64.decode(ivBase64, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        if (aad != null && aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(ciphertext)
    }

    // ---------------------------------------------------------------------------------------------
    // TRAFFIC ANALYSIS DEFENSE & BUFFER ZEROIZATION
    // ---------------------------------------------------------------------------------------------

    /**
     * Pads payload to uniform block boundaries (e.g., 256 bytes) to conceal message length
     * against metadata and traffic analysis by intermediate mesh relay nodes.
     */
    fun padPayload(data: ByteArray, targetBlockSize: Int = 256): ByteArray {
        val padLen = targetBlockSize - (data.size % targetBlockSize)
        val padded = ByteArray(data.size + padLen)
        System.arraycopy(data, 0, padded, 0, data.size)
        padded.fill(padLen.toByte(), data.size, padded.size)
        return padded
    }

    /**
     * Removes uniform PKCS7-style padding with constant-time byte checks.
     */
    fun unpadPayload(padded: ByteArray): ByteArray {
        if (padded.isEmpty()) return padded
        val padLen = padded.last().toInt() and 0xFF
        if (padLen <= 0 || padLen > padded.size) return padded
        var isValid = 0
        for (i in (padded.size - padLen) until padded.size) {
            isValid = isValid or ((padded[i].toInt() and 0xFF) xor padLen)
        }
        return if (isValid == 0) {
            padded.copyOfRange(0, padded.size - padLen)
        } else {
            padded
        }
    }

    /**
     * Securely clears sensitive plaintext cryptographic arrays from memory.
     */
    fun wipeBytes(vararg arrays: ByteArray?) {
        for (arr in arrays) {
            arr?.fill(0)
        }
    }

    /**
     * Performs a Diffie-Hellman ratchet step to evolve the root key and derive a single-use message key
     * for continuous forward secrecy and post-compromise security.
     */
    fun ratchetStep(rootKey: ByteArray, ephemeralDHPair: KeyPair, peerPublicKey: PublicKey): Pair<ByteArray, SecretKey> {
        val dhSecret = computeSharedSecret(ephemeralDHPair.private, peerPublicKey)
        val nextRoot = deriveAesKey(dhSecret, salt = rootKey, info = "BitCall-NextRoot".toByteArray()).encoded
        val messageKey = deriveAesKey(dhSecret, salt = nextRoot, info = "BitCall-MessageKey".toByteArray())
        wipeBytes(dhSecret)
        return Pair(nextRoot, messageKey)
    }

    // ---------------------------------------------------------------------------------------------
    // DIGITAL SIGNATURES (SHA256withECDSA)
    // ---------------------------------------------------------------------------------------------

    fun signData(data: ByteArray, privateKey: PrivateKey): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    fun verifySignature(data: ByteArray, signatureBase64: String, publicKey: PublicKey): Boolean {
        return try {
            val sigBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PASSWORD HASHING & PIN PROTECTION (PBKDF2)
    // ---------------------------------------------------------------------------------------------

    data class PasswordHashResult(val hashBase64: String, val saltBase64: String)

    fun hashPassword(password: String, existingSalt: ByteArray? = null): PasswordHashResult {
        val salt = existingSalt ?: ByteArray(16).apply { secureRandom.nextBytes(this) }
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded
        return PasswordHashResult(
            hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verifyPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
        return try {
            val salt = Base64.decode(saltBase64, Base64.DEFAULT)
            val result = hashPassword(password, salt)
            MessageDigest.isEqual(
                Base64.decode(result.hashBase64, Base64.DEFAULT),
                Base64.decode(expectedHashBase64, Base64.DEFAULT)
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Derives a 256-bit AES encryption key from a contact protection password and salt.
     */
    fun deriveKeyFromPassword(password: String, saltBase64: String): SecretKey {
        val salt = Base64.decode(saltBase64, Base64.DEFAULT)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = skf.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts arbitrary local string data using a password.
     */
    fun encryptWithPassword(data: String, password: String, saltBase64: String): String {
        val key = deriveKeyFromPassword(password, saltBase64)
        val bundle = encryptAesGcm(data.toByteArray(Charsets.UTF_8), key)
        return "${bundle.ivBase64}:${bundle.ciphertextBase64}"
    }

    /**
     * Decrypts arbitrary local string data using a password.
     */
    fun decryptWithPassword(encryptedString: String, password: String, saltBase64: String): String {
        val parts = encryptedString.split(":")
        require(parts.size == 2) { "Malformed encrypted payload format" }
        val key = deriveKeyFromPassword(password, saltBase64)
        val decryptedBytes = decryptAesGcm(parts[1], parts[0], key)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun generateRandomSalt(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun generateRandomBitcallId(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return "BC-" + bytes.joinToString("") { "%02X".format(it) }
    }
}
