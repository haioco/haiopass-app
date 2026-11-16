package com.haio.bypass.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class DeviceCryptoTest {

    private val aesKey = SecretKeySpec(
        ByteArray(32).also { SecureRandom().nextBytes(it) },
        "AES"
    )

    @Test
    fun `generated key is EC P-256 and exports SPKI PEM`() {
        val generated = DeviceCrypto.generateKeyPair()
        assertEquals("EC", generated.keyPair.private.algorithm)
        assertTrue(generated.publicKeyPem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(generated.publicKeyPem.trim().endsWith("-----END PUBLIC KEY-----"))
        assertTrue(DeviceCrypto.isValidPublicKeyPem(generated.publicKeyPem))
    }

    @Test
    fun `private PKCS8 survives AES-GCM round trip`() {
        val generated = DeviceCrypto.generateKeyPair()
        val pkcs8 = DeviceCrypto.privateKeyPkcs8(generated.keyPair.private)

        val encrypted = DeviceCrypto.encrypt(pkcs8, aesKey)
        assertFalse(encrypted.ciphertextBase64.isBlank())
        assertFalse(encrypted.ivBase64.isBlank())

        val decrypted = DeviceCrypto.decrypt(encrypted, aesKey)
        assertTrue(pkcs8.contentEquals(decrypted))
        assertEquals(
            generated.keyPair.private.encoded.toList(),
            DeviceCrypto.loadPrivateKeyPkcs8(decrypted).encoded.toList()
        )
    }

    @Test
    fun `every encryption uses a fresh IV`() {
        val generated = DeviceCrypto.generateKeyPair()
        val pkcs8 = DeviceCrypto.privateKeyPkcs8(generated.keyPair.private)
        val first = DeviceCrypto.encrypt(pkcs8, aesKey)
        val second = DeviceCrypto.encrypt(pkcs8, aesKey)
        assertFalse(first.ivBase64 == second.ivBase64)
        assertFalse(first.ciphertextBase64 == second.ciphertextBase64)
    }

    @Test(expected = AEADBadTagException::class)
    fun `tampered ciphertext is rejected`() {
        val generated = DeviceCrypto.generateKeyPair()
        val pkcs8 = DeviceCrypto.privateKeyPkcs8(generated.keyPair.private)
        val encrypted = DeviceCrypto.encrypt(pkcs8, aesKey)
        val bytes = java.util.Base64.getDecoder().decode(encrypted.ciphertextBase64)
        bytes[0] = (bytes[0].toInt() xor 0xFF).toByte()
        DeviceCrypto.decrypt(
            encrypted.copy(ciphertextBase64 = java.util.Base64.getEncoder().encodeToString(bytes)),
            aesKey
        )
    }

    @Test
    fun `certificate fingerprint is stable lowercase sha256`() {
        val fingerprint = DeviceCrypto.certificateFingerprint(TEST_CERTIFICATE)
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in "0123456789abcdef" })
        assertEquals(fingerprint, DeviceCrypto.certificateFingerprint(TEST_CERTIFICATE))
        assertNotNull(DeviceCrypto.certificateExpiryMillis(TEST_CERTIFICATE))
        assertTrue(DeviceCrypto.certificateExpiryMillis(TEST_CERTIFICATE)!! > 0)
    }

    @Test
    fun `certificate public key matches itself`() {
        val cert = DeviceCrypto.parseCertificate(TEST_CERTIFICATE)
        assertTrue(DeviceCrypto.certificateMatchesPublicKey(TEST_CERTIFICATE, cert.publicKey))
        val other = DeviceCrypto.generateKeyPair()
        assertFalse(
            DeviceCrypto.certificateMatchesPublicKey(TEST_CERTIFICATE, other.keyPair.public)
        )
    }

    @Test
    fun `PEM decode ignores line breaks and headers`() {
        val generated = DeviceCrypto.generateKeyPair()
        val der = DeviceCrypto.decodePem(generated.publicKeyPem)
        assertEquals(generated.keyPair.public.encoded.toList(), der.toList())
    }

    companion object {
        /**
         * Throwaway P-256 self-signed certificate generated for this test with
         * `openssl req -x509 -days 3650`. Public test data only.
         */
        private const val TEST_CERTIFICATE = """-----BEGIN CERTIFICATE-----
MIIBijCCATGgAwIBAgIUOKYM9+LpiDb9sOKLTkMncvFEHBEwCgYIKoZIzj0EAwIw
GzEZMBcGA1UEAwwQaGFpby10ZXN0LWRldmljZTAeFw0yNjExMTUxOTM2MzlaFw0z
NjExMTIxOTM2MzlaMBsxGTAXBgNVBAMMEGhhaW8tdGVzdC1kZXZpY2UwWTATBgcq
hkjOPQIBBggqhkjOPQMBBwNCAARM4c0h2lKvl1YshptDxKoWBy8x9Xnk+vX1+JjW
U2Xf9iar351cUzBDRjCLr45hCNKi9HQsB24QfdLkEt83U8blo1MwUTAdBgNVHQ4E
FgQU97tv73SzFi7OFm1/fuxs6N5TnrEwHwYDVR0jBBgwFoAU97tv73SzFi7OFm1/
fuxs6N5TnrEwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNHADBEAiBvwd2G
c1vo4+Iw6T+TZ8rQJTmlk338D9MUGpf1hpMTqAIgcoqmp+J1E32XHajQmD2zhUZR
wHiGOmEfh9u0W6PuIHk=
-----END CERTIFICATE-----
"""
    }
}
