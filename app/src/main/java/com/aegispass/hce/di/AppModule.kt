package com.aegispass.hce.di

import android.content.Context
import androidx.room.Room
import com.aegispass.hce.data.local.AegisDatabase
import com.aegispass.hce.data.local.dao.AegisDao
import com.aegispass.hce.data.repository.CardRepositoryImpl
import com.aegispass.hce.data.security.KeystoreCryptoManager
import com.aegispass.hce.domain.engine.EmulationMemoryBuffer
import com.aegispass.hce.domain.engine.HceEngineContract
import com.aegispass.hce.domain.model.EmulationSessionState
import com.aegispass.hce.domain.repository.CardRepository
import com.aegispass.hce.domain.usecase.ProtocolParserUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProtocolParserUseCase(): ProtocolParserUseCase = ProtocolParserUseCase()

    @Provides
    @Singleton
    fun provideEmulationMemoryBuffer(
        protocolParser: ProtocolParserUseCase
    ): EmulationMemoryBuffer = EmulationMemoryBuffer(protocolParser)

    @Provides
    @Singleton
    fun provideKeystoreCryptoManager(
        @ApplicationContext context: Context
    ): KeystoreCryptoManager = KeystoreCryptoManager(context)

    @Provides
    @Singleton
    fun provideAegisDatabase(
        @ApplicationContext context: Context,
        cryptoManager: KeystoreCryptoManager
    ): AegisDatabase {
        // Initialize hardware master key and encrypted SQLCipher 256-bit passphrase
        cryptoManager.getOrCreateHardwareMasterKey()
        val passphraseBytes = cryptoManager.getDatabasePassphraseBytes()
        val supportFactory = SupportFactory(passphraseBytes)

        return Room.databaseBuilder(
            context,
            AegisDatabase::class.java,
            AegisDatabase.DATABASE_NAME
        )
            .openHelperFactory(supportFactory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideAegisDao(db: AegisDatabase): AegisDao = db.aegisDao()

    @Provides
    @Singleton
    fun provideCardRepository(
        @ApplicationContext context: Context,
        dao: AegisDao,
        cryptoManager: KeystoreCryptoManager,
        memoryBuffer: EmulationMemoryBuffer
    ): CardRepository = CardRepositoryImpl(context, dao, cryptoManager, memoryBuffer)

    @Provides
    @Singleton
    fun provideHceEngineContract(
        memoryBuffer: EmulationMemoryBuffer,
        repository: CardRepository
    ): HceEngineContract = object : HceEngineContract {
        override suspend fun armEmulationSession(
            cardId: String,
            timeoutSeconds: Int,
            biometricVerified: Boolean
        ): Result<EmulationSessionState> {
            val pair = repository.getProfileWithPayloads(cardId)
                ?: return Result.failure(IllegalArgumentException("Profil kartu tidak ditemukan: $cardId"))
            val (profile, payloads) = pair
            if (!profile.isHceSupported) {
                return Result.failure(
                    IllegalStateException("Kartu ${profile.protocolType.displayName} tidak kompatibel dengan HCE non-root.")
                )
            }
            val customMap = payloads.associate { it.commandApdu to it.responsePayload }
            return memoryBuffer.loadSessionIntoVolatileMemory(
                cardId = profile.id,
                cardName = profile.name,
                primaryAidHex = profile.primaryAid,
                defaultTokenHex = profile.rawUidHex,
                customApduMap = customMap,
                timeoutSeconds = timeoutSeconds,
                biometricAuthenticated = biometricVerified
            )
        }

        override suspend fun disarmEmulationSession(): Result<Unit> = runCatching {
            memoryBuffer.disarmAndScrub()
        }

        override fun observeSessionState(): StateFlow<EmulationSessionState> =
            memoryBuffer.sessionState
    }
}
