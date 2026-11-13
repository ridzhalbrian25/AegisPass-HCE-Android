package com.aegispass.hce.data.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.aegispass.hce.domain.engine.EmulationMemoryBuffer
import com.aegispass.hce.domain.model.ApduResponseHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * PRD Section 9 Core HostApduService (HCE) Implementation.
 * Responds to ISO/IEC 7816-4 / ISO 14443-4 Door Readers within < 35 ms SLA.
 */
@AndroidEntryPoint
class AegisHostApduService : HostApduService() {

    @Inject
    lateinit var memoryBuffer: EmulationMemoryBuffer

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val startTime = System.nanoTime()

        // 1. Validasi Keberadaan Payload APDU
        if (commandApdu == null || commandApdu.isEmpty()) {
            return ApduResponseHelper.STATUS_WRONG_LENGTH
        }

        // 2. Evaluasi Sesi Emulasi (Biometric Gate Active & Timer Valid)
        if (!memoryBuffer.isEmulationSessionActive()) {
            val rejected = ApduResponseHelper.STATUS_CONDITIONS_NOT_SATISFIED
            val latencyMs = (System.nanoTime() - startTime) / 1_000_000
            memoryBuffer.recordAuditLog(commandApdu, rejected, latencyMs)
            return rejected
        }

        // 3. Evaluasi Kesesuaian AID dan Pencarian Respons
        val responsePayload: ByteArray = try {
            val matchedResponse = memoryBuffer.resolveResponseForCommand(commandApdu)
            matchedResponse ?: ApduResponseHelper.STATUS_FILE_NOT_FOUND
        } catch (e: Exception) {
            ApduResponseHelper.STATUS_UNKNOWN_ERROR
        }

        // 4. Catat Latensi Eksekusi (Observability - Circular Ring Buffer)
        val latencyMs = (System.nanoTime() - startTime) / 1_000_000
        memoryBuffer.recordAuditLog(
            commandApdu = commandApdu,
            responseApdu = responsePayload,
            latencyMs = latencyMs
        )

        return responsePayload
    }

    override fun onDeactivated(reason: Int) {
        when (reason) {
            DEACTIVATION_LINK_LOSS -> {
                memoryBuffer.handleRfLinkLoss()
            }
            DEACTIVATION_DESELECTED -> {
                memoryBuffer.handleDeselected()
            }
        }
    }
}
