package com.cns.accounts.data.approvals

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** A freshly created approver key: its public key and Android key attestation chain (base64 DER, leaf first). */
class NewApproverKey(val publicKey: String, val attestation: List<String>)

/**
 * The device keys that make this phone an account's approver (Require Security App): one EC
 * P-256 key per account in the Android Keystore (StrongBox when the phone has one). The private
 * key never leaves the secure hardware; auth-v2 stores only the public key. Which account a key
 * belongs to, and the server's id for this device, are kept next to it in plain preferences
 * (neither is secret).
 */
class ApproverKeys(private val context: Context) {
    private val prefs = context.getSharedPreferences("cns_approver", Context.MODE_PRIVATE)

    fun has(userId: Long): Boolean = prefs.contains(deviceKey(userId)) && keyStore().containsAlias(alias(userId))

    /** The account id whose key this device holds for [username], for approving its own sign-in. */
    fun userIdFor(username: String): Long? = prefs.all.entries
        .firstOrNull { (key, value) -> key.startsWith(UsernamePrefix) && (value as? String).equals(username, ignoreCase = true) }
        ?.key?.removePrefix(UsernamePrefix)?.toLongOrNull()
        ?.takeIf(::has)

    fun deviceId(userId: Long): String? = prefs.getString(deviceKey(userId), null)

    /** When the key was enrolled (device clock), 0 if unknown. */
    fun enrolledAt(userId: Long): Long = prefs.getLong(enrolledAtKey(userId), 0L)

    /** Whether signing needs the screen lock / biometrics first (fixed when the key is created). */
    fun requiresUserAuth(userId: Long): Boolean = prefs.getBoolean(authKey(userId), false)

    /**
     * Creates the account's key, replacing any earlier one. [challenge] (the enrollment challenge,
     * decoded) is bound into the attestation. [userAuth] makes every signature need the screen
     * lock or a strong biometric within the last few seconds.
     */
    fun create(userId: Long, challenge: ByteArray, userAuth: Boolean): NewApproverKey {
        remove(userId)
        val strongBox = Build.VERSION.SDK_INT >= 28 &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        val pair = runCatching { generate(userId, challenge, userAuth, strongBox) }
            .recoverCatching { if (it is StrongBoxUnavailableException && strongBox) generate(userId, challenge, userAuth, false) else throw it }
            .getOrThrow()
        val chain = keyStore().getCertificateChain(alias(userId)).orEmpty()
        prefs.edit().putBoolean(authKey(userId), userAuth).apply()
        return NewApproverKey(
            publicKey = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP),
            attestation = chain.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) },
        )
    }

    /** Records the enrollment: the key now speaks for [userId] ([username]) as server device [deviceId]. */
    fun saveEnrollment(userId: Long, username: String, deviceId: String) {
        prefs.edit()
            .putString(deviceKey(userId), deviceId)
            .putString(UsernamePrefix + userId, username)
            .putLong(enrolledAtKey(userId), System.currentTimeMillis())
            .apply()
    }

    fun rename(userId: Long, username: String) {
        if (prefs.contains(deviceKey(userId))) prefs.edit().putString(UsernamePrefix + userId, username).apply()
    }

    /** ECDSA-SHA256 over [message], base64 ASN.1 DER (what auth-v2 verifies). */
    fun sign(userId: Long, message: ByteArray): String {
        val key = keyStore().getKey(alias(userId), null) as? PrivateKey ?: throw IllegalStateException("no approver key on this device")
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(message)
            sign()
        }
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    fun remove(userId: Long) {
        runCatching { keyStore().deleteEntry(alias(userId)) }
        prefs.edit()
            .remove(deviceKey(userId))
            .remove(authKey(userId))
            .remove(UsernamePrefix + userId)
            .remove(enrolledAtKey(userId))
            .apply()
    }

    private fun generate(userId: Long, challenge: ByteArray, userAuth: Boolean, strongBox: Boolean) =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KeyStoreType).run {
            val spec = KeyGenParameterSpec.Builder(alias(userId), KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challenge)
                .setIsStrongBoxBacked(strongBox)
            if (userAuth) {
                spec.setUserAuthenticationRequired(true)
                // A new fingerprint must not silently retire the approver (that would lock the
                // account into the TOTP fallback); the screen lock still guards the key.
                spec.setInvalidatedByBiometricEnrollment(false)
                if (Build.VERSION.SDK_INT >= 30) {
                    spec.setUserAuthenticationParameters(
                        UserAuthSeconds,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    spec.setUserAuthenticationValidityDurationSeconds(UserAuthSeconds)
                }
            }
            initialize(spec.build())
            generateKeyPair()
        }

    private fun keyStore() = KeyStore.getInstance(KeyStoreType).apply { load(null) }

    private fun alias(userId: Long) = "cns_approver_$userId"
    private fun deviceKey(userId: Long) = "device_$userId"
    private fun authKey(userId: Long) = "user_auth_$userId"
    private fun enrolledAtKey(userId: Long) = "enrolled_at_$userId"

    private companion object {
        const val KeyStoreType = "AndroidKeyStore"
        const val UsernamePrefix = "username_"
        // How long one unlock covers signatures: one decision, with room for the network call.
        const val UserAuthSeconds = 15
    }
}

/** The bytes the approver signs for a decision; auth-v2 rebuilds the same string to verify. */
fun approvalMessage(id: String, userId: Long, action: String, nonce: String, approve: Boolean, expiresAtUnix: Long): ByteArray =
    listOf("cns-approval-v1", id, userId.toString(), action, nonce, if (approve) "approve" else "deny", expiresAtUnix.toString())
        .joinToString("|").toByteArray(Charsets.UTF_8)

/** The bytes signed at enrollment, proving the new key is held by this session's user. */
fun enrollMessage(userId: Long, challenge: String): ByteArray =
    "cns-approver-enroll-v1|$userId|$challenge".toByteArray(Charsets.UTF_8)
