package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.CryptoEngine
import com.example.crypto.QrCodeUtil
import com.example.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val identity by viewModel.userIdentity.collectAsState()
    val metrics by viewModel.meshMetrics.collectAsState()
    val relayEnabled by viewModel.relayEnabled.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var showQrDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupExportString by remember { mutableStateOf<String?>(null) }
    var copiedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Identity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Your Cryptographic Identity", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                if (identity != null) {
                    Column {
                        Text("Display Name: ${identity!!.displayName}", fontWeight = FontWeight.SemiBold)
                        Text("BitCall ID: ${identity!!.bitcallId}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }

                    Column {
                        Text("Key Fingerprint (SHA-256):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = identity!!.fingerprint,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 16.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showQrDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("show_my_qr_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Show QR")
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(identity!!.publicKeyBase64))
                                copiedNotice = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copiedNotice) "Copied!" else "Copy Key")
                        }
                    }
                }
            }
        }

        // Mesh Network Statistics
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Mesh Telemetry & Diagnostics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBox("Sent Packets", "${metrics.sentPackets}")
                    StatBox("Received", "${metrics.receivedPackets}")
                    StatBox("Relayed Hops", "${metrics.relayedPackets}")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBox("Duplicates Dropped", "${metrics.droppedDuplicates}")
                    StatBox("Est. Latency", "${metrics.averageLatencyMs} ms")
                    StatBox("Relay Mode", if (relayEnabled) "Active" else "Off")
                }
            }
        }

        // Relay Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Participate as Mesh Relay", fontWeight = FontWeight.SemiBold)
                    Text("Forward encrypted packets across the network for others without reading contents", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = relayEnabled,
                    onCheckedChange = { viewModel.toggleRelay(it) }
                )
            }
        }

        // Backup & Export
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text("Encrypted Identity Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("Export your cryptographic identity wrapped in an AES-256-GCM vault with a password.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Button(
                    onClick = { showBackupDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_backup_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Export Encrypted Backup")
                }
            }
        }
    }

    // QR Code Dialog
    if (showQrDialog && identity != null) {
        val qrPayload = QrCodeUtil.ContactQrPayload(
            bitcallId = identity!!.bitcallId,
            displayName = identity!!.displayName,
            publicKeyBase64 = identity!!.publicKeyBase64,
            fingerprint = identity!!.fingerprint
        ).toJson()

        val qrBitmap: Bitmap = remember(qrPayload) {
            QrCodeUtil.generateQrBitmap(qrPayload, 512)
        }

        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("My BitCall QR Code", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "My Identity QR Code",
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Scan this with another BitCall device to establish an authenticated end-to-end encrypted channel.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) { Text("Done") }
            }
        )
    }

    // Backup Dialog
    if (showBackupDialog && identity != null) {
        AlertDialog(
            onDismissRequest = {
                showBackupDialog = false
                backupExportString = null
                backupPassword = ""
            },
            title = { Text("Export Encrypted Backup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a passphrase to seal your private identity material:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Backup Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (backupExportString != null) {
                        Text("Encrypted Backup Vault String:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = backupExportString!!,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 4,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (backupPassword.isNotBlank()) {
                            val salt = CryptoEngine.generateRandomSalt()
                            val dataToSeal = "{\"id\":\"${identity!!.bitcallId}\",\"pk\":\"${identity!!.publicKeyBase64}\"}"
                            backupExportString = CryptoEngine.encryptWithPassword(dataToSeal, backupPassword, salt)
                            clipboardManager.setText(AnnotatedString(backupExportString!!))
                        }
                    }
                ) {
                    Text(if (backupExportString != null) "Copy Vault" else "Encrypt Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackupDialog = false
                    backupExportString = null
                    backupPassword = ""
                }) { Text("Close") }
            }
        )
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
