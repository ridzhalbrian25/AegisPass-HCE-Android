package com.aegispass.hce.data.hce

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import com.aegispass.hce.domain.model.ApduResponseHelper
import com.aegispass.hce.domain.model.ScannedTagResult
import com.aegispass.hce.domain.usecase.ProtocolParserUseCase

/**
 * PRD Phase 1 NFC Tag Reader & Auto-Detection Engine.
 * Uses `NfcAdapter.enableReaderMode` with `FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK`
 * to synchronously inspect physical access cards without system intent popups.
 */
class NfcTagReaderManager(
    private val protocolParser: ProtocolParserUseCase = ProtocolParserUseCase()
) {
    private val readerFlags =
        NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

    private val discoveryAids = listOf(
        "F001020304",
        "A0000000031010",
        "A0000000041010",
        "A0000001510000",
        "D2760000850101"
    )

    fun enableScanMode(activity: Activity, onTagScanned: (ScannedTagResult) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        adapter.enableReaderMode(
            activity,
            { tag: Tag? ->
                if (tag != null) {
                    val result = inspectPhysicalTag(tag)
                    onTagScanned(result)
                }
            },
            readerFlags,
            null
        )
    }

    fun disableScanMode(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        adapter.disableReaderMode(activity)
    }

    private fun inspectPhysicalTag(tag: Tag): ScannedTagResult {
        val uidHex = ApduResponseHelper.bytesToHex(tag.id ?: ByteArray(0))
        val techList = tag.techList?.toList() ?: emptyList()

        var sakHex = "00"
        var atqaHex = "0000"
        var atsHex = ""
        var discoveredAid = "F001020304"
        var discoveredPayload = uidHex.ifEmpty { "0102030405060708" }

        // 1. Extract Low-Level ISO 14443-3A parameters (SAK & ATQA)
        try {
            NfcA.get(tag)?.use { nfcA ->
                sakHex = String.format("%02X", nfcA.sak.toInt() and 0xFF)
                atqaHex = ApduResponseHelper.bytesToHex(nfcA.atqa ?: ByteArray(0))
            }
        } catch (_: Exception) {
        }

        // 2. If IsoDep (ISO 14443-4) is supported, extract ATS & probe standard discovery AIDs
        if (techList.any { it.endsWith("IsoDep") }) {
            try {
                IsoDep.get(tag)?.use { isoDep ->
                    isoDep.connect()
                    isoDep.timeout = 500
                    val histBytes = isoDep.historicalBytes ?: isoDep.hiLayerResponse ?: ByteArray(0)
                    atsHex = ApduResponseHelper.bytesToHex(histBytes)

                    for (aidHex in discoveryAids) {
                        val aidBytes = ApduResponseHelper.hexToBytes(aidHex)
                        val selectCmd = ByteArray(5 + aidBytes.size)
                        selectCmd[0] = 0x00
                        selectCmd[1] = 0xA4.toByte()
                        selectCmd[2] = 0x04
                        selectCmd[3] = 0x00
                        selectCmd[4] = aidBytes.size.toByte()
                        System.arraycopy(aidBytes, 0, selectCmd, 5, aidBytes.size)

                        val resp = isoDep.transceive(selectCmd)
                        val respHex = ApduResponseHelper.bytesToHex(resp)
                        if (respHex.endsWith("9000") && respHex.length > 4) {
                            discoveredAid = aidHex
                            discoveredPayload = respHex.substring(0, respHex.length - 4)
                            break
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val (protocolType, compatibilityStatus) = protocolParser.evaluateCompatibility(techList)

        return ScannedTagResult(
            rawUidHex = uidHex,
            sakHex = sakHex,
            atqaHex = atqaHex,
            atsHex = atsHex.ifEmpty { "7877B102" },
            detectedTechList = techList.map { it.substringAfterLast('.') },
            protocolType = protocolType,
            compatibilityStatus = compatibilityStatus,
            discoveredAidHex = discoveredAid,
            discoveredPayloadHex = discoveredPayload
        )
    }
}
