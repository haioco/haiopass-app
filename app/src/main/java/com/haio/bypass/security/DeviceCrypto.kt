package com.haio.bypass.security

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Pure-JVM crypto helpers for the HaioGate device identity.
 *
 * Kept free of Android classes so the key/certificate round trip can be unit
 * tested on the JVM. The Android Keystore only supplies the AES-256-GCM
 * wrapping key; all data-plane crypto lives here.
 *
 * Security notes:
 *  - EC P-256 (secp256r1) software key, generated on the device and never
 *    uploaded. Only the SPKI public key leaves the device.
 *  - PKCS#8 private bytes are AES-GCM encrypted at rest; a fresh random IV is
 *    used for every encryption (`setRandomizedEncryptionRequired(true)` on the
 *    Keystore key).
 *  - No method in this object logs key material.
 */
object DeviceCrypto {

    const val EC_CURVE = "secp256r1"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    data class EncryptedPayload(
        val ciphertextBase64: String,
        val ivBase64: String
    )

    data class GeneratedKeyPair(
        val keyPair: KeyPair,
        /** PEM `PUBLIC KEY` (SPKI) sent to the enrollment endpoint. */
        val publicKeyPem: String
    )

    fun generateKeyPair(): GeneratedKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(EC_CURVE), SecureRandom())
        val keyPair = generator.generateKeyPair()
        return GeneratedKeyPair(
            keyPair = keyPair,
            publicKeyPem = publicKeyPem(keyPair.public)
        )
    }

    fun publicKeyPem(publicKey: PublicKey): String = encodePem("PUBLIC KEY", publicKey.encoded)

    fun privateKeyPkcs8(privateKey: PrivateKey): ByteArray = privateKey.encoded

    fun loadPrivateKeyPkcs8(bytes: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))

    fun encrypt(
        privateKeyPkcs8: ByteArray,
        wrappingKey: SecretKey,
        letProviderChooseIv: Boolean = false
    ): EncryptedPayload {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val iv: ByteArray
        if (letProviderChooseIv) {
            // Android Keystore keys created with
            // setRandomizedEncryptionRequired(true) reject a caller-supplied
            // IV; the provider generates and exposes one instead.
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
            iv = cipher.iv
        } else {
            iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(privateKeyPkcs8)
        return EncryptedPayload(
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
            ivBase64 = Base64.getEncoder().encodeToString(iv)
        )
    }

    fun decrypt(payload: EncryptedPayload, wrappingKey: SecretKey): ByteArray {
        val ciphertext = Base64.getDecoder().decode(payload.ciphertextBase64)
        val iv = Base64.getDecoder().decode(payload.ivBase64)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /** SHA-256 of the DER leaf, lowercase hex — the server's binding value. */
    fun certificateFingerprint(certificatePem: String): String {
        val cert = parseCertificate(certificatePem)
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun certificateExpiryMillis(certificatePem: String): Long? =
        runCatching { parseCertificate(certificatePem).notAfter.time }.getOrNull()

    /** Guards against a CA/server bug that signs a different public key. */
    fun certificateMatchesPublicKey(certificatePem: String, publicKey: PublicKey): Boolean =
        runCatching {
            val certKey = parseCertificate(certificatePem).publicKey
            if (certKey is java.security.interfaces.ECPublicKey &&
                publicKey is java.security.interfaces.ECPublicKey
            ) {
                // Compare the curve point, not the DER encoding: providers can
                // encode named curves differently while the key is identical.
                certKey.w.affineX == publicKey.w.affineX &&
                    certKey.w.affineY == publicKey.w.affineY
            } else {
                certKey.encoded.contentEquals(publicKey.encoded)
            }
        }.getOrDefault(false)

    fun certificateExpiryIso(certificatePem: String): String? =
        certificateExpiryMillis(certificatePem)?.let {
            java.time.Instant.ofEpochMilli(it).toString()
        }

    fun parseCertificate(certificatePem: String): X509Certificate {
        val der = decodePem(certificatePem)
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream()) as X509Certificate
    }

    /** Parses a concatenated PEM CA chain (one or more certificates). */
    fun parseCertificateChain(chainPem: String): List<X509Certificate> {
        if (chainPem.isBlank()) return emptyList()
        return CertificateFactory.getInstance("X.509")
            .generateCertificates(chainPem.byteInputStream())
            .mapNotNull { it as? X509Certificate }
    }

    fun isValidPublicKeyPem(pem: String): Boolean = runCatching {
        val der = decodePem(pem)
        KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(der)
        )
        true
    }.getOrDefault(false)

    fun encodePem(label: String, der: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(der)
        val body = base64.chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
    }

    fun decodePem(pem: String): ByteArray {
        val body = pem
            .replace("\r", "")
            .lineSequence()
            .filterNot { it.startsWith("-----") || it.isBlank() }
            .joinToString("")
        return Base64.getDecoder().decode(body)
    }
}
