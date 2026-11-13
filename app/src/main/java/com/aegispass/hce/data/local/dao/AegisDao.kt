package com.aegispass.hce.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aegispass.hce.data.local.entity.CardApduPayloadEntity
import com.aegispass.hce.data.local.entity.CardProfileEntity
import com.aegispass.hce.data.local.entity.EmulationAuditLogEntity
import com.aegispass.hce.data.local.entity.ReaderDiagnosticEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AegisDao {

    @Query("SELECT * FROM card_profiles ORDER BY is_favorite DESC, updated_at DESC")
    fun observeAllProfiles(): Flow<List<CardProfileEntity>>

    @Query("SELECT * FROM card_profiles WHERE id = :cardId LIMIT 1")
    suspend fun getProfileById(cardId: String): CardProfileEntity?

    @Query("SELECT * FROM card_apdu_payloads WHERE card_profile_id = :cardId ORDER BY execution_order ASC")
    suspend fun getPayloadsForCard(cardId: String): List<CardApduPayloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: CardProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayloads(payloads: List<CardApduPayloadEntity>)

    @Query("DELETE FROM card_apdu_payloads WHERE card_profile_id = :cardId")
    suspend fun deletePayloadsForCard(cardId: String)

    @Transaction
    suspend fun upsertProfileWithPayloads(
        profile: CardProfileEntity,
        payloads: List<CardApduPayloadEntity>
    ) {
        insertProfile(profile)
        deletePayloadsForCard(profile.id)
        insertPayloads(payloads)
    }

    @Query("UPDATE card_profiles SET is_favorite = :fav, updated_at = :updatedAt WHERE id = :cardId")
    suspend fun updateFavoriteStatus(cardId: String, fav: Int, updatedAt: Long)

    @Query("DELETE FROM card_profiles WHERE id = :cardId")
    suspend fun deleteProfileById(cardId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: EmulationAuditLogEntity)

    @Query("SELECT * FROM emulation_audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentAuditLogs(): Flow<List<EmulationAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaderDiagnostic(diagnostic: ReaderDiagnosticEntity)

    @Query("SELECT * FROM reader_diagnostics ORDER BY last_interacted DESC")
    fun observeReaderDiagnostics(): Flow<List<ReaderDiagnosticEntity>>

    @Query("DELETE FROM card_profiles")
    suspend fun wipeAllProfiles()
}
