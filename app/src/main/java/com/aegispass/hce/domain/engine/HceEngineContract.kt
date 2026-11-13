package com.aegispass.hce.domain.engine

import com.aegispass.hce.domain.model.EmulationSessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * PRD Section 5.2 Internal Service Interface Contract (Domain UseCase <-> HCE Engine)
 */
interface HceEngineContract {
    /**
     * Mengaktifkan sesi emulasi kartu tertentu ke dalam memori volatile.
     * Wajib disertai biometricAuthToken yang tervalidasi dari BiometricPrompt.
     * @param cardId UUID unik profil kartu.
     * @param timeoutSeconds Durasi aktif sebelum session dibatalkan otomatis (30-60s).
     * @param biometricVerified Flag bukti bahwa callback BiometricPrompt sukses.
     */
    suspend fun armEmulationSession(
        cardId: String,
        timeoutSeconds: Int = 30,
        biometricVerified: Boolean = false
    ): Result<EmulationSessionState>

    /**
     * Mematikan sesi emulasi seketika dan membersihkan buffer memori byte array (0x00 scrub).
     */
    suspend fun disarmEmulationSession(): Result<Unit>

    /**
     * Mengambil alur state reaktif status emulasi saat ini.
     */
    fun observeSessionState(): StateFlow<EmulationSessionState>
}
