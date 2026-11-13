package com.aegispass.hce.presentation.mvi

import com.aegispass.hce.domain.model.ApduPayload
import com.aegispass.hce.domain.model.CardIconType
import com.aegispass.hce.domain.model.CardProfile
import com.aegispass.hce.domain.model.CardProtocolType
import com.aegispass.hce.domain.model.EmulationAuditLog
import com.aegispass.hce.domain.model.EmulationSessionState
import com.aegispass.hce.domain.model.ReaderDiagnostic
import com.aegispass.hce.domain.model.ScannedTagResult

enum class AegisTab {
    WALLET,
    SCANNER,
    APDU_INSPECTOR,
    DIAGNOSTICS,
    SECURITY_BACKUP
}

data class AegisUiState(
    val activeTab: AegisTab = AegisTab.WALLET,
    val profiles: List<CardProfile> = emptyList(),
    val selectedCardIndex: Int = 0,
    val sessionState: EmulationSessionState = EmulationSessionState(),
    val configuredTimeoutSeconds: Int = 30,
    val isScanningNfc: Boolean = false,
    val lastScannedTag: ScannedTagResult? = null,
    val auditLogs: List<EmulationAuditLog> = emptyList(),
    val readerDiagnostics: List<ReaderDiagnostic> = emptyList(),
    val exportedBackupJson: String? = null,
    val statusBannerMessage: String? = null,
    val isOledPureBlack: Boolean = true
) {
    val selectedCard: CardProfile?
        get() = profiles.getOrNull(selectedCardIndex)
}

sealed interface AegisIntent {
    data class SelectTab(val tab: AegisTab) : AegisIntent
    data class SelectCardIndex(val index: Int) : AegisIntent
    data class SetTimeoutSeconds(val seconds: Int) : AegisIntent
    data class RequestBiometricArm(val cardId: String) : AegisIntent
    data class OnBiometricAuthenticated(val cardId: String) : AegisIntent
    data object DisarmEmulation : AegisIntent
    data class SimulateDoorReaderApdu(val commandApduHex: String) : AegisIntent
    data class OnPhysicalTagScanned(val result: ScannedTagResult) : AegisIntent
    data class SaveNewOrUpdatedCard(
        val id: String?,
        val name: String,
        val description: String,
        val locationTag: String,
        val colorPrimary: String,
        val colorSecondary: String,
        val iconType: CardIconType,
        val protocolType: CardProtocolType,
        val rawUidHex: String,
        val primaryAid: String,
        val customPayloads: List<ApduPayload>
    ) : AegisIntent
    data class ToggleFavorite(val cardId: String, val isFavorite: Boolean) : AegisIntent
    data class DeleteCard(val cardId: String) : AegisIntent
    data class ExportEncryptedBackup(val password: String) : AegisIntent
    data class ImportEncryptedBackup(val jsonPayload: String, val password: String) : AegisIntent
    data object ExecuteEmergencyPanicWipe : AegisIntent
    data object DismissStatusBanner : AegisIntent
}
