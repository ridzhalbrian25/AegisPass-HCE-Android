package com.aegispass.hce.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PRD Section 4.2 TABEL 1: card_profiles
 */
@Entity(
    tableName = "card_profiles",
    indices = [
        Index(value = ["primary_aid"], name = "index_card_profiles_primary_aid"),
        Index(value = ["is_favorite"], name = "index_card_profiles_is_favorite")
    ]
)
data class CardProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "location_tag") val locationTag: String?,
    @ColumnInfo(name = "color_primary") val colorPrimary: String,
    @ColumnInfo(name = "color_secondary") val colorSecondary: String,
    @ColumnInfo(name = "icon_type", defaultValue = "'APARTMENT'") val iconType: String,
    @ColumnInfo(name = "protocol_type") val protocolType: String,
    @ColumnInfo(name = "raw_uid_hex") val rawUidHex: String,
    @ColumnInfo(name = "sak_hex") val sakHex: String?,
    @ColumnInfo(name = "atqa_hex") val atqaHex: String?,
    @ColumnInfo(name = "ats_hex") val atsHex: String?,
    @ColumnInfo(name = "primary_aid") val primaryAid: String?,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Int,
    @ColumnInfo(name = "is_hce_supported", defaultValue = "1") val isHceSupported: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * PRD Section 4.2 TABEL 2: card_apdu_payloads
 */
@Entity(
    tableName = "card_apdu_payloads",
    foreignKeys = [
        ForeignKey(
            entity = CardProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["card_profile_id"], name = "index_card_apdu_payloads_card_profile_id"),
        Index(value = ["card_profile_id", "command_apdu"], name = "index_card_apdu_unique_order", unique = true)
    ]
)
data class CardApduPayloadEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "card_profile_id") val cardProfileId: String,
    @ColumnInfo(name = "command_apdu") val commandApdu: String,
    @ColumnInfo(name = "response_payload") val responsePayload: String,
    @ColumnInfo(name = "execution_order", defaultValue = "0") val executionOrder: Int,
    @ColumnInfo(name = "description") val description: String?
)

/**
 * PRD Section 4.2 TABEL 3: emulation_audit_logs
 */
@Entity(
    tableName = "emulation_audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = CardProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["card_profile_id"], name = "index_emulation_audit_logs_card_profile_id"),
        Index(value = ["timestamp"], name = "index_emulation_audit_logs_timestamp")
    ]
)
data class EmulationAuditLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "card_profile_id") val cardProfileId: String,
    @ColumnInfo(name = "reader_aid_req") val readerAidReq: String?,
    @ColumnInfo(name = "latency_ms") val latencyMs: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "apdu_trace_json") val apduTraceJson: String?
)

/**
 * PRD Section 4.2 TABEL 4: reader_diagnostics
 */
@Entity(
    tableName = "reader_diagnostics",
    foreignKeys = [
        ForeignKey(
            entity = CardProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["card_profile_id"], name = "index_reader_diagnostics_card_profile_id")
    ]
)
data class ReaderDiagnosticEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "card_profile_id") val cardProfileId: String,
    @ColumnInfo(name = "detected_vendor", defaultValue = "'UNKNOWN'") val detectedVendor: String,
    @ColumnInfo(name = "terminal_sig_hex") val terminalSigHex: String,
    @ColumnInfo(name = "success_count", defaultValue = "0") val successCount: Int,
    @ColumnInfo(name = "failure_count", defaultValue = "0") val failureCount: Int,
    @ColumnInfo(name = "last_interacted") val lastInteracted: Long
)
