# AegisPass HCE: Zero-Root Virtual Access Card Emulator

**AegisPass HCE** adalah aplikasi utilitas Android non-root berkinerja tinggi (<4.5 MB Release APK) dan sepenuhnya *offline-first* yang memanfaatkan arsitektur resmi **Android Host-based Card Emulation (HCE)** sesuai standar ISO/IEC 7816-4 dan ISO/IEC 14443-4 (ISO-DEP).

---

## Arsitektur & Fitur Utama

1. **100% Non-Root HCE (`AegisHostApduService`)**
   - Mengimplementasikan `android.nfc.cardemulation.HostApduService` resmi.
   - Merespons perintah APDU `SELECT AID` (`00 A4 04 00`) dan `READ BINARY` / `GET DATA` (`00 B0` / `00 CA` / `80 CA`) dengan target latensi **< 20 ms** (SLA < 35 ms).
   - Menolak transaksi dengan status word `0x6F00` apabila sesi emulasi belum di-*arm* melalui Biometric Gate atau telah melewati batas waktu (timeout).

2. **NFC Tag Reader & Auto-Detection Engine (`NfcTagReaderManager`)**
   - Menggunakan `NfcAdapter.enableReaderMode` (`FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK`).
   - Mengekstraksi `UID` (4/7/10-byte), `SAK`, `ATQA`, dan `ATS` (Answer to Select).
   - **HCE Compatibility Checker**:
     - `IsoDep` (ISO 14443-4 / DESFire / JavaCard): **100% Fully Compatible (HCE Supported)**.
     - `MifareClassic` (Crypto-1 hardware UID): **Hardware Incompatible (Non-Root HCE Restricted)** dengan peringatan teknis transparan.
     - `MifareUltralight` / NTAG: **Partially Compatible (UID-Only Dependency)**.

3. **Biometric-Gated Security & Volatile Memory Scrubbing (`EmulationMemoryBuffer`)**
   - Wajib autentikasi `BiometricPrompt` (`BIOMETRIC_STRONG | DEVICE_CREDENTIAL`) untuk setiap sesi aktivasi emulasi (30–60 detik).
   - Kredensial disimpan di memori kerja menggunakan `ByteArray` dan langsung di-*scrub* (`Arrays.fill(bytes, 0.toByte())`) segera setelah countdown berakhir, RF link terputus, atau sesi dimatikan.

4. **Database Terenkripsi & Portabilitas Kriptografis (`AegisDatabase`, `KeystoreCryptoManager`)**
   - Room Database v2.6+ dengan **SQLCipher 256-bit AES-CBC** (`card_profiles`, `card_apdu_payloads`, `emulation_audit_logs`, `reader_diagnostics`).
   - Kunci master disimpan di **Android Keystore** (`StrongBox` dengan fallback `TEE` AES-256-GCM).
   - Ekspor/Impor Backup JSON terenkripsi sesuai skema **AES-256-GCM + PBKDF2WithHmacSHA256** (100.000 iterasi).
   - Fitur **Emergency Panic Wipe** untuk menghapus database, SharedPreferences, merusak kunci Keystore, dan menghentikan proses (`exitProcess(0)`).

---

## Struktur Proyek (Clean Architecture + MVI)

```text
AegisPass-HCE-Android/
├── .github/workflows/android-release.yml   # Pipeline CI/CD Detekt, Lint & Signed APK
├── gradle/libs.versions.toml               # Katalog Versi (Kotlin 2.0, Compose, Hilt, Room)
├── build.gradle.kts                        # Konfigurasi Root Gradle
├── settings.gradle.kts                     # Pengaturan Modul Proyek
└── app/
    ├── build.gradle.kts                    # Konfigurasi App, R8 Full Mode & Dependensi
    ├── proguard-rules.pro                  # Aturan Shrinking R8/ProGuard & SQLCipher
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml         # Deklarasi NFC, Biometric, HCE Service & TileService
        │   ├── res/xml/apduservice.xml     # Routing Tabel AID (Category: other)
        │   ├── res/xml/shortcuts.xml       # Dynamic App Shortcuts
        │   └── java/com/aegispass/hce/
        │       ├── AegisApplication.kt
        │       ├── domain/
        │       │   ├── model/              # CardProfile, ApduResponseHelper, Entities
        │       │   ├── engine/             # HceEngineContract, EmulationMemoryBuffer
        │       │   ├── repository/         # CardRepository Contract
        │       │   └── usecase/            # ProtocolParserUseCase, CardProfileUseCase
        │       ├── data/
        │       │   ├── local/              # Room Entities, AegisDao, AegisDatabase (SQLCipher)
        │       │   ├── security/           # KeystoreCryptoManager, PBKDF2 Backup, Tamper Check
        │       │   ├── hce/                # AegisHostApduService, NfcTagReaderManager, TileService
        │       │   └── repository/         # CardRepositoryImpl
        │       ├── di/                     # Dagger-Hilt AppModule
        │       └── presentation/
        │           ├── MainActivity.kt     # FLAG_SECURE, BiometricPrompt, ReaderMode, Haptics
        │           ├── mvi/                # AegisContract, AegisViewModel
        │           └── ui/                 # Jetpack Compose Screens, Hex Viewer, OLED Theme
        └── test/java/com/aegispass/hce/
            └── AegisVerificationTest.kt    # Unit Test 4 Skenario Verifikasi PRD Bab 10
```

## Cara Membangun (Build Instructions)

1. Buka folder `AegisPass-HCE-Android` menggunakan **Android Studio Ladybug (2024.2.1)+** dengan **JDK 17**.
2. Jalankan sinkronisasi Gradle (`./gradlew tasks`).
3. Untuk menjalankan unit test verifikasi protokol, biometrik, session expiry, dan memory scrubbing:
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. Untuk mengompilasi APK Release teroptimasi R8 (`< 4.5 MB`):
   ```bash
   ./gradlew assembleRelease
   ```
