package com.cns.accounts.data.authenticator

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 TOTP with the parameters every CNS secret uses (and most authenticator QR codes): SHA1, 6 digits, 30s. */
object Totp {
    const val PeriodMillis = 30_000L
    private const val Digits = 6

    fun code(secret: ByteArray, timeMillis: Long): String {
        val counter = timeMillis / PeriodMillis
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var modulus = 1
        repeat(Digits) { modulus *= 10 }
        return (binary % modulus).toString().padStart(Digits, '0')
    }

    /** Decodes an RFC 4648 base32 secret (case/space/padding tolerant); null if it isn't one. */
    fun decodeBase32(input: String): ByteArray? {
        val clean = input.uppercase().filterNot { it == ' ' || it == '-' || it == '=' }
        if (clean.isEmpty()) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (char in clean) {
            val value = alphabet.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add((buffer shr bits and 0xff).toByte())
            }
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }
}
