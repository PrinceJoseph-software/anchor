package com.anchor.data.repository

/**
 * Pure-Kotlin HMAC-SHA-256 used to sign and verify Anchor export payloads.
 *
 * No external dependency — the SHA-256 compression function is implemented
 * directly from FIPS 180-4, so it works identically on Android and iOS.
 *
 * Flow
 * ─────
 * Export: serialize AnchorExport with signature="" → sign → embed hex digest
 * Import: parse → re-serialize with signature="" → verify → apply data
 *
 * A crafted import whose signature doesn't match the embedded app key is rejected,
 * preventing abuse by anyone who inspects or reconstructs the JSON schema.
 */
internal object AnchorSigner {

    // Fixed app-specific signing key — never changes across releases.
    // Stored as a hex literal so no plaintext secret appears in the class file.
    // Decoded value: "Anch0r::S1gn!ngK3y::2024::d3f4ult"
    private val SIGNING_KEY: ByteArray = hexDecode(
        "416e636830723a3a5331676e216e674b33793a3a323032343a3a6433663475336c74"
    )

    /**
     * Returns the HMAC-SHA-256 of [canonical] (the JSON with signature="") as a
     * 64-character lowercase hex string.
     */
    fun sign(canonical: String): String =
        hexEncode(hmacSha256(SIGNING_KEY, canonical.encodeToByteArray()))

    /**
     * Returns true iff [sig] is the correct HMAC-SHA-256 of [canonical].
     * The comparison is constant-time to prevent timing side-channels.
     */
    fun verify(canonical: String, sig: String): Boolean {
        if (sig.length != 64) return false
        val expected = sign(canonical)
        var diff = 0
        for (i in 0 until 64) diff = diff or (expected[i].code xor sig[i].code)
        return diff == 0
    }

    // ── HMAC-SHA-256 (RFC 2104) ──────────────────────────────────────────────

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val blockLen = 64
        val normKey  = if (key.size > blockLen) sha256(key) else key
        val padKey   = normKey + ByteArray(blockLen - normKey.size)
        val ipad     = ByteArray(blockLen) { (padKey[it].toInt() xor 0x36).toByte() }
        val opad     = ByteArray(blockLen) { (padKey[it].toInt() xor 0x5c).toByte() }
        return sha256(opad + sha256(ipad + data))
    }

    // ── SHA-256 (FIPS 180-4) ─────────────────────────────────────────────────

    private val H0 = intArrayOf(
        0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372.toInt(), 0xa54ff53a.toInt(),
        0x510e527f.toInt(), 0x9b05688c.toInt(), 0x1f83d9ab.toInt(), 0x5be0cd19.toInt(),
    )

    @Suppress("SpellCheckingInspection")
    private val KK = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
        0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
        0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
        0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
        0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

    private fun sha256(input: ByteArray): ByteArray {
        // SHA-256 arithmetic is mod 2^32; Kotlin/JVM Int overflow wraps correctly.
        val bitLen = input.size.toLong() * 8L

        // Append 0x80, then zero-pad until length ≡ 56 (mod 64), then 8-byte big-endian bit count.
        val padLen = (55 - input.size).mod(64) + 1
        val msg = ByteArray(input.size + 1 + padLen + 8)
        input.copyInto(msg)
        msg[input.size] = 0x80.toByte()
        for (i in 7 downTo 0) msg[msg.size - 8 + (7 - i)] = ((bitLen ushr (i * 8)) and 0xFF).toByte()

        val h = H0.copyOf()
        val w = IntArray(64)

        for (block in 0 until msg.size / 64) {
            val off = block * 64
            for (i in 0..15) {
                w[i] = ((msg[off + i * 4].toInt()     and 0xFF) shl 24) or
                       ((msg[off + i * 4 + 1].toInt() and 0xFF) shl 16) or
                       ((msg[off + i * 4 + 2].toInt() and 0xFF) shl  8) or
                        (msg[off + i * 4 + 3].toInt() and 0xFF)
            }
            for (i in 16..63) {
                val s0 = w[i-15].rotateRight(7) xor w[i-15].rotateRight(18) xor (w[i-15] ushr  3)
                val s1 = w[i- 2].rotateRight(17) xor w[i- 2].rotateRight(19) xor (w[i- 2] ushr 10)
                w[i]   = w[i-16] + s0 + w[i-7] + s1
            }

            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hv = h[7]

            for (i in 0..63) {
                val s1   = e.rotateRight(6)  xor e.rotateRight(11) xor e.rotateRight(25)
                val ch   = (e and f) xor (e.inv() and g)
                val tmp1 = hv + s1 + ch + KK[i] + w[i]
                val s0   = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj  = (a and b) xor (a and c) xor (b and c)
                val tmp2 = s0 + maj
                hv = g; g = f; f = e; e = d + tmp1
                d = c; c = b; b = a; a = tmp1 + tmp2
            }

            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hv
        }

        val out = ByteArray(32)
        for (i in 0..7) {
            out[i*4]   = (h[i] ushr 24).toByte()
            out[i*4+1] = (h[i] ushr 16).toByte()
            out[i*4+2] = (h[i] ushr  8).toByte()
            out[i*4+3] =  h[i].toByte()
        }
        return out
    }

    // ── Hex helpers ──────────────────────────────────────────────────────────

    private fun hexEncode(b: ByteArray): String =
        b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun hexDecode(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
