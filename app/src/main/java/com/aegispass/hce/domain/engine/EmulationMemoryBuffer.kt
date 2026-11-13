package com.aegispass.hce.domain.engine

import com.aegispass.hce.domain.model.ApduResponseHelper
import com.aegispass.hce.domain.model.AuditStatus
import com.aegispass.hce.domain.model.EmulationAuditLog
import com.aegispass.hce.domain.model.EmulationSessionState
import com.aegispass.hce.domain.usecase.ProtocolParserUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * High-speed Volatile In-Memory HCE Execution Buffer.
 * - Strictly isolated from main UI thread and disk I/O during `processCommandApdu` (< 20 ms SLA).
 * - Implements NFR 6.1 Volatile Memory Scrubbing (`Arrays.fill(bytes, 0.toByte())`).
 * - Implements NFR 6.3 Circular Ring Buffer (max 100 entries) for real-time APDU transaction traces.
 */
class EmulationMemoryBuffer(
    private val protocolParser: ProtocolParserUseCase = ProtocolParserUseCase()
) {
    private val lock = ReentrantLock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdownJob: Job? = null

    @Volatile
    private var environmentCompromised: Boolean = false

    @Volatile
    private var sessionActive: Boolean = false

    @Volatile
    private var sessionExpiryEpochMs: Long = 0L

    private var activeCardId: String? = null
    private var activeCardName: String? = null
    private var activePrimaryAidBytes: ByteArray? = null
    private var activeDefaultTokenBytes: ByteArray? = null

    // Pre-parsed command-to-response byte lookup table for < 1ms resolution
    private val commandResponseTable = ConcurrentHashMap<String, ByteArray>()

    // Circular Ring Buffer (NFR 6.3: Max 100 entries FIFO)
    private val ringBufferCapacity = 100
    private val auditRingBuffer = ArrayDeque<EmulationAuditLog>(ringBufferCapacity)

    private val _sessionState = MutableStateFlow(EmulationSessionState())
    val sessionState: StateFlow<EmulationSessionState> = _sessionState.asStateFlow()

    private val _ringBufferFlow = MutableStateFlow<List<EmulationAuditLog>>(emptyList())
    val ringBufferFlow: StateFlow<List<EmulationAuditLog>> = _ringBufferFlow.asStateFlow()

    fun setEnvironmentCompromised(compromised: Boolean) {
        environmentCompromised = compromised
        if (compromised) {
            scrubAndResetInternal()
        }
    }

    /**
     * Loads decrypted card profile credentials into volatile ByteArray memory ONLY after
     * strong biometric authentication succeeds.
     */
    fun loadSessionIntoVolatileMemory(
        cardId: String,
        cardName: String,
        primaryAidHex: String,
        defaultTokenHex: String,
        customApduMap: Map<String, String>,
        timeoutSeconds: Int,
        biometricAuthenticated: Boolean
    ): Result<EmulationSessionState> = lock.withLock {
        if (environmentCompromised) {
            return Result.failure(SecurityException("Lingkungan perangkat terdeteksi tidak aman (Tamper/Hooking detected)."))
        }
        if (!biometricAuthenticated) {
            return Result.failure(SecurityException("Aktivasi ditolak: BiometricPrompt (BIOMETRIC_STRONG) belum terverifikasi."))
        }

        // Scrub any previous session bytes first
        scrubAndResetInternal()

        val clampedTimeout = timeoutSeconds.coerceIn(5, 60)
        activeCardId = cardId
        activeCardName = cardName
        activePrimaryAidBytes = ApduResponseHelper.hexToBytes(primaryAidHex)
        activeDefaultTokenBytes = ApduResponseHelper.hexToBytes(defaultTokenHex)

        customApduMap.forEach { (cmdHex, respHex) ->
            val normalizedCmd = cmdHex.replace(" ", "").uppercase()
            commandResponseTable[normalizedCmd] = ApduResponseHelper.hexToBytes(respHex)
        }

        sessionActive = true
        sessionExpiryEpochMs = System.currentTimeMillis() + (clampedTimeout * 1000L)

        val newState = EmulationSessionState(
            isActive = true,
            activeCardId = cardId,
            activeCardName = cardName,
            activeAidHex = primaryAidHex.uppercase(),
            remainingSeconds = clampedTimeout,
            totalSeconds = clampedTimeout,
            totalTransactionsProcessed = 0,
            lastLatencyMs = 0L,
            lastStatusWordHex = "9000 (ARMED)"
        )
        _sessionState.value = newState

        startCountdownTimer(clampedTimeout)
        return Result.success(newState)
    }

    private fun startCountdownTimer(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            var remaining = seconds
            while (remaining > 0 && sessionActive) {
                delay(1000L)
                remaining--
                lock.withLock {
                    if (sessionActive) {
                        _sessionState.value = _sessionState.value.copy(remainingSeconds = remaining)
                    }
                }
            }
            if (remaining <= 0) {
                disarmAndScrub()
            }
        }
    }

    /**
     * Checks if session is currently armed and within its valid countdown window.
     */
    fun isEmulationSessionActive(): Boolean {
        if (environmentCompromised || !sessionActive) return false
        if (System.currentTimeMillis() > sessionExpiryEpochMs) {
            disarmAndScrub()
            return false
        }
        return true
    }

    /**
     * Resolves an incoming C-APDU into an R-APDU in < 1 ms using volatile memory.
     */
    fun resolveResponseForCommand(commandApdu: ByteArray): ByteArray? = lock.withLock {
        if (!isEmulationSessionActive()) {
            return ApduResponseHelper.STATUS_CONDITIONS_NOT_SATISFIED
        }

        val cmdHex = ApduResponseHelper.bytesToHex(commandApdu)

        // 1. Check exact custom APDU state table first
        commandResponseTable[cmdHex]?.let { matched ->
            incrementTransactionCounter("9000")
            return matched.copyOf()
        }

        // 2. Parse structured ISO 7816-4 command via ProtocolParserUseCase
        val parsed = protocolParser.parseCommandApdu(commandApdu)
        val primaryAid = activePrimaryAidBytes ?: return null
        val tokenPayload = activeDefaultTokenBytes ?: ByteArray(0)

        return when {
            parsed.isSelectByAid -> {
                if (parsed.dataField.contentEquals(primaryAid)) {
                    // Build FCI / Token response + 0x90 0x00
                    val response = ByteArray(tokenPayload.size + 2)
                    System.arraycopy(tokenPayload, 0, response, 0, tokenPayload.size)
                    response[tokenPayload.size] = 0x90.toByte()
                    response[tokenPayload.size + 1] = 0x00.toByte()
                    incrementTransactionCounter("9000")
                    response
                } else {
                    incrementTransactionCounter("6A82")
                    null // Caller maps null to STATUS_FILE_NOT_FOUND (0x6A82)
                }
            }
            parsed.isReadBinaryOrGetData -> {
                val response = ByteArray(tokenPayload.size + 2)
                System.arraycopy(tokenPayload, 0, response, 0, tokenPayload.size)
                response[tokenPayload.size] = 0x90.toByte()
                response[tokenPayload.size + 1] = 0x00.toByte()
                incrementTransactionCounter("9000")
                response
            }
            else -> {
                incrementTransactionCounter("6A82")
                null
            }
        }
    }

    private fun incrementTransactionCounter(statusWordHex: String) {
        val current = _sessionState.value
        _sessionState.value = current.copy(
            totalTransactionsProcessed = current.totalTransactionsProcessed + 1,
            lastStatusWordHex = statusWordHex
        )
    }

    fun recordAuditLog(
        commandApdu: ByteArray,
        responseApdu: ByteArray,
        latencyMs: Long
    ) = lock.withLock {
        val cHex = ApduResponseHelper.bytesToHex(commandApdu)
        val rHex = ApduResponseHelper.bytesToHex(responseApdu)
        val status = when {
            rHex.endsWith("9000") -> AuditStatus.SUCCESS
            rHex.endsWith("6A82") -> AuditStatus.FAILED_UNKNOWN_AID
            rHex.endsWith("6F00") -> AuditStatus.FAILED_UNARMED_SESSION
            else -> AuditStatus.FAILED_TIMEOUT
        }

        val entry = EmulationAuditLog(
            id = UUID.randomUUID().toString(),
            cardProfileId = activeCardId ?: "UNARMED",
            readerAidReq = cHex,
            latencyMs = latencyMs,
            status = status,
            timestamp = System.currentTimeMillis(),
            apduTraceJson = "{"c_apdu":"$cHex","r_apdu":"$rHex","latency_ms":$latencyMs}"
        )

        if (auditRingBuffer.size >= ringBufferCapacity) {
            auditRingBuffer.removeFirst()
        }
        auditRingBuffer.addLast(entry)
        _ringBufferFlow.value = auditRingBuffer.toList().reversed()
        _sessionState.value = _sessionState.value.copy(lastLatencyMs = latencyMs)
    }

    fun handleRfLinkLoss() {
        // Keep session armed if timer still remaining, or update state diagnostics
    }

    fun handleDeselected() {
        // ISO 14443-4 DESELECT callback
    }

    fun disarmAndScrub() = lock.withLock {
        countdownJob?.cancel()
        countdownJob = null
        scrubAndResetInternal()
    }

    private fun scrubAndResetInternal() {
        sessionActive = false
        sessionExpiryEpochMs = 0L
        activeCardId = null
        activeCardName = null

        // Explicit zero-fill of all sensitive byte arrays in heap
        ApduResponseHelper.scrubBytes(activePrimaryAidBytes)
        ApduResponseHelper.scrubBytes(activeDefaultTokenBytes)
        activePrimaryAidBytes = null
        activeDefaultTokenBytes = null

        commandResponseTable.values.forEach { bytes ->
            ApduResponseHelper.scrubBytes(bytes)
        }
        commandResponseTable.clear()

        _sessionState.value = EmulationSessionState(
            isActive = false,
            activeCardId = null,
            activeCardName = null,
            activeAidHex = null,
            remainingSeconds = 0,
            totalTransactionsProcessed = 0,
            lastLatencyMs = 0L,
            lastStatusWordHex = "6F00 (SCRUBBED)"
        )
    }

    /**
     * Exposed for Memory Hygiene Inspection Unit Tests (PRD Section 10.4)
     */
    internal fun inspectRawTokenBufferForTesting(): ByteArray? = activeDefaultTokenBytes
}
