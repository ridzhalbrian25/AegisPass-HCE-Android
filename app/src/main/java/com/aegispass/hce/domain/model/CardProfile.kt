package com.aegispass.hce.domain.model

enum class CardProtocolType(val displayName: String, val standardSpec: String) {
    ISO_DEP("ISO-DEP (ISO 14443-4 / 7816-4)", "DESFire EV1/EV2/EV3 / SmartMX / JavaCard"),
    MIFARE_ULTRALIGHT("Mifare Ultralight / NTAG", "ISO 14443-3A Type 2 Tag"),
    MIFARE_CLASSIC("Mifare Classic 1K/4K (Crypto-1)", "ISO 14443-3A Proprietary Crypto-1"),
    FELICA_NFC_F("FeliCa (NFC-F)", "JIS X 6319-4")
}

enum class HceCompatibilityStatus(
    val badgeLabel: String,
    val isSupported: Boolean,
    val technicalMessage: String
) {
    FULLY_COMPATIBLE(
        badgeLabel = "100% Fully Compatible (HCE Supported)",
        isSupported = true,
        technicalMessage = "Kartu mendukung layer ISO-DEP (ISO/IEC 14443-4 & 7816-4) dan dapat diemulasi penuh oleh Android HostApduService tanpa akses Root."
    ),
    PARTIALLY_COMPATIBLE(
        badgeLabel = "Partially Compatible (UID-Only Dependency)",
        isSupported = true,
        technicalMessage = "Kartu berbasis Mifare Ultralight/NTAG. Emulasi berhasil apabila door reader membaca payload NDEF/AID aplikasi, namun gagal jika reader mengunci static hardware UID."
    ),
    HARDWARE_INCOMPATIBLE(
        badgeLabel = "Hardware Incompatible (Non-Root HCE Restricted)",
        isSupported = false,
        technicalMessage = "PERINGATAN TEKNIS: Kartu ini murni menggunakan Mifare Classic (ISO 14443-3A) berenkripsi proprietary Crypto-1 tanpa layer IsoDep. Pembaca pintu mengandalkan verifikasi UID hardware level rendah yang diblokir oleh Android HCE API tanpa modifikasi Root (libnfc-nci.conf)."
    )
}

enum class CardIconType(val label: String) {
    APARTMENT("Residential / Apartment"),
    OFFICE("Corporate Office"),
    FITNESS("Fitness Center / Gym"),
    PARKING("Parking Barrier"),
    SECURITY_GATE("Security Turnstile Gate")
}

enum class AuditStatus {
    SUCCESS,
    FAILED_TIMEOUT,
    FAILED_UNKNOWN_AID,
    FAILED_UNARMED_SESSION,
    FAILED_TAMPER_LOCKED
}

enum class ReaderVendor(val displayName: String, val signaturePrefix: String) {
    HID_GLOBAL("HID Global (iCLASS SE / Signo)", "A000000440"),
    DORMAKABA("Dormakaba (evolo / Legic)", "F001020304"),
    ASSA_ABLOY("Assa Abloy / VingCard", "A0000000031010"),
    HONEYWELL("Honeywell OmniAssure", "A0000001510000"),
    GENERIC_ISO7816("Generic ISO 7816-4 Terminal", "D2760000850101"),
    UNKNOWN("Unknown Door Terminal", "")
}

data class CardProfile(
    val id: String,
    val name: String,
    val description: String,
    val locationTag: String,
    val colorPrimary: String,
    val colorSecondary: String,
    val iconType: CardIconType,
    val protocolType: CardProtocolType,
    val rawUidHex: String,
    val sakHex: String,
    val atqaHex: String,
    val atsHex: String,
    val primaryAid: String,
    val isFavorite: Boolean,
    val isHceSupported: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val payloads: List<ApduPayload> = emptyList()
)

data class ApduPayload(
    val id: String,
    val cardProfileId: String,
    val commandApdu: String,
    val responsePayload: String,
    val executionOrder: Int,
    val description: String
)

data class EmulationAuditLog(
    val id: String,
    val cardProfileId: String,
    val readerAidReq: String,
    val latencyMs: Long,
    val status: AuditStatus,
    val timestamp: Long,
    val apduTraceJson: String
)

data class ReaderDiagnostic(
    val id: String,
    val cardProfileId: String,
    val detectedVendor: String,
    val terminalSigHex: String,
    val successCount: Int,
    val failureCount: Int,
    val lastInteracted: Long
)

data class EmulationSessionState(
    val isActive: Boolean = false,
    val activeCardId: String? = null,
    val activeCardName: String? = null,
    val activeAidHex: String? = null,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 30,
    val totalTransactionsProcessed: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastStatusWordHex: String = "IDLE"
)

data class ScannedTagResult(
    val rawUidHex: String,
    val sakHex: String,
    val atqaHex: String,
    val atsHex: String,
    val detectedTechList: List<String>,
    val protocolType: CardProtocolType,
    val compatibilityStatus: HceCompatibilityStatus,
    val discoveredAidHex: String,
    val discoveredPayloadHex: String
)
