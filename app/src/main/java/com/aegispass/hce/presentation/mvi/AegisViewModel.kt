package com.aegispass.hce.presentation.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegispass.hce.domain.engine.EmulationMemoryBuffer
import com.aegispass.hce.domain.engine.HceEngineContract
import com.aegispass.hce.domain.model.ApduPayload
import com.aegispass.hce.domain.model.ApduResponseHelper
import com.aegispass.hce.domain.model.CardIconType
import com.aegispass.hce.domain.model.CardProfile
import com.aegispass.hce.domain.model.CardProtocolType
import com.aegispass.hce.domain.repository.CardRepository
import com.aegispass.hce.domain.usecase.CardProfileUseCase
import com.aegispass.hce.domain.usecase.ProtocolParserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AegisViewModel @Inject constructor(
    private val cardProfileUseCase: CardProfileUseCase,
    private val repository: CardRepository,
    private val hceEngine: HceEngineContract,
    private val memoryBuffer: EmulationMemoryBuffer,
    private val protocolParser: ProtocolParserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AegisUiState())
    val uiState: StateFlow<AegisUiState> = _uiState.asStateFlow()

    private val _biometricTriggerFlow = MutableSharedFlow<String>()
    val biometricTriggerFlow: SharedFlow<String> = _biometricTriggerFlow.asSharedFlow()

    init {
        observeProfiles()
        observeSession()
        observeAuditRingBuffer()
        observeDiagnostics()
        seedSampleCardsIfEmpty()
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            cardProfileUseCase.observeAllProfiles().collectLatest { profiles ->
                _uiState.update { state ->
                    val safeIndex = state.selectedCardIndex.coerceIn(0, (profiles.size - 1).coerceAtLeast(0))
                    state.copy(profiles = profiles, selectedCardIndex = safeIndex)
                }
            }
        }
    }

    private fun observeSession() {
        viewModelScope.launch {
            hceEngine.observeSessionState().collectLatest { session ->
                _uiState.update { it.copy(sessionState = session) }
            }
        }
    }

    private fun observeAuditRingBuffer() {
        viewModelScope.launch {
            memoryBuffer.ringBufferFlow.collectLatest { logs ->
                _uiState.update { it.copy(auditLogs = logs) }
            }
        }
    }

    private fun observeDiagnostics() {
        viewModelScope.launch {
            repository.observeReaderDiagnostics().collectLatest { diags ->
                _uiState.update { it.copy(readerDiagnostics = diags) }
            }
        }
    }

    private fun seedSampleCardsIfEmpty() {
        viewModelScope.launch {
            cardProfileUseCase.observeAllProfiles().collectLatest { list ->
                if (list.isEmpty()) {
                    val now = System.currentTimeMillis()
                    val aptId = UUID.randomUUID().toString()
                    val aptProfile = CardProfile(
                        id = aptId,
                        name = "Sudirman Suites Tower A",
                        description = "Kartu Akses Lift & Lobby Utama (DESFire EV2 ISO-DEP)",
                        locationTag = "Jakarta Pusat",
                        colorPrimary = "#0F172A",
                        colorSecondary = "#0284C7",
                        iconType = CardIconType.APARTMENT,
                        protocolType = CardProtocolType.ISO_DEP,
                        rawUidHex = "04A28F3B916C80",
                        sakHex = "20",
                        atqaHex = "0344",
                        atsHex = "7577810280",
                        primaryAid = "F001020304",
                        isFavorite = true,
                        isHceSupported = true,
                        createdAt = now,
                        updatedAt = now
                    )
                    val aptPayloads = listOf(
                        ApduPayload(
                            id = UUID.randomUUID().toString(),
                            cardProfileId = aptId,
                            commandApdu = "00A4040005F00102030400",
                            responsePayload = "04A28F3B916C809000",
                            executionOrder = 0,
                            description = "SELECT AID Sudirman Suites"
                        )
                    )
                    cardProfileUseCase.saveCardProfile(aptProfile, aptPayloads)
                }
            }
        }
    }

    fun dispatch(intent: AegisIntent) {
        when (intent) {
            is AegisIntent.SelectTab -> {
                _uiState.update { it.copy(activeTab = intent.tab) }
            }
            is AegisIntent.SelectCardIndex -> {
                _uiState.update { it.copy(selectedCardIndex = intent.index) }
            }
            is AegisIntent.SetTimeoutSeconds -> {
                _uiState.update { it.copy(configuredTimeoutSeconds = intent.seconds.coerceIn(15, 60)) }
            }
            is AegisIntent.RequestBiometricArm -> {
                viewModelScope.launch {
                    _biometricTriggerFlow.emit(intent.cardId)
                }
            }
            is AegisIntent.OnBiometricAuthenticated -> {
                viewModelScope.launch {
                    val timeout = _uiState.value.configuredTimeoutSeconds
                    val res = hceEngine.armEmulationSession(
                        cardId = intent.cardId,
                        timeoutSeconds = timeout,
                        biometricVerified = true
                    )
                    res.onFailure { err ->
                        _uiState.update { it.copy(statusBannerMessage = err.message) }
                    }
                }
            }
            is AegisIntent.DisarmEmulation -> {
                viewModelScope.launch {
                    hceEngine.disarmEmulationSession()
                }
            }
            is AegisIntent.SimulateDoorReaderApdu -> {
                viewModelScope.launch {
                    val startNs = System.nanoTime()
                    val cmdBytes = ApduResponseHelper.hexToBytes(intent.commandApduHex)
                    val respBytes = if (!memoryBuffer.isEmulationSessionActive()) {
                        ApduResponseHelper.STATUS_CONDITIONS_NOT_SATISFIED
                    } else {
                        memoryBuffer.resolveResponseForCommand(cmdBytes)
                            ?: ApduResponseHelper.STATUS_FILE_NOT_FOUND
                    }
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000
                    memoryBuffer.recordAuditLog(cmdBytes, respBytes, latencyMs)

                    val vendor = protocolParser.identifyReaderVendor(intent.commandApduHex)
                    val latestLog = memoryBuffer.ringBufferFlow.value.firstOrNull()
                    if (latestLog != null) {
                        repository.recordAuditLogToDb(
                            log = latestLog,
                            detectedVendor = vendor.displayName,
                            terminalSigHex = intent.commandApduHex.take(14)
                        )
                    }
                }
            }
            is AegisIntent.OnPhysicalTagScanned -> {
                _uiState.update {
                    it.copy(
                        lastScannedTag = intent.result,
                        activeTab = AegisTab.SCANNER
                    )
                }
            }
            is AegisIntent.SaveNewOrUpdatedCard -> {
                viewModelScope.launch {
                    val now = System.currentTimeMillis()
                    val cardId = intent.id ?: UUID.randomUUID().toString()
                    val isSupported = intent.protocolType != CardProtocolType.MIFARE_CLASSIC
                    val profile = CardProfile(
                        id = cardId,
                        name = intent.name,
                        description = intent.description,
                        locationTag = intent.locationTag,
                        colorPrimary = intent.colorPrimary,
                        colorSecondary = intent.colorSecondary,
                        iconType = intent.iconType,
                        protocolType = intent.protocolType,
                        rawUidHex = intent.rawUidHex,
                        sakHex = if (isSupported) "20" else "08",
                        atqaHex = "0004",
                        atsHex = "7877B102",
                        primaryAid = intent.primaryAid,
                        isFavorite = false,
                        isHceSupported = isSupported,
                        createdAt = now,
                        updatedAt = now
                    )
                    val defaultPayloads = intent.customPayloads.ifEmpty {
                        listOf(
                            ApduPayload(
                                id = UUID.randomUUID().toString(),
                                cardProfileId = cardId,
                                commandApdu = "00A4040005${intent.primaryAid.take(10)}",
                                responsePayload = "${intent.rawUidHex}9000",
                                executionOrder = 0,
                                description = "Auto-generated SELECT AID response"
                            )
                        )
                    }
                    val result = cardProfileUseCase.saveCardProfile(profile, defaultPayloads)
                    result.onSuccess {
                        _uiState.update {
                            it.copy(
                                activeTab = AegisTab.WALLET,
                                statusBannerMessage = "Profil kartu '${intent.name}' berhasil disimpan terenkripsi."
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update { it.copy(statusBannerMessage = err.message) }
                    }
                }
            }
            is AegisIntent.ToggleFavorite -> {
                viewModelScope.launch {
                    cardProfileUseCase.toggleFavorite(intent.cardId, intent.isFavorite)
                }
            }
            is AegisIntent.DeleteCard -> {
                viewModelScope.launch {
                    cardProfileUseCase.deleteCardProfile(intent.cardId)
                }
            }
            is AegisIntent.ExportEncryptedBackup -> {
                viewModelScope.launch {
                    val res = repository.exportEncryptedBackupJson(intent.password.toCharArray())
                    res.onSuccess { json ->
                        _uiState.update {
                            it.copy(
                                exportedBackupJson = json,
                                statusBannerMessage = "Backup JSON terenkripsi (AES-256-GCM + PBKDF2 100k iterasi) siap disalin."
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update { it.copy(statusBannerMessage = "Gagal ekspor: ${err.message}") }
                    }
                }
            }
            is AegisIntent.ImportEncryptedBackup -> {
                viewModelScope.launch {
                    val res = repository.importEncryptedBackupJson(
                        intent.jsonPayload,
                        intent.password.toCharArray()
                    )
                    res.onSuccess { count ->
                        _uiState.update {
                            it.copy(statusBannerMessage = "Berhasil memulihkan $count profil kartu dari backup terenkripsi.")
                        }
                    }.onFailure { err ->
                        _uiState.update { it.copy(statusBannerMessage = "Gagal dekripsi backup: ${err.message}") }
                    }
                }
            }
            is AegisIntent.ExecuteEmergencyPanicWipe -> {
                viewModelScope.launch {
                    repository.executeEmergencyPanicWipe()
                }
            }
            is AegisIntent.DismissStatusBanner -> {
                _uiState.update { it.copy(statusBannerMessage = null) }
            }
        }
    }
}
