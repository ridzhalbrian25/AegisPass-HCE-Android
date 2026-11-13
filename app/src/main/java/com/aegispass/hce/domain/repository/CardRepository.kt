package com.aegispass.hce.domain.repository

import com.aegispass.hce.domain.model.ApduPayload
import com.aegispass.hce.domain.model.CardProfile
import com.aegispass.hce.domain.model.EmulationAuditLog
import com.aegispass.hce.domain.model.ReaderDiagnostic
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun observeAllProfiles(): Flow<List<CardProfile>>
    fun observeAuditLogs(cardId: String? = null): Flow<List<EmulationAuditLog>>
    fun observeReaderDiagnostics(): Flow<List<ReaderDiagnostic>>
    suspend fun getProfileWithPayloads(cardId: String): Pair<CardProfile, List<ApduPayload>>?
    suspend fun upsertProfile(profile: CardProfile, payloads: List<ApduPayload>): Result<Unit>
    suspend fun setFavorite(cardId: String, isFavorite: Boolean): Result<Unit>
    suspend fun deleteProfile(cardId: String): Result<Unit>
    suspend fun recordAuditLogToDb(log: EmulationAuditLog, detectedVendor: String, terminalSigHex: String)
    suspend fun exportEncryptedBackupJson(password: CharArray): Result<String>
    suspend fun importEncryptedBackupJson(jsonPayload: String, password: CharArray): Result<Int>
    suspend fun executeEmergencyPanicWipe(): Result<Unit>
}
