package com.cns.accounts.data.sendly

import com.cns.accounts.BuildConfig
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.HttpResponse
import com.cns.accounts.data.api.HttpTransport
import com.cns.accounts.data.api.RequestBody
import com.cns.accounts.data.api.optInstant
import com.cns.accounts.data.api.optStringOrNull
import org.json.JSONObject
import java.time.Instant

/** Sendly's answer to a device registration (`/android/me/devices/register`). */
data class SendlyRegistration(
    val deviceId: String,
    /** Set once this device is trusted: the identity key, wrapped for this device. */
    val envelope: ByteArray?,
    val identityKeyVersion: Int,
    /** The account's active identity public key, to check the unwrapped private key against. */
    val identityPublicJwk: JSONObject?,
    val needsEnrollment: Boolean,
    /** The account has no identity key yet (or an old one): a browser has to set it up first. */
    val needsIdentitySetup: Boolean,
)

data class SendlyEnrollment(val id: String, val verificationCode: String, val expiresAt: Instant?)

/** Another device asking to join, with its public key to wrap the identity key for. */
data class SendlyPendingEnrollment(
    val id: String,
    val requestDeviceId: String,
    val verificationCode: String,
    val deviceLabel: String,
    val devicePublicJwk: JSONObject?,
    val createdAt: Instant?,
)

/**
 * Sendly's device routes for Android clients (`/android/me/devices/...`, bearer-authenticated
 * with a CNS access token issued to the `Sendly` client, see [SendlySession]). Sendly answers
 * errors as `{error, code}`, mapped to [ApiException] like auth's.
 */
class SendlyApi(private val session: SendlySession) {
    private val transport = HttpTransport(BuildConfig.SENDLY_BASE_URL)

    suspend fun register(deviceId: String, label: String, publicJwk: JSONObject): SendlyRegistration {
        val body = JSONObject()
            .put("device_id", deviceId)
            .put("device_label", label)
            .put("public_key_jwk", publicJwk)
            .put("key_algorithm", SendlyCrypto.DeviceKeyAlgorithm)
            .put("key_version", 1)
            .put("device_kind", "approver")
        val json = call("POST", "/android/me/devices/register", body)
        val envelope = json.optJSONObject("identity_key_envelope")
        return SendlyRegistration(
            deviceId = json.optString("device_id", deviceId),
            envelope = envelope?.optStringOrNull("wrapped_private_key_b64")?.let { java.util.Base64.getDecoder().decode(it) },
            identityKeyVersion = envelope?.optInt("identity_key_version") ?: 0,
            identityPublicJwk = json.optJSONObject("identity_public_key")?.let { jwkField(it, "public_key_jwk") },
            needsEnrollment = json.optBoolean("needs_enrollment"),
            needsIdentitySetup = json.optBoolean("needs_identity_setup") || json.optBoolean("needs_identity_migration"),
        )
    }

    suspend fun createEnrollment(deviceId: String): SendlyEnrollment {
        val json = call("POST", "/android/me/devices/enrollments", JSONObject().put("request_device_id", deviceId))
        return SendlyEnrollment(json.optString("enrollment_id"), json.optString("verification_code"), json.optInstant("expires_at"))
    }

    suspend fun cancelEnrollment(enrollmentId: String, deviceId: String) {
        call("POST", "/android/me/devices/enrollments/${enc(enrollmentId)}/cancel", JSONObject().put("request_device_id", deviceId))
    }

    suspend fun pendingEnrollments(): List<SendlyPendingEnrollment> {
        val items = call("GET", "/android/me/devices/enrollments/pending").optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val enrollment = item.optJSONObject("enrollment") ?: return@mapNotNull null
            val device = item.optJSONObject("request_device") ?: JSONObject()
            SendlyPendingEnrollment(
                id = enrollment.optString("id"),
                requestDeviceId = enrollment.optString("request_device_id"),
                verificationCode = enrollment.optString("verification_code"),
                deviceLabel = device.optStringOrNull("device_label") ?: "Unknown device",
                devicePublicJwk = jwkField(device, "public_key_jwk"),
                createdAt = enrollment.optInstant("created_at"),
            )
        }
    }

    suspend fun approve(enrollment: SendlyPendingEnrollment, approverDeviceId: String, wrappedIdentityKey: ByteArray, identityKeyVersion: Int) {
        val body = JSONObject()
            .put("approver_device_id", approverDeviceId)
            .put("verification_code", enrollment.verificationCode)
            .put("wrapped_identity_private_key_b64", java.util.Base64.getEncoder().encodeToString(wrappedIdentityKey))
            .put("identity_key_wrap_alg", SendlyCrypto.IdentityWrapAlg)
            .put(
                "identity_key_wrap_meta",
                JSONObject()
                    .put("type", "enrollment-approval")
                    .put("approver_device_id", approverDeviceId)
                    .put("request_device_id", enrollment.requestDeviceId),
            )
            .put("identity_key_version", identityKeyVersion)
        call("POST", "/android/me/devices/enrollments/${enc(enrollment.id)}/approve", body)
    }

    suspend fun reject(enrollmentId: String, approverDeviceId: String) {
        call("POST", "/android/me/devices/enrollments/${enc(enrollmentId)}/reject", JSONObject().put("approver_device_id", approverDeviceId))
    }

    /** Sendly forgets this approver device and its copy of the identity key. */
    suspend fun removeDevice(deviceId: String) {
        call("DELETE", "/android/me/devices/${enc(deviceId)}")
    }

    /** One retry with a fresh token when Sendly rejects the cached one. */
    private suspend fun call(method: String, path: String, json: JSONObject? = null): JSONObject {
        val body = json?.let(RequestBody::Json)
        var token = session.accessToken()
        var response = transport.execute(method, path, body, bearerToken = token)
        if (response.status == 401) {
            token = session.accessToken(forceRefresh = true)
            response = transport.execute(method, path, body, bearerToken = token)
        }
        if (!response.isSuccessful) throw sendlyError(response)
        return response.json()
    }

    private fun sendlyError(response: HttpResponse): ApiException {
        val body = runCatching { JSONObject(response.body) }.getOrNull()
        return ApiException(
            status = response.status,
            code = body?.optStringOrNull("code") ?: "sendly_request_failed",
            message = body?.optStringOrNull("error") ?: "Sendly request failed (${response.status})",
        )
    }

    // Sendly stores JWKs as JSON; older rows may hold them as a JSON string.
    private fun jwkField(json: JSONObject, name: String): JSONObject? =
        json.optJSONObject(name) ?: json.optStringOrNull(name)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun enc(segment: String) = java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
