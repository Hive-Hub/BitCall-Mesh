package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@Composable
fun AiAssistantScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val aiLastResult by viewModel.aiLastResult.collectAsState()
    val isRecordingVoice by viewModel.audioEngine.isRecordingVoiceClip.collectAsState()

    var lowLatencyPrompt by remember { mutableStateOf("") }
    var securityQuery by remember { mutableStateOf("") }
    var ttsInputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Feature Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Low Latency", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Deep Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Transcribe", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("TTS Voice", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> {
                // Low-Latency Responses with Gemini 3.1 Flash-Lite
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Low-Latency Assistant (gemini-3.1-flash-lite)", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Rapid sub-second responses for emergency broadcast drafting, battery-saving node optimizations, and immediate mesh diagnostics.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = lowLatencyPrompt,
                            onValueChange = { lowLatencyPrompt = it },
                            placeholder = { Text("e.g. Draft an emergency beacon message for medical supplies...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("low_latency_input"),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.askLowLatencyAssistant(lowLatencyPrompt) },
                                enabled = !isAiLoading && lowLatencyPrompt.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("submit_low_latency_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Ask Assistant")
                            }
                            OutlinedButton(
                                onClick = {
                                    lowLatencyPrompt = "How do I optimize Bluetooth battery life during continuous multi-hop relaying?"
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Preset Tip")
                            }
                        }
                    }
                }
            }

            1 -> {
                // High Thinking Mode with Gemini 4 Argon
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text("Deep Cryptographic Audit (gemini-4-argon)", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "High thinking mode (ThinkingLevel.HIGH) to audit peer trust boundaries, forward secrecy, packet tampering resilience, and multi-hop attack vectors.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = securityQuery,
                            onValueChange = { securityQuery = it },
                            placeholder = { Text("e.g. Analyze resistance against rogue relay node packet dropping and sybil attacks...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("security_audit_input"),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 4
                        )

                        Button(
                            onClick = { viewModel.runDeepSecurityAudit(securityQuery) },
                            enabled = !isAiLoading && securityQuery.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_audit_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Execute High-Thinking Security Audit")
                        }
                    }
                }
            }

            2 -> {
                // Speech-to-Text with Gemini 3.5 Transcribe
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Audio Transcription (gemini-3.5-transcribe)", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Record voice using your microphone and transcribe it into high-accuracy text for mesh messages or notes.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        IconButton(
                            onClick = {
                                if (!isRecordingVoice) {
                                    viewModel.audioEngine.startVoiceClipRecording()
                                } else {
                                    val pcm = viewModel.audioEngine.stopVoiceClipRecording()
                                    if (pcm.isNotEmpty()) {
                                        viewModel.transcribeAudioAndSend("BROADCAST", pcm)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isRecordingVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                .testTag("ai_record_voice_button")
                        ) {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Record",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = if (isRecordingVoice) "Recording... Tap to stop & transcribe" else "Tap microphone to speak",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            3 -> {
                // Text-to-Speech with Gemini 3.8 Flash TTS
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Text to Speech (gemini-3.8-flash-tts)", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Convert written emergency broadcasts or encrypted messages into natural spoken audio cadence.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = ttsInputText,
                            onValueChange = { ttsInputText = it },
                            placeholder = { Text("Enter text to convert to speech readout...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_input_field"),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3
                        )

                        Button(
                            onClick = { viewModel.speakMessageWithTts(ttsInputText) },
                            enabled = !isAiLoading && ttsInputText.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generate_tts_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Generate Speech Readout")
                        }
                    }
                }
            }
        }

        // Live Result / Output Card
        if (isAiLoading || aiLastResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = if (isAiLoading) aiStatus ?: "Processing with Gemini..." else "AI Output Result",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (aiLastResult != null) {
                        Text(
                            text = aiLastResult!!,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
