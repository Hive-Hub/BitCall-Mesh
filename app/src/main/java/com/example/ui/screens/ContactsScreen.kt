package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.CryptoEngine
import com.example.crypto.QrCodeUtil
import com.example.data.local.ContactEntity
import com.example.ui.MainViewModel

@Composable
fun ContactsScreen(viewModel: MainViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<ContactEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = contacts.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.bitcallId.contains(searchQuery, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search verified contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("contact_search_bar"),
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.bitcallId }) { contact ->
                    ContactRowItem(
                        contact = contact,
                        onClick = { selectedContact = contact }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_contact_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Contact")
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { id, name, phone, pk, fp ->
                viewModel.addContact(id, name, phone, pk, fp)
                showAddDialog = false
            }
        )
    }

    if (selectedContact != null) {
        ContactDetailDialog(
            contact = selectedContact!!,
            onDismiss = { selectedContact = null },
            onVerifyToggle = {
                viewModel.setContactVerification(selectedContact!!.bitcallId, !selectedContact!!.isVerified)
                selectedContact = selectedContact!!.copy(isVerified = !selectedContact!!.isVerified)
            },
            onSetPassword = { pwd ->
                viewModel.setContactProtection(selectedContact!!.bitcallId, pwd)
                selectedContact = null
            },
            onDelete = {
                viewModel.deleteContact(selectedContact!!)
                selectedContact = null
            },
            onOpenChat = {
                val c = selectedContact!!
                selectedContact = null
                viewModel.openConversation(c)
            }
        )
    }
}

@Composable
fun ContactRowItem(contact: ContactEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("contact_item_${contact.bitcallId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.displayName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    if (contact.isVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (contact.isProtected) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Protected",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = contact.bitcallId + if (contact.phoneNumber != null) " • ${contact.phoneNumber}" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "FP: ${contact.fingerprint.take(23)}...",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (id: String, name: String, phone: String?, pk: String, fp: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var qrJsonInput by remember { mutableStateOf("") }
    var manualId by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var manualPhone by remember { mutableStateOf("") }
    var manualPubKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add BitCall Contact", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Import QR Data") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Manual Entry") }
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        "Paste or scan identity QR JSON payload exported from another BitCall device:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = qrJsonInput,
                        onValueChange = { qrJsonInput = it; error = null },
                        placeholder = { Text("{\"v\":1,\"id\":\"BC-...\",\"pk\":\"...\"}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("qr_payload_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            val mock = QrCodeUtil.ContactQrPayload(
                                bitcallId = "BC-" + java.util.UUID.randomUUID().toString().take(6).uppercase(),
                                displayName = "Nearby BitCall Peer",
                                publicKeyBase64 = CryptoEngine.encodePublicKey(CryptoEngine.generateEphemeralKeyPair().public),
                                fingerprint = "6B:2E:88:19:AC:40:99:FF:11:33:55:77:99:AA:BB:CC"
                            ).toJson()
                            qrJsonInput = mock
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simulate Scanned QR Code")
                    }
                } else {
                    OutlinedTextField(
                        value = manualId,
                        onValueChange = { manualId = it },
                        label = { Text("BitCall ID (e.g. BC-123456)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualPhone,
                        onValueChange = { manualPhone = it },
                        label = { Text("Phone Label (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualPubKey,
                        onValueChange = { manualPubKey = it },
                        label = { Text("Public Key (Base64)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        val parsed = QrCodeUtil.ContactQrPayload.fromJson(qrJsonInput.trim())
                        if (parsed != null) {
                            onAdd(parsed.bitcallId, parsed.displayName, null, parsed.publicKeyBase64, parsed.fingerprint)
                        } else {
                            error = "Invalid BitCall QR Code payload"
                        }
                    } else {
                        if (manualId.isBlank() || manualName.isBlank()) {
                            error = "ID and Name are required"
                        } else {
                            val pk = if (manualPubKey.isNotBlank()) manualPubKey else CryptoEngine.encodePublicKey(CryptoEngine.generateEphemeralKeyPair().public)
                            val fp = CryptoEngine.computeFingerprint(pk.toByteArray())
                            onAdd(manualId, manualName, manualPhone.ifBlank { null }, pk, fp)
                        }
                    }
                },
                modifier = Modifier.testTag("confirm_add_contact_button")
            ) {
                Text("Add Contact")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ContactDetailDialog(
    contact: ContactEntity,
    onDismiss: () -> Unit,
    onVerifyToggle: () -> Unit,
    onSetPassword: (String?) -> Unit,
    onDelete: () -> Unit,
    onOpenChat: () -> Unit
) {
    var showPasswordInput by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.displayName, fontWeight = FontWeight.Bold)
                if (contact.isVerified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("BitCall ID: ${contact.bitcallId}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Fingerprint:\n${contact.fingerprint}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onVerifyToggle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (contact.isVerified) "Mark Unverified" else "✓ Verify Public Key Fingerprint")
                }

                if (!showPasswordInput) {
                    OutlinedButton(
                        onClick = { showPasswordInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (contact.isProtected) "Change / Remove Password Lock" else "🔒 Protect Contact with Password")
                    }
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Set Password (leave empty to remove)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSetPassword(password.ifBlank { null }) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Password")
                        }
                        OutlinedButton(
                            onClick = { showPasswordInput = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenChat,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Chat")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
