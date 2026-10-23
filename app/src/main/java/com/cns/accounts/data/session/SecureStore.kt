package com.cns.accounts.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small string store for secrets (the refresh token, the authenticator key): values are
 * AES-GCM encrypted with a non-exportable Android Keystore key before they touch
 * SharedPreferences. The file is excluded from backups (see res/xml), since a restored copy
 * could never be decrypted on another device anyway. A value that fails to decrypt (key wiped,
 * e.g. after a lock-screen reset on some devices) reads as absent.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IvLength))
            String(cipher.doFinal(bytes, IvLength, bytes.size - IvLength), Charsets.UTF_8)
        }.getOrNull()
    }

    /** Writes synchronously: a rotated refresh token must be on disk before the old one is dropped. */
    fun put(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).commit()
            return
        }
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KeyStoreType).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreType)
        generator.init(
            KeyGenParameterSpec.Builder(KeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PrefsName = "cns_secure"
        const val KeyStoreType = "AndroidKeyStore"
        const val KeyAlias = "cns_secure_store"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvLength = 12
    }
}
