package com.aegispass.hce.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegispass.hce.domain.model.CardIconType
import com.aegispass.hce.domain.model.CardProfile
import com.aegispass.hce.domain.model.CardProtocolType
import com.aegispass.hce.domain.usecase.ProtocolParserUseCase
import com.aegispass.hce.presentation.mvi.AegisIntent
import com.aegispass.hce.presentation.mvi.AegisTab
import com.aegispass.hce.presentation.mvi.AegisUiState

@Composable
fun AegisMainScreen(
    state: AegisUiState,
    onIntent: (AegisIntent) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = state.activeTab == AegisTab.WALLET,
                    onClick = { onIntent(AegisIntent.SelectTab(AegisTab.WALLET)) },
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = "Dompet Kartu HCE") },
                    label = { Text("Kartu") }
                )
                NavigationBarItem(
                    selected = state.activeTab == AegisTab.SCANNER,
                    onClick = { onIntent(AegisIntent.SelectTab(AegisTab.SCANNER)) },
                    icon = { Icon(Icons.Default.Nfc, contentDescription = "Pemindai Tag NFC") },
                    label = { Text("Scan NFC") }
                )
                NavigationBarItem(
                    selected = state.activeTab == AegisTab.APDU_INSPECTOR,
                    onClick = { onIntent(AegisIntent.SelectTab(AegisTab.APDU_INSPECTOR)) },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Inspektur APDU & Hex") },
                    label = { Text("APDU Hex") }
                )
                NavigationBarItem(
                    selected = state.activeTab == AegisTab.DIAGNOSTICS,
                    onClick = { onIntent(AegisIntent.SelectTab(AegisTab.DIAGNOSTICS)) },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Diagnostik Reader") },
                    label = { Text("Diagnostik") }
                )
                NavigationBarItem(
                    selected = state.activeTab == AegisTab.SECURITY_BACKUP,
                    onClick = { onIntent(AegisIntent.SelectTab(AegisTab.SECURITY_BACKUP)) },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Keamanan & Backup") },
                    label = { Text("Vault") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            state.statusBannerMessage?.let { banner ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(banner, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        OutlinedButton(onClick = { onIntent(AegisIntent.DismissStatusBanner) }) {
                            Text("Tutup")
                        }
                    }
                }
            }

            when (state.activeTab) {
                AegisTab.WALLET -> WalletAndEmulationView(state, onIntent)
                AegisTab.SCANNER -> NfcScannerAndCreatorView(state, onIntent)
                AegisTab.APDU_INSPECTOR -> ApduHexInspectorView(state, onIntent)
                AegisTab.DIAGNOSTICS -> DiagnosticsAndAuditView(state)
                AegisTab.SECURITY_BACKUP -> SecurityBackupAndPanicView(state, onIntent)
            }
        }
    }
}

@Composable
private fun WalletAndEmulationView(
    state: AegisUiState,
    onIntent: (AegisIntent) -> Unit
) {
    val activeCard: CardProfile? = state.selectedCard
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "AegisPass HCE • Zero-Root Access Vault",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (activeCard != null) {
            // Credit Card Aspect Ratio 1.586 (85.60 mm x 53.98 mm - PRD Phase 1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.586f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF0284C7))
                        )
                    )
                    .padding(20.dp)
                    .semantics { contentDescription = "Kartu Akses ${activeCard.name}" }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(activeCard.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                            Text(activeCard.locationTag, fontSize = 13.sp, color = Color(0xFFBAE6FD))
                        }
                        Text(
                            text = if (activeCard.isHceSupported) "ISO-DEP HCE" else "RESTRICTED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeCard.isHceSupported) Color(0xFF34D399) else Color(0xFFF87171)
                        )
                    }

                    Column {
                        Text(
                            text = "AID: ${activeCard.primaryAid}  |  UID: ${activeCard.rawUidHex}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "SAK: ${activeCard.sakHex} • ATQA: ${activeCard.atqaHex} • ATS: ${activeCard.atsHex}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // Biometric Emulation Trigger & Countdown Indicator
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.sessionState.isActive) {
                        CircularProgressIndicator(
                            progress = {
                                state.sessionState.remainingSeconds.toFloat() /
                                    state.sessionState.totalSeconds.coerceAtLeast(1).toFloat()
                            }
                        )
                        Text(
                            text = "EMULASI AKTIF: ${state.sessionState.remainingSeconds}s tersisa",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                        Text(
                            text = "Tempelkan bagian belakang smartphone ke Door Reader ISO 14443-4.",
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { onIntent(AegisIntent.DisarmEmulation) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Matikan & Scrub Memori (0x00)")
                        }
                    } else {
                        Button(
                            onClick = { onIntent(AegisIntent.RequestBiometricArm(activeCard.id)) },
                            enabled = activeCard.isHceSupported,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Arm Emulation (Biometric Strong Gate)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NfcScannerAndCreatorView(
    state: AegisUiState,
    onIntent: (AegisIntent) -> Unit
) {
    var name by remember { mutableStateOf("Kartu Akses Gedung Baru") }
    var location by remember { mutableStateOf("Lobby Utama") }
    var aid by remember { mutableStateOf(state.lastScannedTag?.discoveredAidHex ?: "F001020304") }
    var uid by remember { mutableStateOf(state.lastScannedTag?.rawUidHex ?: "04112233445566") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pemindai Tag NFC & Evaluasi Protokol", style = MaterialTheme.typography.titleMedium)
        state.lastScannedTag?.let { scanned ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(scanned.compatibilityStatus.badgeLabel, fontWeight = FontWeight.Bold)
                    Text(scanned.compatibilityStatus.technicalMessage, fontSize = 12.sp)
                }
            }
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Kartu") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Lokasi Fasilitas") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = aid, onValueChange = { aid = it }, label = { Text("Primary AID (HEX)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = uid, onValueChange = { uid = it }, label = { Text("UID / Payload Token (HEX)") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                onIntent(
                    AegisIntent.SaveNewOrUpdatedCard(
                        id = null,
                        name = name,
                        description = "Profil HCE Kustom",
                        locationTag = location,
                        colorPrimary = "#0F172A",
                        colorSecondary = "#1E40AF",
                        iconType = CardIconType.OFFICE,
                        protocolType = CardProtocolType.ISO_DEP,
                        rawUidHex = uid,
                        primaryAid = aid,
                        customPayloads = emptyList()
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enkripsi & Simpan ke Room SQLCipher")
        }
    }
}

@Composable
private fun ApduHexInspectorView(
    state: AegisUiState,
    onIntent: (AegisIntent) -> Unit
) {
    val parser = remember { ProtocolParserUseCase() }
    var testCommandHex by remember { mutableStateOf("00A4040005F00102030400") }
    val dumpLines = remember(testCommandHex) { parser.formatHexDumpSplitView(testCommandHex) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("APDU Command Simulator & Split Hex Viewer", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = testCommandHex,
            onValueChange = { testCommandHex = it },
            label = { Text("C-APDU Command (HEX)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onIntent(AegisIntent.SimulateDoorReaderApdu(testCommandHex)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kirim C-APDU ke AegisHostApduService")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(dumpLines) { line ->
                Text(
                    text = "${line.offsetHex}  |  ${line.hexChunk}  |  ${line.asciiRepresentation}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsAndAuditView(state: AegisUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Audit Log Transaksi APDU & Latensi (< 35 ms SLA)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.auditLogs) { log ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Status: ${log.status} • Latensi: ${log.latencyMs} ms", fontWeight = FontWeight.Bold)
                        Text(log.apduTraceJson, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityBackupAndPanicView(
    state: AegisUiState,
    onIntent: (AegisIntent) -> Unit
) {
    var backupPassword by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Portabilitas Terenkripsi (PBKDF2) & Panic Wipe", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = backupPassword,
            onValueChange = { backupPassword = it },
            label = { Text("Password Enkripsi Backup (AES-256-GCM)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onIntent(AegisIntent.ExportEncryptedBackup(backupPassword)) },
            enabled = backupPassword.length >= 8,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ekspor Backup JSON (100.000 Iterasi PBKDF2)")
        }
        state.exportedBackupJson?.let { json ->
            Text(json, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 8)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onIntent(AegisIntent.ExecuteEmergencyPanicWipe) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EMERGENCY PANIC WIPE (Hapus Keystore & DB)")
        }
    }
}
