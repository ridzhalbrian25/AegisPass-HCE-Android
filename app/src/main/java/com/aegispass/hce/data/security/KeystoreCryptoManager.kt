package com.aegispass.hce.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aegispass.hce.domain.model.ApduResponseHelper
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * PRD Phase 2 & Section 5.3 / 6.1 Key & Secret Management:
 * - Hardware-Backed Android Keystore (StrongBox Keymaster with TEE fallback) AES-256-GCM.
 * - EncryptedSharedPreferences for SQLCipher 256-bit passphrase storage.
 * - Portable Encrypted JSON Backup (AES-256-GCM + PBKDF2WithHmacSHA256 >= 100,000 iterations).
 */
class KeystoreCryptoManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "aegispass_hardware_aes256_master"
        private const val PREFS_FILE_NAME = "aegispass_secure_prefs"
        private const val KEY_DB_PASSPHRASE = "sqlcipher_passphrase_hex"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100_000
    }

    private val secureRandom = SecureRandom()

    /**
     * Generates or retrieves the hardware-backed AES-256-GCM SecretKey from Android Keystore.
     */
    fun getOrCreateHardwareMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(MASTER_KEY_ALIAS, null)?.let { return it as SecretKey }

        val hasStrongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }

        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        return try {
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        } catch (_: Exception) {
            // Fallback to TEE if StrongBox provisioning fails on specific OEM firmware
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(false)
            }
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
    }

    /**
     * Retrieves or generates a 32-byte (256-bit) SQLCipher passphrase stored in EncryptedSharedPreferences.
     */
    fun getDatabasePassphraseBytes(): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        var passphraseHex = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (passphraseHex.isNullOrEmpty()) {
            val randomBytes = ByteArray(32)
            secureRandom.nextBytes(randomBytes)
            passphraseHex = ApduResponseHelper.bytesToHex(randomBytes)
            prefs.edit().putString(KEY_DB_PASSPHRASE, passphraseHex).commit()
            ApduResponseHelper.scrubBytes(randomBytes)
        }
        return ApduResponseHelper.hexToBytes(passphraseHex)
    }

    /**
     * Encrypts plaintext JSON payload into the PRD Section 5.3 RFC 7516 Subset Backup Schema.
     */
    fun createPortableEncryptedBackup(plainJson: String, password: CharArray): String {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }

        val derivedKey = deriveKeyPbkdf2(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
        val cipherBytes = cipher.doFinal(plainBytes)
        ApduResponseHelper.scrubBytes(plainBytes)

        val cryptoMetadata = JSONObject().apply {
            put("algorithm", "AES-256-GCM")
            put("kdf", PBKDF2_ALGORITHM)
            put("salt_hex", ApduResponseHelper.bytesToHex(salt))
            put("iv_hex", ApduResponseHelper.bytesToHex(iv))
            put("iterations", PBKDF2_ITERATIONS)
        }

        return JSONObject().apply {
            put("version", 1)
            put("export_timestamp", System.currentTimeMillis() / 1000L)
            put("crypto_metadata", cryptoMetadata)
            put("cipher_text", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }.toString(2)
    }

    /**
     * Decrypts a PRD Section 5.3 portable JSON backup using the user's password.
     */
    fun decryptPortableBackup(backupJsonStr: String, password: CharArray): String {
        val root = JSONObject(backupJsonStr)
        val version = root.getInt("version")
        require(version == 1) { "Versi skema backup tidak didukung: $version" }

        val meta = root.getJSONObject("crypto_metadata")
        val iterations = meta.getInt("iterations")
        require(iterations >= 100_000) { "Iterasi KDF di bawah standar keamanan minimum (100.000)." }

        val salt = ApduResponseHelper.hexToBytes(meta.getString("salt_hex"))
        val iv = ApduResponseHelper.hexToBytes(meta.getString("iv_hex"))
        val cipherBytes = Base64.decode(root.getString("cipher_text"), Base64.NO_WRAP)

        val derivedKey = deriveKeyPbkdf2(password, salt, iterations)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val decryptedBytes = cipher.doFinal(cipherBytes)
        val result = String(decryptedBytes, Charsets.UTF_8)
        ApduResponseHelper.scrubBytes(decryptedBytes)
        return result
    }

    private fun deriveKeyPbkdf2(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        val secretKey = SecretKeySpec(keyBytes, "AES")
        Arrays.fill(keyBytes, 0.toByte())
        return secretKey
    }

    /**
     * PRD Phase 2 Local Emergency Data Wipe ("Panic" Action):
     * Destroys Keystore Master Key and wipes EncryptedSharedPreferences.
     */
    fun destroyAllKeysAndPreferences() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        } catch (_: Exception) {
        }
        context.deleteSharedPreferences(PREFS_FILE_NAME)
    }
}
