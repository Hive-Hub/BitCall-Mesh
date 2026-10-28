package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAssistantService
import com.example.audio.AudioEngine
import com.example.bluetooth.BluetoothTransport
import com.example.crypto.CryptoEngine
import com.example.crypto.QrCodeUtil
import com.example.data.local.BitCallDatabase
import com.example.data.local.CallRecordEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ContactEntity
import com.example.data.local.DevicePeerEntity
import com.example.data.local.SecurityPreferences
import com.example.data.local.UserIdentityEntity
import com.example.mesh.BitCallMeshService
import com.example.mesh.MeshMetrics
import com.example.mesh.MeshRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen {
    ONBOARDING,
    APP_LOCK,
    CHATS,
    CHAT_DETAIL,
    CONTACTS,
    NEARBY_RADAR,
    ACTIVE_CALL,
    AI_TOOLS,
    SETTINGS,
    DIAGNOSTICS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BitCallDatabase.getInstance(application)
    private val securityPrefs = SecurityPreferences(application)
    val transport = BluetoothTransport(application, viewModelScope)
    val meshRouter = MeshRouter(database, transport, securityPrefs, viewModelScope)
    val audioEngine = AudioEngine(application, viewModelScope)
    val aiService = AiAssistantService()

    // Screen navigation state
    private val _currentScreen = MutableStateFlow(AppScreen.CHATS)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Active conversation
    private val _activeContact = MutableStateFlow<ContactEntity?>(null)
    val activeContact: StateFlow<ContactEntity?> = _activeContact.asStateFlow()

    // Unlocked protected contacts set (in memory during session)
    private val _unlockedContactIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedContactIds: StateFlow<Set<String>> = _unlockedContactIds.asStateFlow()

    // App lock status
    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _lockoutRemainingSec = MutableStateFlow(0)
    val lockoutRemainingSec: StateFlow<Int> = _lockoutRemainingSec.asStateFlow()

    // User Identity flow
    val userIdentity: StateFlow<UserIdentityEntity?> = database.userIdentityDao().getUserIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Contacts & Messages flows
    val contacts: StateFlow<List<ContactEntity>> = database.contactDao().getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMessages: StateFlow<List<ChatMessageEntity>> = database.chatMessageDao().getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peers: StateFlow<Map<String, DevicePeerEntity>> = transport.connectedPeers
    val meshMetrics: StateFlow<MeshMetrics> = meshRouter.metrics
    val relayEnabled: StateFlow<Boolean> = securityPrefs.relayEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Active Call State
    private val _activeCallPeer = MutableStateFlow<ContactEntity?>(null)
    val activeCallPeer: StateFlow<ContactEntity?> = _activeCallPeer.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    // AI Status & Results
    private val _aiStatus = MutableStateFlow<String?>(null)
    val aiStatus: StateFlow<String?> = _aiStatus.asStateFlow()

    private val _aiLastResult = MutableStateFlow<String?>(null)
    val aiLastResult: StateFlow<String?> = _aiLastResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    init {
        checkAppLaunchState()
    }

    private fun checkAppLaunchState() {
        viewModelScope.launch(Dispatchers.IO) {
            val identity = database.userIdentityDao().getUserIdentitySync()
            val lockEnabled = securityPrefs.appLockEnabled.firstOrNull() ?: false

            if (identity == null || !identity.isConfigured) {
                _currentScreen.value = AppScreen.ONBOARDING
            } else if (lockEnabled && !_isAppUnlocked.value) {
                _currentScreen.value = AppScreen.APP_LOCK
            } else {
                _currentScreen.value = AppScreen.CHATS
                startMeshService()
            }
        }
    }

    fun startMeshService() {
        BitCallMeshService.startService(getApplication())
        transport.startDiscovery()
    }

    // ---------------------------------------------------------------------------------------------
    // ONBOARDING & IDENTITY CREATION
    // ---------------------------------------------------------------------------------------------

    fun completeOnboarding(displayName: String, pin: String?, relayEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitcallId = CryptoEngine.generateRandomBitcallId()
            val alias = "bitcall_key_$bitcallId"
            val keyPair = try {
                CryptoEngine.generateIdentityKeyPair(alias)
            } catch (e: Exception) {
                CryptoEngine.generateEphemeralKeyPair()
            }
            val pubKeyBase64 = CryptoEngine.encodePublicKey(keyPair.public)
            val fingerprint = CryptoEngine.computeFingerprint(keyPair.public)

            val identity = UserIdentityEntity(
                bitcallId = bitcallId,
                displayName = displayName.ifBlank { "BitCall Node" },
                publicKeyBase64 = pubKeyBase64,
                keyStoreAlias = alias,
                fingerprint = fingerprint,
                createdAt = System.currentTimeMillis(),
                isConfigured = true
            )
            database.userIdentityDao().insertUserIdentity(identity)

            if (!pin.isNullOrBlank()) {
                val hashed = CryptoEngine.hashPassword(pin)
                securityPrefs.setAppLock(hashed.hashBase64, hashed.saltBase64)
            } else {
                securityPrefs.disableAppLock()
            }

            securityPrefs.setRelayEnabled(relayEnabled)
            securityPrefs.setOnboardingCompleted(true)
            _isAppUnlocked.value = true
            _currentScreen.value = AppScreen.CHATS

            // Seed initial mock contacts and peers for immediate out-of-the-box exploration
            seedInitialDemoNetwork(bitcallId)
            startMeshService()
        }
    }

    private suspend fun seedInitialDemoNetwork(myBitcallId: String) {
        transport.seedSimulatedMeshPeers()
        val demoContact1 = ContactEntity(
            bitcallId = "BC-ALICE-99",
            displayName = "Alice (Phone Z)",
            phoneNumber = "+1-555-0199",
            publicKeyBase64 = CryptoEngine.encodePublicKey(CryptoEngine.generateEphemeralKeyPair().public),
            fingerprint = "4A:9C:12:88:FF:21:03:DD:AA:88:51:30:19:62:EE:91",
            isVerified = true,
            addedAt = System.currentTimeMillis() - 86400000,
            lastSeen = System.currentTimeMillis() - 120000,
            isProtected = false,
            passwordSalt = null,
            passwordHash = null,
            isBlocked = false
        )
        database.contactDao().insertOrUpdateContact(demoContact1)

        val demoMessage = ChatMessageEntity(
            messageId = UUID.randomUUID().toString(),
            conversationId = demoContact1.bitcallId,
            senderBitcallId = demoContact1.bitcallId,
            recipientBitcallId = myBitcallId,
            text = "Welcome to BitCall! This message reached you across 3 Bluetooth hops. Everything is end-to-end encrypted.",
            timestamp = System.currentTimeMillis() - 60000,
            status = "DELIVERED",
            isIncoming = true,
            hopsCount = 3
        )
        database.chatMessageDao().insertMessage(demoMessage)
    }

    // ---------------------------------------------------------------------------------------------
    // APP LOCK & AUTHENTICATION
    // ---------------------------------------------------------------------------------------------

    fun unlockApp(pin: String): Boolean {
        var success = false
        viewModelScope.launch(Dispatchers.IO) {
            val hash = securityPrefs.pinHash.firstOrNull() ?: ""
            val salt = securityPrefs.pinSalt.firstOrNull() ?: ""
            if (CryptoEngine.verifyPassword(pin, salt, hash)) {
                securityPrefs.resetFailedAttempts()
                _isAppUnlocked.value = true
                _currentScreen.value = AppScreen.CHATS
                startMeshService()
                success = true
            } else {
                securityPrefs.recordFailedAttempt()
            }
        }
        return success
    }

    // ---------------------------------------------------------------------------------------------
    // CONTACT MANAGEMENT
    // ---------------------------------------------------------------------------------------------

    fun addContact(
        bitcallId: String,
        displayName: String,
        phoneNumber: String?,
        publicKeyBase64: String,
        fingerprint: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = ContactEntity(
                bitcallId = bitcallId.trim(),
                displayName = displayName.ifBlank { "Peer ${bitcallId.take(6)}" },
                phoneNumber = phoneNumber?.trim()?.ifBlank { null },
                publicKeyBase64 = publicKeyBase64.trim(),
                fingerprint = fingerprint.trim(),
                isVerified = false,
                addedAt = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                isProtected = false,
                passwordSalt = null,
                passwordHash = null,
                isBlocked = false
            )
            database.contactDao().insertOrUpdateContact(contact)
        }
    }

    fun setContactVerification(bitcallId: String, isVerified: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            database.contactDao().setVerificationStatus(bitcallId, isVerified)
        }
    }

    fun setContactProtection(bitcallId: String, password: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = database.contactDao().getContactById(bitcallId) ?: return@launch
            if (!password.isNullOrBlank()) {
                val hashed = CryptoEngine.hashPassword(password)
                val updated = contact.copy(
                    isProtected = true,
                    passwordSalt = hashed.saltBase64,
                    passwordHash = hashed.hashBase64
                )
                database.contactDao().insertOrUpdateContact(updated)
            } else {
                val updated = contact.copy(
                    isProtected = false,
                    passwordSalt = null,
                    passwordHash = null
                )
                database.contactDao().insertOrUpdateContact(updated)
            }
        }
    }

    fun unlockContact(bitcallId: String, password: String): Boolean {
        var matched = false
        viewModelScope.launch(Dispatchers.IO) {
            val contact = database.contactDao().getContactById(bitcallId)
            if (contact != null && contact.passwordSalt != null && contact.passwordHash != null) {
                if (CryptoEngine.verifyPassword(password, contact.passwordSalt, contact.passwordHash)) {
                    _unlockedContactIds.value = _unlockedContactIds.value + bitcallId
                    matched = true
                }
            }
        }
        return matched
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            database.contactDao().deleteContact(contact)
            database.chatMessageDao().deleteConversationMessages(contact.bitcallId)
        }
    }

    fun toggleBlockContact(bitcallId: String, isBlocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            database.contactDao().setBlockedStatus(bitcallId, isBlocked)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // CHAT & MESSAGING
    // ---------------------------------------------------------------------------------------------

    fun openConversation(contact: ContactEntity) {
        _activeContact.value = contact
        _currentScreen.value = AppScreen.CHAT_DETAIL
    }

    fun sendMessage(recipientBitcallId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                meshRouter.sendChatMessage(recipientBitcallId, text.trim())
            } catch (e: Exception) {
                _aiStatus.value = "Error sending: ${e.message}"
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.chatMessageDao().deleteMessage(messageId)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // VOICE CALLING
    // ---------------------------------------------------------------------------------------------

    fun initiateCall(contact: ContactEntity) {
        _activeCallPeer.value = contact
        _currentScreen.value = AppScreen.ACTIVE_CALL
        _callDurationSeconds.value = 0

        audioEngine.startCall { audioBytes ->
            // In a live call, audio packets are streamed to the peer over the mesh transport
        }

        // Call timer
        viewModelScope.launch(Dispatchers.Default) {
            while (audioEngine.isCallActive.value) {
                kotlinx.coroutines.delay(1000L)
                _callDurationSeconds.value += 1
            }
        }
    }

    fun endCall() {
        val duration = _callDurationSeconds.value
        val peer = _activeCallPeer.value
        audioEngine.endCall()

        if (peer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                database.callRecordDao().insertCallRecord(
                    CallRecordEntity(
                        callId = UUID.randomUUID().toString(),
                        peerBitcallId = peer.bitcallId,
                        peerDisplayName = peer.displayName,
                        callType = "OUTGOING",
                        durationSeconds = duration,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        _activeCallPeer.value = null
        _currentScreen.value = AppScreen.CHATS
    }

    // ---------------------------------------------------------------------------------------------
    // AI FEATURES (TTS, TRANSCRIBE, LOW LATENCY, THINKING AUDIT)
    // ---------------------------------------------------------------------------------------------

    /**
     * Converts an encrypted chat message text into speech using gemini-3.8-flash-tts.
     */
    fun speakMessageWithTts(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiLoading.value = true
            _aiStatus.value = "Generating speech via Gemini 3.8 Flash TTS..."
            val result = aiService.convertTextToSpeech(text)
            _aiLastResult.value = result
            _aiStatus.value = "TTS Output Ready"
            _isAiLoading.value = false
        }
    }

    /**
     * Transcribes recorded microphone audio using gemini-3.5-transcribe.
     */
    fun transcribeAudioAndSend(recipientBitcallId: String, pcmAudio: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiLoading.value = true
            _aiStatus.value = "Transcribing voice memo via Gemini 3.5 Transcribe..."
            val wavAudio = audioEngine.createWavFromPcm(pcmAudio)
            val transcribedText = aiService.transcribeAudio(wavAudio)
            _aiLastResult.value = "Transcribed: $transcribedText"
            _isAiLoading.value = false

            if (transcribedText.isNotBlank() && !transcribedText.startsWith("Transcription error")) {
                meshRouter.sendChatMessage(
                    recipientBitcallId = recipientBitcallId,
                    plainText = transcribedText,
                    isVoiceNote = true,
                    audioDurationMs = (pcmAudio.size / 32).toLong()
                )
            }
        }
    }

    /**
     * Rapid assistant response using gemini-3.1-flash-lite.
     */
    fun askLowLatencyAssistant(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiLoading.value = true
            _aiStatus.value = "Querying Gemini 3.1 Flash-Lite (Low Latency)..."
            val response = aiService.generateLowLatencyResponse(query)
            _aiLastResult.value = response
            _aiStatus.value = "Response received"
            _isAiLoading.value = false
        }
    }

    /**
     * Deep cryptographic and mesh security audit using gemini-4-argon with high thinking mode.
     */
    fun runDeepSecurityAudit(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiLoading.value = true
            _aiStatus.value = "Running deep cryptographic audit with Gemini 4 Argon (ThinkingLevel.HIGH)..."
            val audit = aiService.auditMeshSecurity(query)
            _aiLastResult.value = audit
            _aiStatus.value = "Security audit completed"
            _isAiLoading.value = false
        }
    }

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun toggleRelay(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            securityPrefs.setRelayEnabled(enabled)
        }
    }
}
