package com.aegispass.hce.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aegispass.hce.data.local.dao.AegisDao
import com.aegispass.hce.data.local.entity.CardApduPayloadEntity
import com.aegispass.hce.data.local.entity.CardProfileEntity
import com.aegispass.hce.data.local.entity.EmulationAuditLogEntity
import com.aegispass.hce.data.local.entity.ReaderDiagnosticEntity

@Database(
    entities = [
        CardProfileEntity::class,
        CardApduPayloadEntity::class,
        EmulationAuditLogEntity::class,
        ReaderDiagnosticEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun aegisDao(): AegisDao

    companion object {
        const val DATABASE_NAME = "aegispass_vault.db"
    }
}
