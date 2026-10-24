package com.cns.accounts.data.sendly

import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Sendly's device-key crypto, byte for byte what its web client (`web/static/js/crypto.js`)
 * does with WebCrypto, so this app can stand in for a trusted browser:
 *
 * - device keys are RSA-OAEP-2048 with SHA-256 (MGF1-SHA256), public key sent as a JWK;
 * - the identity private key travels as `RSA-OAEP-2048+AES-GCM-256-v1`: a random AES-256 key
 *   wrapped with RSA-OAEP (256 bytes), then a 12-byte IV, then the AES-GCM ciphertext of the
 *   identity private JWK as JSON.
 *
 * The device key is generated in software, not in the Keystore: Keystore's RSA-OAEP only does
 * MGF1-SHA1 before Android 14, which can't open WebCrypto's envelopes. Its private half is
 * stored encrypted under a Keystore key instead (see [SendlyLink]).
 */
object SendlyCrypto {
    const val DeviceKeyAlgorithm = "RSA-OAEP-2048"
    const val IdentityWrapAlg = "RSA-OAEP-2048+AES-GCM-256-v1"

    private const val WrappedKeyLength = 256
    private const val IvLength = 12
    private val random = SecureRandom()
    private val oaepSha256 = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

    fun generateDeviceKey(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, random) }.generateKeyPair()

    /** The public key as WebCrypto exports an RSA-OAEP/SHA-256 key. */
    fun publicJwk(key: PublicKey): JSONObject {
        val rsa = key as RSAPublicKey
        return JSONObject()
            .put("kty", "RSA")
            .put("alg", "RSA-OAEP-256")
            .put("e", b64url(rsa.publicExponent))
            .put("n", b64url(rsa.modulus))
            .put("ext", true)
            .put("key_ops", org.json.JSONArray().put("encrypt"))
    }

    fun encodePrivateKey(key: PrivateKey): String = Base64.getEncoder().encodeToString(key.encoded)

    fun decodePrivateKey(encoded: String): PrivateKey =
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)))

    /** Wraps the identity private JWK ([identityJwk], JSON) for a device's public JWK. */
    fun wrapIdentityKey(identityJwk: String, devicePublicJwk: JSONObject): ByteArray {
        val aesKey = KeyGenerator.getInstance("AES").apply { init(256, random) }.generateKey()
        val wrappedKey = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.ENCRYPT_MODE, publicKeyFromJwk(devicePublicJwk), oaepSha256)
            doFinal(aesKey.encoded)
        }
        val iv = ByteArray(IvLength).also(random::nextBytes)
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            doFinal(identityJwk.toByteArray(Charsets.UTF_8))
        }
        return wrappedKey + iv + ciphertext
    }

    /** Opens an identity-key envelope addressed to this device; returns the identity private JWK as JSON. */
    fun unwrapIdentityKey(envelope: ByteArray, devicePrivateKey: PrivateKey): String {
        require(envelope.size > WrappedKeyLength + IvLength) { "identity key envelope too short" }
        val rawAesKey = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.DECRYPT_MODE, devicePrivateKey, oaepSha256)
            doFinal(envelope, 0, WrappedKeyLength)
        }
        val plaintext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(rawAesKey, "AES"), GCMParameterSpec(128, envelope, WrappedKeyLength, IvLength))
            doFinal(envelope, WrappedKeyLength + IvLength, envelope.size - WrappedKeyLength - IvLength)
        }
        return String(plaintext, Charsets.UTF_8)
    }

    fun publicKeyFromJwk(jwk: JSONObject): PublicKey {
        require(jwk.optString("kty") == "RSA") { "not an RSA key" }
        val spec = RSAPublicKeySpec(BigInteger(1, b64urlDecode(jwk.getString("n"))), BigInteger(1, b64urlDecode(jwk.getString("e"))))
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    private fun b64url(value: BigInteger): String {
        // Unsigned big-endian, as JWK wants: drop BigInteger's sign byte.
        val bytes = value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun b64urlDecode(value: String): ByteArray = Base64.getUrlDecoder().decode(value.trimEnd('='))
}
