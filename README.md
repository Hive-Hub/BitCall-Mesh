# BitCall: Encrypted Bluetooth Mesh Calling and Messaging for Android

BitCall is an offline-first, decentralized, end-to-end encrypted communication platform for Android. It enables users to exchange encrypted text messages and live voice calls over a peer-to-peer Bluetooth mesh network across nearby phones, without cellular infrastructure or central servers.

---

## Key Features

1. **Cryptographic Identity on Every Device**
   - Local NIST P-256 (secp256r1) keypair generation protected with Android Keystore.
   - Elliptic Curve Diffie-Hellman (ECDH) key agreement for forward-secure session key negotiation.
   - HKDF-SHA256 key derivation and AES-256-GCM authenticated encryption for every message packet.
   - SHA-256 public key fingerprints for out-of-band verification.

2. **Three-Tier Security Architecture**
   - **App Lock:** Local PIN/Password protection hashed with PBKDF2WithHmacSHA256 (100,000 iterations) with rate limiting and lockouts.
   - **Contact Password Lock:** Protect individual contacts or conversations with an independent password and unique per-contact salt.
   - **End-to-End Encryption:** All message contents and payloads are sealed on the sender device before broadcast; intermediate relay devices cannot read plaintext.

3. **Multi-Hop Bluetooth Mesh Networking**
   - Controlled multi-hop forwarding with hop limits (default 7 hops) and duplicate message suppression LRU cache.
   - Store-and-forward queue with automatic TTL expiration purge.
   - Cryptographic delivery receipts (ACKs) confirming packet arrival at the final recipient.
   - User-configurable relay participation mode and Android Foreground Service (`BitCallMeshService`).

4. **Real-Time Voice Calling**
   - Low-latency voice capture via `AudioRecord` (16kHz mono 16-bit PCM) and playback via `AudioTrack`.
   - Adaptive jitter buffering, audio packet sequencing, and mute/speaker toggles.
   - Live stream diagnostics (packet loss, latency, jitter).

5. **AI Speech & Cryptographic Intelligence (Firebase AI Logic)**
   - **Text-to-Speech (TTS):** Converts encrypted message text to speech audio readout using `gemini-3.8-flash-tts`.
   - **Low-Latency Responses:** Rapid emergency message drafting and node diagnostics using `gemini-3.1-flash-lite`.
   - **High Thinking Mode:** Deep cryptographic threat modeling and multi-hop attack audit using `gemini-4-argon` (`ThinkingLevel.HIGH`).
   - **Audio Transcription:** Live microphone voice note transcription to text using `gemini-3.5-transcribe`.

6. **Contact Verification & QR Code Exchange**
   - High-contrast QR code generation (`QrCodeUtil` using ZXing) for fast in-person public key exchange.
   - Visual verified badges and fingerprint comparison.

---

## Technical Stack

- **Platform:** Android (Min SDK 24, Target SDK 36)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3 with Dark Cybernetic aesthetic)
- **Database:** Room (SQLite) with parameterized queries
- **Preferences:** Jetpack DataStore Preferences
- **Security:** Android Keystore, NIST P-256 ECDH, HKDF-SHA256, AES-256-GCM, PBKDF2WithHmacSHA256
- **AI Backend:** Firebase AI Logic with App Check attestation
- **Testing:** JUnit, Robolectric local JVM testing

---

## Building and Running the App

### Requirements
- JDK 17 or higher
- Android SDK (API 34-36)

### Gradle Commands

1. **Build Debug APK:**
   ```bash
   gradle assembleDebug
   ```

2. **Run Local Unit & Robolectric Tests:**
   ```bash
   gradle :app:testDebugUnitTest
   ```

3. **Install on Two Physical Android Devices:**
   ```bash
   gradle installDebug
   ```
   - Open BitCall on both devices.
   - Grant Bluetooth and microphone permissions.
   - Scan each other's identity QR code under Settings or Contacts.
   - Exchange encrypted text messages and initiate direct voice calls!
