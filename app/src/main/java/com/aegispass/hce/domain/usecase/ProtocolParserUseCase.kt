package com.aegispass.hce.domain.usecase

import com.aegispass.hce.domain.model.ApduResponseHelper
import com.aegispass.hce.domain.model.CardProtocolType
import com.aegispass.hce.domain.model.HceCompatibilityStatus
import com.aegispass.hce.domain.model.ReaderVendor

data class ParsedApduCommand(
    val cla: Byte,
    val ins: Byte,
    val p1: Byte,
    val p2: Byte,
    val lc: Int,
    val dataField: ByteArray,
    val le: Int,
    val isSelectByAid: Boolean,
    val isReadBinaryOrGetData: Boolean
)

/**
 * ISO/IEC 7816-4 APDU Command Parser & HCE Protocol Compatibility Evaluator.
 */
class ProtocolParserUseCase {

    fun parseCommandApdu(apdu: ByteArray): ParsedApduCommand {
        if (apdu.size < 4) {
            return ParsedApduCommand(
                cla = 0, ins = 0, p1 = 0, p2 = 0, lc = 0,
                dataField = ByteArray(0), le = 0,
                isSelectByAid = false, isReadBinaryOrGetData = false
            )
        }

        val cla = apdu[0]
        val ins = apdu[1]
        val p1 = apdu[2]
        val p2 = apdu[3]

        val lc = if (apdu.size >= 5) (apdu[4].toInt() and 0xFF) else 0
        val dataEndIndex = (5 + lc).coerceAtMost(apdu.size)
        val dataField = if (apdu.size > 5 && lc > 0) {
            apdu.copyOfRange(5, dataEndIndex)
        } else {
            ByteArray(0)
        }
        val le = if (apdu.size > dataEndIndex) (apdu[apdu.size - 1].toInt() and 0xFF) else 0

        val isSelectByAid = (cla == 0x00.toByte() && ins == 0xA4.toByte() && p1 == 0x04.toByte())
        val isReadOrGetData = (ins == 0xB0.toByte() || ins == 0xCA.toByte())

        return ParsedApduCommand(
            cla = cla,
            ins = ins,
            p1 = p1,
            p2 = p2,
            lc = lc,
            dataField = dataField,
            le = le,
            isSelectByAid = isSelectByAid,
            isReadBinaryOrGetData = isReadOrGetData
        )
    }

    /**
     * Evaluates physical NFC Tag tech list according to PRD Phase 1 HCE Compatibility Rules.
     */
    fun evaluateCompatibility(techList: List<String>): Pair<CardProtocolType, HceCompatibilityStatus> {
        val normalized = techList.map { it.substringAfterLast('.') }
        return when {
            normalized.contains("IsoDep") -> {
                CardProtocolType.ISO_DEP to HceCompatibilityStatus.FULLY_COMPATIBLE
            }
            normalized.contains("MifareClassic") -> {
                CardProtocolType.MIFARE_CLASSIC to HceCompatibilityStatus.HARDWARE_INCOMPATIBLE
            }
            normalized.contains("MifareUltralight") -> {
                CardProtocolType.MIFARE_ULTRALIGHT to HceCompatibilityStatus.PARTIALLY_COMPATIBLE
            }
            normalized.contains("NfcF") -> {
                CardProtocolType.FELICA_NFC_F to HceCompatibilityStatus.PARTIALLY_COMPATIBLE
            }
            else -> {
                CardProtocolType.MIFARE_ULTRALIGHT to HceCompatibilityStatus.PARTIALLY_COMPATIBLE
            }
        }
    }

    /**
     * Identifies door reader vendor signature based on incoming C-APDU AID query (PRD Phase 3).
     */
    fun identifyReaderVendor(commandApduHex: String): ReaderVendor {
        val upper = commandApduHex.uppercase()
        return ReaderVendor.entries.firstOrNull { vendor ->
            vendor.signaturePrefix.isNotEmpty() && upper.contains(vendor.signaturePrefix)
        } ?: ReaderVendor.GENERIC_ISO7816
    }

    /**
     * Formats raw hex string into a 16-byte split view (Offset | Hex Data | ASCII) for Phase 2 Hex Viewer.
     */
    fun formatHexDumpSplitView(hexString: String): List<HexDumpLine> {
        val bytes = ApduResponseHelper.hexToBytes(hexString)
        if (bytes.isEmpty()) return emptyList()

        val lines = mutableListOf<HexDumpLine>()
        var offset = 0
        while (offset < bytes.size) {
            val chunkEnd = (offset + 16).coerceAtMost(bytes.size)
            val chunk = bytes.copyOfRange(offset, chunkEnd)

            val offsetStr = String.format("%04X", offset)
            val hexPart = chunk.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
                .padEnd(47, ' ')
            val asciiPart = chunk.map { b ->
                val c = b.toInt() and 0xFF
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")

            lines.add(HexDumpLine(offsetStr, hexPart, asciiPart))
            offset += 16
        }
        return lines
    }
}

data class HexDumpLine(
    val offsetHex: String,
    val hexChunk: String,
    val asciiRepresentation: String
)
