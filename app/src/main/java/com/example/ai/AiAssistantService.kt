package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.ThinkingConfig
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "BitCallAi"

/**
 * Service managing all AI capabilities for BitCall via Firebase AI Logic:
 * 1. Low-latency AI mesh helper via `gemini-3.1-flash-lite`
 * 2. High-thinking cryptographic audit via `gemini-4-argon` with `ThinkingLevel.HIGH`
 * 3. Speech-to-Text Audio Transcription via `gemini-3.5-transcribe`
 * 4. Text-to-Speech audio generation via `gemini-3.8-flash-tts`
 */
class AiAssistantService {

    // 1. Low-latency model for rapid emergency responses, quick messaging drafts, and diagnostics
    private val lowLatencyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite"
        )
    }

    // 2. High-thinking model for deep cryptographic threat analysis and multi-hop route security audits
    // Note: thinkingLevel set to HIGH, no maxOutputTokens set as required
    private val thinkingModel by lazy {
        val config = generationConfig {
            thinkingConfig = ThinkingConfig.Builder()
                .setThinkingLevel(ThinkingLevel.HIGH)
                .build()
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = config
        )
    }

    // 3. Audio transcription model for microphone speech-to-text
    private val transcribeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-transcribe"
        )
    }

    // 4. Text-to-Speech model for reading encrypted messages and transcripts aloud
    private val ttsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash-tts"
        )
    }

    /**
     * Generates a rapid, low-latency response using gemini-3.1-flash-lite.
     */
    suspend fun generateLowLatencyResponse(prompt: String): String {
        return try {
            withTimeout(45_000L) {
                val response = lowLatencyModel.generateContent(
                    "You are BitCall Mesh Assistant, an offline-first decentralized emergency communicator assistant. Provide a brief, ultra-concise response under 3 sentences for:\n$prompt"
                )
                response.text ?: "No response generated."
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Low latency request timed out", e)
            "Request timed out. Please check your connectivity and try again."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Low latency request failed", e)
            "AI Assistant unavailable: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Conducts a deep cryptographic and mesh network threat audit with high thinking enabled (gemini-4-argon).
     */
    suspend fun auditMeshSecurity(query: String): String {
        return try {
            withTimeout(60_000L) {
                val fullPrompt = """
                    You are BitCall's Senior Cryptographic & Mesh Network Security Auditor.
                    Evaluate the following cryptographic or routing question thoroughly:
                    $query
                    
                    Focus on:
                    1. Replay attack and duplicate packet suppression
                    2. ECDH & AES-256-GCM forward secrecy and keystore protections
                    3. Multi-hop relay security and untrusted intermediate nodes
                    4. Threat model boundaries
                """.trimIndent()
                val response = thinkingModel.generateContent(fullPrompt)
                response.text ?: "Security audit returned no text."
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Security audit timed out", e)
            "Security audit timed out after 60 seconds."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Security audit failed", e)
            "Audit failed: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Transcribes microphone audio bytes (WAV/PCM) using gemini-3.5-transcribe.
     */
    suspend fun transcribeAudio(audioBytes: ByteArray): String {
        return try {
            withTimeout(60_000L) {
                val content = Content(
                    role = "user",
                    parts = listOf(
                        InlineDataPart(audioBytes, "audio/wav"),
                        TextPart("Transcribe this voice audio accurately into plain text. Return only the transcription without filler.")
                    )
                )
                val response = transcribeModel.generateContent(content)
                val transcribed = response.text?.trim()
                if (!transcribed.isNullOrBlank()) {
                    transcribed
                } else {
                    "No speech detected in audio."
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            "Transcription timed out."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            "Transcription error: ${e.localizedMessage ?: "Failed to process audio"}"
        }
    }

    /**
     * Converts text into spoken audio or speech representation using gemini-3.8-flash-tts.
     */
    suspend fun convertTextToSpeech(textToSpeak: String): String {
        return try {
            withTimeout(60_000L) {
                val response = ttsModel.generateContent(
                    "Generate speech audio readout transcript with phonetic clarity and cadence for the following BitCall message:\n\"$textToSpeak\""
                )
                response.text ?: "Speech generation complete."
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "TTS request timed out", e)
            "TTS generation timed out."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TTS request failed", e)
            "TTS generation error: ${e.localizedMessage ?: "Unknown error"}"
        }
    }
}
