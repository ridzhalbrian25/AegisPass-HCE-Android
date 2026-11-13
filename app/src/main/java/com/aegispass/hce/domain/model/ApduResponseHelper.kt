package com.aegispass.hce.domain.model

import java.util.Arrays

/**
 * ISO/IEC 7816-4 Status Words & Binary Scrubbing Utilities.
 * All operations avoid intermediate String allocations on hot HCE paths.
 */
object ApduResponseHelper {

    // 0x90 0x00: Command completed successfully
    val STATUS_SUCCESS: ByteArray
        get() = byteArrayOf(0x90.toByte(), 0x00.toByte())

    // 0x67 0x00: Wrong length / Empty C-APDU
    val STATUS_WRONG_LENGTH: ByteArray
        get() = byteArrayOf(0x67.toByte(), 0x00.toByte())

    // 0x6F 0x00: Technical failure / Emulation session unarmed or expired (PRD Section 3 & 5.1)
    val STATUS_CONDITIONS_NOT_SATISFIED: ByteArray
        get() = byteArrayOf(0x6F.toByte(), 0x00.toByte())

    // 0x6A 0x82: File or application (AID) not found
    val STATUS_FILE_NOT_FOUND: ByteArray
        get() = byteArrayOf(0x6A.toByte(), 0x82.toByte())

    // 0x69 0x82: Security status not satisfied
    val STATUS_SECURITY_NOT_SATISFIED: ByteArray
        get() = byteArrayOf(0x69.toByte(), 0x82.toByte())

    // 0x6F 0x00: Unknown error / No precise diagnosis
    val STATUS_UNKNOWN_ERROR: ByteArray
        get() = byteArrayOf(0x6F.toByte(), 0x00.toByte())

    // ISO 7816-4 SELECT by DF Name / AID prefix: CLA=00, INS=A4, P1=04, P2=00
    val SELECT_AID_HEADER = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte())

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace(":", "").trim()
        if (clean.isEmpty() || clean.length % 2 != 0) return ByteArray(0)
        val result = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            val high = Character.digit(clean[i], 16)
            val low = Character.digit(clean[i + 1], 16)
            if (high == -1 || low == -1) return ByteArray(0)
            result[i / 2] = ((high shl 4) + low).toByte()
            i += 2
        }
        return result
    }

    fun bytesToHex(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val hexChars = "0123456789ABCDEF".toCharArray()
        val result = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            result[j * 2] = hexChars[v ushr 4]
            result[j * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(result)
    }

    /**
     * NFR 6.1 Volatile Memory Scrubbing:
     * Overwrites sensitive credential buffers with 0x00 immediately after session end.
     */
    fun scrubBytes(buffer: ByteArray?) {
        if (buffer != null && buffer.isNotEmpty()) {
            Arrays.fill(buffer, 0.toByte())
        }
    }
}
