package com.cns.accounts.data.sendly

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Base64

class SendlyCryptoTest {
    // Made by Node's WebCrypto exactly as Sendly's web/static/js/crypto.js wraps an identity key
    // for a device (test-only keys).
    private val fixture = JSONObject(javaClass.classLoader!!.getResource("sendly_envelope_fixture.json")!!.readText())

    @Test
    fun opensAnEnvelopeFromTheWebClient() {
        val deviceKey = SendlyCrypto.decodePrivateKey(fixture.getString("device_private_pkcs8"))
        val envelope = Base64.getDecoder().decode(fixture.getString("envelope_b64"))
        val jwk = SendlyCrypto.unwrapIdentityKey(envelope, deviceKey)
        assertEquals(JSONObject(fixture.getString("identity_jwk")).toString(), JSONObject(jwk).toString())
    }

    @Test
    fun wrapsForAWebCryptoPublicKeyAndRoundTrips() {
        val device = SendlyCrypto.generateDeviceKey()
        val jwk = SendlyCrypto.publicJwk(device.public)
        assertEquals("RSA-OAEP-256", jwk.getString("alg"))
        assertEquals("AQAB", jwk.getString("e"))
        // 2048-bit modulus, unsigned: 256 bytes, no sign byte.
        assertEquals(256, Base64.getUrlDecoder().decode(jwk.getString("n")).size)
        val identity = fixture.getString("identity_jwk")
        val envelope = SendlyCrypto.wrapIdentityKey(identity, jwk)
        assertEquals(identity, SendlyCrypto.unwrapIdentityKey(envelope, device.private))
    }

    @Test
    fun wrapsForTheWebDevicesPublicJwk() {
        // The fixture's device public key is a WebCrypto export; its private half opens our envelope.
        val envelope = SendlyCrypto.wrapIdentityKey("{\"kty\":\"RSA\"}", fixture.getJSONObject("device_public_jwk"))
        val opened = SendlyCrypto.unwrapIdentityKey(envelope, SendlyCrypto.decodePrivateKey(fixture.getString("device_private_pkcs8")))
        assertEquals("{\"kty\":\"RSA\"}", opened)
    }

    @Test
    fun privateKeyEncodingRoundTrips() {
        val key = SendlyCrypto.generateDeviceKey().private
        assertFalse(SendlyCrypto.encodePrivateKey(key).isBlank())
        assertEquals(key, SendlyCrypto.decodePrivateKey(SendlyCrypto.encodePrivateKey(key)))
    }
}
