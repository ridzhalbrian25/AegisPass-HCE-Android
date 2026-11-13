package com.aegispass.hce

import com.aegispass.hce.domain.engine.EmulationMemoryBuffer
import com.aegispass.hce.domain.model.ApduResponseHelper
import com.aegispass.hce.domain.model.CardProtocolType
import com.aegispass.hce.domain.model.HceCompatibilityStatus
import com.aegispass.hce.domain.usecase.ProtocolParserUseCase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD Section 10 Verification & Test Strategy Suite:
 * 1. Protocol Verification Test (Mifare Classic 1K vs DESFire/ISO-DEP)
 * 2. Biometric Bypass Resistance Test
 * 3. Session Expiry Test (0x6F00 returned when unarmed/expired)
 * 4. Memory Hygiene Inspection (0x00 zero-fill scrubbing on byte arrays)
 */
class AegisVerificationTest {

    private val protocolParser = ProtocolParserUseCase()

    @Test
    fun `1_protocolVerificationTest_mifareClassicFlaggedIncompatible_isoDepCompatible`() {
        // Mifare Classic 1K without IsoDep layer MUST be flagged as HARDWARE_INCOMPATIBLE
        val mifareTechs = listOf("android.nfc.tech.NfcA", "android.nfc.tech.MifareClassic")
        val (mifareProto, mifareStatus) = protocolParser.evaluateCompatibility(mifareTechs)
        assertEquals(CardProtocolType.MIFARE_CLASSIC, mifareProto)
        assertEquals(HceCompatibilityStatus.HARDWARE_INCOMPATIBLE, mifareStatus)
        assertFalse(mifareStatus.isSupported)

        // DESFire EV1/EV2 or JavaCard with IsoDep MUST be flagged as 100% FULLY_COMPATIBLE
        val isoDepTechs = listOf("android.nfc.tech.NfcA", "android.nfc.tech.IsoDep")
        val (isoProto, isoStatus) = protocolParser.evaluateCompatibility(isoDepTechs)
        assertEquals(CardProtocolType.ISO_DEP, isoProto)
        assertEquals(HceCompatibilityStatus.FULLY_COMPATIBLE, isoStatus)
        assertTrue(isoStatus.isSupported)
    }

    @Test
    fun `2_biometricBypassResistanceTest_programmaticCallWithoutBiometricRemainsLocked`() {
        val memoryBuffer = EmulationMemoryBuffer(protocolParser)

        // Attempt to arm session with biometricAuthenticated = false
        val result = memoryBuffer.loadSessionIntoVolatileMemory(
            cardId = "card-001",
            cardName = "Office Access",
            primaryAidHex = "F001020304",
            defaultTokenHex = "04AABBCCDDEE",
            customApduMap = emptyMap(),
            timeoutSeconds = 30,
            biometricAuthenticated = false
        )

        assertTrue(result.isFailure)
        assertFalse(memoryBuffer.isEmulationSessionActive())
        assertEquals(false, memoryBuffer.sessionState.value.isActive)
    }

    @Test
    fun `3_sessionExpiryTest_disarmedOrExpiredSessionReturns0x6F00`() {
        val memoryBuffer = EmulationMemoryBuffer(protocolParser)

        // Arm with valid biometric authentication
        val armResult = memoryBuffer.loadSessionIntoVolatileMemory(
            cardId = "card-001",
            cardName = "Apartment Lift",
            primaryAidHex = "F001020304",
            defaultTokenHex = "01020304",
            customApduMap = emptyMap(),
            timeoutSeconds = 30,
            biometricAuthenticated = true
        )
        assertTrue(armResult.isSuccess)
        assertTrue(memoryBuffer.isEmulationSessionActive())

        // Verify SELECT AID succeeds while armed (returns payload 01020304 + 9000)
        val selectApdu = ApduResponseHelper.hexToBytes("00A4040005F00102030400")
        val activeResp = memoryBuffer.resolveResponseForCommand(selectApdu)
        assertNotNull(activeResp)
        assertEquals("010203049000", ApduResponseHelper.bytesToHex(activeResp!!))

        // Simulate timeout expiry / disarm
        memoryBuffer.disarmAndScrub()
        assertFalse(memoryBuffer.isEmulationSessionActive())

        // Subsequent C-APDU SELECT MUST return 0x6F00 (STATUS_CONDITIONS_NOT_SATISFIED)
        val expiredResp = memoryBuffer.resolveResponseForCommand(selectApdu)
        assertArrayEquals(ApduResponseHelper.STATUS_CONDITIONS_NOT_SATISFIED, expiredResp)
        assertEquals("6F00", ApduResponseHelper.bytesToHex(expiredResp!!))
    }

    @Test
    fun `4_memoryHygieneInspection_tokenByteArrayIsScrubbedWithZerosOnDisarm`() {
        val memoryBuffer = EmulationMemoryBuffer(protocolParser)
        memoryBuffer.loadSessionIntoVolatileMemory(
            cardId = "card-hygiene",
            cardName = "Vault Card",
            primaryAidHex = "A0000000031010",
            defaultTokenHex = "DEADBEEFCAFEBABE",
            customApduMap = emptyMap(),
            timeoutSeconds = 30,
            biometricAuthenticated = true
        )

        // Capture reference to the internal volatile ByteArray before disarming
        val rawBufferRef = memoryBuffer.inspectRawTokenBufferForTesting()
        assertNotNull(rawBufferRef)
        assertEquals("DEADBEEFCAFEBABE", ApduResponseHelper.bytesToHex(rawBufferRef!!))

        // Disarm session -> must scrub the array in-place with 0x00
        memoryBuffer.disarmAndScrub()

        for (b in rawBufferRef) {
            assertEquals(0.toByte(), b)
        }
    }
}
