package com.aegispass.hce.domain.usecase

import com.aegispass.hce.domain.model.ApduPayload
import com.aegispass.hce.domain.model.CardProfile
import com.aegispass.hce.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Domain UseCase enforcing strict input schema validation (AID length, HEX integrity)
 * and orchestrating CRUD operations on CardRepository.
 */
class CardProfileUseCase @Inject constructor(
    private val repository: CardRepository
) {
    private val hexRegex = Regex("^[0-9A-Fa-f]+$")

    fun observeAllProfiles(): Flow<List<CardProfile>> = repository.observeAllProfiles()

    suspend fun saveCardProfile(profile: CardProfile, payloads: List<ApduPayload>): Result<Unit> {
        // Strict schema validation
        if (profile.name.isBlank() || profile.name.length > 64) {
            return Result.failure(IllegalArgumentException("Nama profil kartu wajib diisi (1–64 karakter)."))
        }

        val cleanAid = profile.primaryAid.replace(" ", "").uppercase()
        if (cleanAid.length !in 10..32 || cleanAid.length % 2 != 0 || !hexRegex.matches(cleanAid)) {
            return Result.failure(
                IllegalArgumentException("Primary AID wajib berupa string HEX genap sepanjang 5–16 byte (10–32 karakter HEX).")
            )
        }

        val cleanUid = profile.rawUidHex.replace(" ", "").replace(":", "").uppercase()
        if (cleanUid.length !in listOf(8, 14, 20) || !hexRegex.matches(cleanUid)) {
            return Result.failure(
                IllegalArgumentException("UID kartu wajib sepanjang 4-byte (8 HEX), 7-byte (14 HEX), atau 10-byte (20 HEX).")
            )
        }

        for (p in payloads) {
            val cmd = p.commandApdu.replace(" ", "")
            val resp = p.responsePayload.replace(" ", "")
            if (cmd.isEmpty() || cmd.length % 2 != 0 || !hexRegex.matches(cmd)) {
                return Result.failure(IllegalArgumentException("Command APDU tidak valid: ${p.commandApdu}"))
            }
            if (resp.isEmpty() || resp.length % 2 != 0 || !hexRegex.matches(resp)) {
                return Result.failure(IllegalArgumentException("Response APDU tidak valid: ${p.responsePayload}"))
            }
        }

        return repository.upsertProfile(
            profile.copy(primaryAid = cleanAid, rawUidHex = cleanUid),
            payloads
        )
    }

    suspend fun toggleFavorite(cardId: String, isFavorite: Boolean): Result<Unit> {
        return repository.setFavorite(cardId, isFavorite)
    }

    suspend fun deleteCardProfile(cardId: String): Result<Unit> {
        return repository.deleteProfile(cardId)
    }
}
