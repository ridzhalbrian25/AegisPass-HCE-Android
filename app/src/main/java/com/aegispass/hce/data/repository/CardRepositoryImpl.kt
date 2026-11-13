package com.aegispass.hce.data.repository

import android.content.Context
import com.aegispass.hce.data.local.AegisDatabase
import com.aegispass.hce.data.local.dao.AegisDao
import com.aegispass.hce.data.local.entity.CardApduPayloadEntity
import com.aegispass.hce.data.local.entity.CardProfileEntity
import com.aegispass.hce.data.local.entity.EmulationAuditLogEntity
import com.aegispass.hce.data.local.entity.ReaderDiagnosticEntity
import com.aegispass.hce.data.security.KeystoreCryptoManager
import com.aegispass.hce.domain.engine.EmulationMemoryBuffer
import com.aegispass.hce.domain.model.ApduPayload
import com.aegispass.hce.domain.model.AuditStatus
import com.aegispass.hce.domain.model.CardIconType
import com.aegispass.hce.domain.model.CardProfile
import com.aegispass.hce.domain.model.CardProtocolType
import com.aegispass.hce.domain.model.EmulationAuditLog
import com.aegispass.hce.domain.model.ReaderDiagnostic
import com.aegispass.hce.domain.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import kotlin.system.exitProcess

class CardRepositoryImpl @Inject constructor(
    private val context: Context,
    private val dao: AegisDao,
    private val cryptoManager: KeystoreCryptoManager,
    private val memoryBuffer: EmulationMemoryBuffer
) : CardRepository {

    override fun observeAllProfiles(): Flow<List<CardProfile>> {
        return dao.observeAllProfiles().map { list ->
            list.map { entity -> entity.toDomain() }
        }
    }

    override fun observeAuditLogs(cardId: String?): Flow<List<EmulationAuditLog>> {
        return dao.observeRecentAuditLogs().map { list ->
            list.filter { cardId == null || it.cardProfileId == cardId }
                .map { entity ->
                    EmulationAuditLog(
                        id = entity.id,
                        cardProfileId = entity.cardProfileId,
                        readerAidReq = entity.readerAidReq ?: "",
                        latencyMs = entity.latencyMs,
                        status = runCatching { AuditStatus.valueOf(entity.status) }
                            .getOrDefault(AuditStatus.FAILED_TIMEOUT),
                        timestamp = entity.timestamp,
                        apduTraceJson = entity.apduTraceJson ?: "{}"
                    )
                }
        }
    }

    override fun observeReaderDiagnostics(): Flow<List<ReaderDiagnostic>> {
        return dao.observeReaderDiagnostics().map { list ->
            list.map { entity ->
                ReaderDiagnostic(
                    id = entity.id,
                    cardProfileId = entity.cardProfileId,
                    detectedVendor = entity.detectedVendor,
                    terminalSigHex = entity.terminalSigHex,
                    successCount = entity.successCount,
                    failureCount = entity.failureCount,
                    lastInteracted = entity.lastInteracted
                )
            }
        }
    }

    override suspend fun getProfileWithPayloads(cardId: String): Pair<CardProfile, List<ApduPayload>>? =
        withContext(Dispatchers.IO) {
            val entity = dao.getProfileById(cardId) ?: return@withContext null
            val payloads = dao.getPayloadsForCard(cardId).map { it.toDomain() }
            entity.toDomain(payloads) to payloads
        }

    override suspend fun upsertProfile(profile: CardProfile, payloads: List<ApduPayload>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entity = profile.toEntity()
                val payloadEntities = payloads.map { it.toEntity(profile.id) }
                dao.upsertProfileWithPayloads(entity, payloadEntities)
            }
        }

    override suspend fun setFavorite(cardId: String, isFavorite: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                dao.updateFavoriteStatus(cardId, if (isFavorite) 1 else 0, System.currentTimeMillis())
            }
        }

    override suspend fun deleteProfile(cardId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                dao.deleteProfileById(cardId)
            }
        }

    override suspend fun recordAuditLogToDb(
        log: EmulationAuditLog,
        detectedVendor: String,
        terminalSigHex: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            dao.insertAuditLog(
                EmulationAuditLogEntity(
                    id = log.id,
                    cardProfileId = log.cardProfileId,
                    readerAidReq = log.readerAidReq,
                    latencyMs = log.latencyMs,
                    status = log.status.name,
                    timestamp = log.timestamp,
                    apduTraceJson = log.apduTraceJson
                )
            )
            dao.insertReaderDiagnostic(
                ReaderDiagnosticEntity(
                    id = UUID.randomUUID().toString(),
                    cardProfileId = log.cardProfileId,
                    detectedVendor = detectedVendor,
                    terminalSigHex = terminalSigHex,
                    successCount = if (log.status == AuditStatus.SUCCESS) 1 else 0,
                    failureCount = if (log.status != AuditStatus.SUCCESS) 1 else 0,
                    lastInteracted = log.timestamp
                )
            )
        }
        Unit
    }

    override suspend fun exportEncryptedBackupJson(password: CharArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val profiles = dao.observeAllProfiles().first()
                val array = JSONArray()
                for (p in profiles) {
                    val payloads = dao.getPayloadsForCard(p.id)
                    val payloadArray = JSONArray()
                    payloads.forEach { apdu ->
                        payloadArray.put(
                            JSONObject().apply {
                                put("id", apdu.id)
                                put("command_apdu", apdu.commandApdu)
                                put("response_payload", apdu.responsePayload)
                                put("execution_order", apdu.executionOrder)
                                put("description", apdu.description ?: "")
                            }
                        )
                    }
                    array.put(
                        JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("description", p.description ?: "")
                            put("location_tag", p.locationTag ?: "")
                            put("color_primary", p.colorPrimary)
                            put("color_secondary", p.colorSecondary)
                            put("icon_type", p.iconType)
                            put("protocol_type", p.protocolType)
                            put("raw_uid_hex", p.rawUidHex)
                            put("sak_hex", p.sakHex ?: "20")
                            put("atqa_hex", p.atqaHex ?: "0004")
                            put("ats_hex", p.atsHex ?: "7877B102")
                            put("primary_aid", p.primaryAid ?: "F001020304")
                            put("is_favorite", p.isFavorite)
                            put("is_hce_supported", p.isHceSupported)
                            put("payloads", payloadArray)
                        }
                    )
                }
                val plainJson = JSONObject().put("profiles", array).toString()
                cryptoManager.createPortableEncryptedBackup(plainJson, password)
            }
        }

    override suspend fun importEncryptedBackupJson(
        jsonPayload: String,
        password: CharArray
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val plainJson = cryptoManager.decryptPortableBackup(jsonPayload, password)
            val root = JSONObject(plainJson)
            val array = root.getJSONArray("profiles")
            val now = System.currentTimeMillis()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val cardId = obj.optString("id", UUID.randomUUID().toString())
                val profileEntity = CardProfileEntity(
                    id = cardId,
                    name = obj.getString("name"),
                    description = obj.optString("description", ""),
                    locationTag = obj.optString("location_tag", ""),
                    colorPrimary = obj.optString("color_primary", "#0F172A"),
                    colorSecondary = obj.optString("color_secondary", "#1E40AF"),
                    iconType = obj.optString("icon_type", "APARTMENT"),
                    protocolType = obj.optString("protocol_type", "ISO_DEP"),
                    rawUidHex = obj.getString("raw_uid_hex"),
                    sakHex = obj.optString("sak_hex", "20"),
                    atqaHex = obj.optString("atqa_hex", "0004"),
                    atsHex = obj.optString("ats_hex", "7877B102"),
                    primaryAid = obj.optString("primary_aid", "F001020304"),
                    isFavorite = obj.optInt("is_favorite", 0),
                    isHceSupported = obj.optInt("is_hce_supported", 1),
                    createdAt = now,
                    updatedAt = now
                )
                val payloadArr = obj.optJSONArray("payloads") ?: JSONArray()
                val payloads = mutableListOf<CardApduPayloadEntity>()
                for (j in 0 until payloadArr.length()) {
                    val pObj = payloadArr.getJSONObject(j)
                    payloads.add(
                        CardApduPayloadEntity(
                            id = pObj.optString("id", UUID.randomUUID().toString()),
                            cardProfileId = cardId,
                            commandApdu = pObj.getString("command_apdu"),
                            responsePayload = pObj.getString("response_payload"),
                            executionOrder = pObj.optInt("execution_order", j),
                            description = pObj.optString("description", "")
                        )
                    )
                }
                dao.upsertProfileWithPayloads(profileEntity, payloads)
            }
            array.length()
        }
    }

    override suspend fun executeEmergencyPanicWipe(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            memoryBuffer.disarmAndScrub()
            dao.wipeAllProfiles()
            cryptoManager.destroyAllKeysAndPreferences()
            context.deleteDatabase(AegisDatabase.DATABASE_NAME)
            exitProcess(0)
        }
    }

    private fun CardProfileEntity.toDomain(payloads: List<ApduPayload> = emptyList()): CardProfile {
        return CardProfile(
            id = id,
            name = name,
            description = description ?: "",
            locationTag = locationTag ?: "",
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            iconType = runCatching { CardIconType.valueOf(iconType) }.getOrDefault(CardIconType.APARTMENT),
            protocolType = runCatching { CardProtocolType.valueOf(protocolType) }.getOrDefault(CardProtocolType.ISO_DEP),
            rawUidHex = rawUidHex,
            sakHex = sakHex ?: "20",
            atqaHex = atqaHex ?: "0004",
            atsHex = atsHex ?: "7877B102",
            primaryAid = primaryAid ?: "F001020304",
            isFavorite = isFavorite == 1,
            isHceSupported = isHceSupported == 1,
            createdAt = createdAt,
            updatedAt = updatedAt,
            payloads = payloads
        )
    }

    private fun CardProfile.toEntity(): CardProfileEntity {
        return CardProfileEntity(
            id = id,
            name = name,
            description = description,
            locationTag = locationTag,
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            iconType = iconType.name,
            protocolType = protocolType.name,
            rawUidHex = rawUidHex,
            sakHex = sakHex,
            atqaHex = atqaHex,
            atsHex = atsHex,
            primaryAid = primaryAid,
            isFavorite = if (isFavorite) 1 else 0,
            isHceSupported = if (isHceSupported) 1 else 0,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun CardApduPayloadEntity.toDomain(): ApduPayload {
        return ApduPayload(
            id = id,
            cardProfileId = cardProfileId,
            commandApdu = commandApdu,
            responsePayload = responsePayload,
            executionOrder = executionOrder,
            description = description ?: ""
        )
    }

    private fun ApduPayload.toEntity(cardId: String): CardApduPayloadEntity {
        return CardApduPayloadEntity(
            id = id,
            cardProfileId = cardId,
            commandApdu = commandApdu.replace(" ", "").uppercase(),
            responsePayload = responsePayload.replace(" ", "").uppercase(),
            executionOrder = executionOrder,
            description = description
        )
    }
}
