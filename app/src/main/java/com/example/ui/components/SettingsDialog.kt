package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.HistoryEdu
import com.example.data.ApiKeyEntry
import com.example.data.ApiKeyStatus
import com.example.data.PreferencesManager
import com.example.ui.SettingsState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
    settings: SettingsState,
    apiPool: List<ApiKeyEntry>,
    activeKeyIndex: Int,
    totalMistakesCount: Int = 0,
    totalDaysCount: Int = 0,
    onOpenMistakesArchive: () -> Unit = {},
    onDismiss: () -> Unit,
    onAddKey: (String) -> Boolean,
    onAddMultipleKeys: (String) -> Int,
    onRemoveKey: (String) -> Unit,
    onSelectActiveKey: (Int) -> Unit,
    onResetPoolStatuses: () -> Unit,
    onLiveSilenceDurationChanged: (Long) -> Unit,
    onLiveSensitivityChanged: (Int) -> Unit,
    onSave: (
        apiKey: String,
        modelName: String,
        correctionModelName: String,
        fontSizeSp: Float,
        silenceDurationMs: Long,
        sensitivityThreshold: Int,
        keepScreenOn: Boolean,
        enableSmoothing: Boolean,
        enableGrammarCheck: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    var newKeyInput by remember { mutableStateOf("") }
    var bulkKeysInput by remember { mutableStateOf("") }
    var showBulkInput by remember { mutableStateOf(false) }

    var tempModelName by remember { mutableStateOf(settings.modelName) }
    var tempCorrectionModel by remember { mutableStateOf(settings.correctionModelName) }
    var tempFontSize by remember { mutableFloatStateOf(settings.fontSizeSp) }
    var tempSilenceDurationMs by remember { mutableLongStateOf(settings.silenceDurationMs) }
    var tempSensitivity by remember { mutableIntStateOf(settings.sensitivityThreshold) }
    var tempKeepScreenOn by remember { mutableStateOf(settings.keepScreenOn) }
    var tempEnableSmoothing by remember { mutableStateOf(settings.enableSmoothing) }
    var tempEnableGrammarCheck by remember { mutableStateOf(settings.enableGrammarCheck) }

    ModalBottomSheet(
        onDismissRequest = {
            // Apply live silence & sensitivity even on swipe dismiss
            onLiveSilenceDurationChanged(tempSilenceDurationMs)
            onLiveSensitivityChanged(tempSensitivity)
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        contentColor = Color(0xFFEEEEEE),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = Color(0xFF00B0FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transcribe V2 Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = Color(0xFFAAAAAA)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // MISTAKES ARCHIVE CARD (STUDY MISTAKES BY DAY)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E1712))
                    .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(14.dp))
                    .clickable { onOpenMistakesArchive() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mistakes Archive",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (totalDaysCount > 0) {
                                "$totalDaysCount days recorded ($totalMistakesCount items) • Tap to study"
                            } else {
                                "Automatic daily study archive • Tap to view"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFFFFCC80)
                        )
                    }
                }

                Button(
                    onClick = onOpenMistakesArchive,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Study", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // 1. API KEY POOLING SECTION
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "API Key Pool (${apiPool.size} keys)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B0FF)
                    )
                    Text(
                        text = "Automatic failover across transcription, smoothing & grammar APIs.",
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
                if (apiPool.isNotEmpty()) {
                    IconButton(
                        onClick = onResetPoolStatuses,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset statuses",
                            tint = Color(0xFF81D4FA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pool Items List
            if (apiPool.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No keys in pool. Add Gemini API keys below.",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    apiPool.forEachIndexed { index, entry ->
                        val isActive = index == activeKeyIndex
                        val borderColor = when {
                            isActive -> Color(0xFF00B0FF)
                            entry.status == ApiKeyStatus.RATE_LIMITED -> Color(0xFFFF9800)
                            entry.status == ApiKeyStatus.FAILED -> Color(0xFFE53935)
                            else -> Color(0xFF2C2C2C)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1A1A))
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .clickable { onSelectActiveKey(index) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                val dotColor = when {
                                    isActive -> Color(0xFF00E676)
                                    entry.status == ApiKeyStatus.RATE_LIMITED -> Color(0xFFFF9800)
                                    entry.status == ApiKeyStatus.FAILED -> Color(0xFFE53935)
                                    else -> Color(0xFF757575)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(dotColor, CircleShape)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Key #${index + 1}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) Color(0xFF00B0FF) else Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = entry.maskedKey,
                                            fontSize = 12.sp,
                                            color = Color(0xFFAAAAAA)
                                        )
                                    }

                                    val statusLabel = when (entry.status) {
                                        ApiKeyStatus.ACTIVE -> "Active (Serving)"
                                        ApiKeyStatus.HEALTHY -> "Ready for fallback"
                                        ApiKeyStatus.RATE_LIMITED -> "Rate Limited (429)"
                                        ApiKeyStatus.FAILED -> "Failed: ${entry.lastError ?: "Error"}"
                                    }
                                    Text(
                                        text = statusLabel,
                                        fontSize = 11.sp,
                                        color = when (entry.status) {
                                            ApiKeyStatus.ACTIVE -> Color(0xFF00E676)
                                            ApiKeyStatus.RATE_LIMITED -> Color(0xFFFFB74D)
                                            ApiKeyStatus.FAILED -> Color(0xFFEF9A9A)
                                            else -> Color(0xFF888888)
                                        }
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onRemoveKey(entry.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove key",
                                    tint = Color(0xFF757575),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add Single Key Field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = { newKeyInput = it },
                    placeholder = { Text("Paste Gemini API Key (AIza...)", fontSize = 12.sp, color = Color(0xFF666666)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newKeyInput.isNotBlank()) {
                            onAddKey(newKeyInput.trim())
                            newKeyInput = ""
                        }
                        focusManager.clearFocus()
                    })
                )

                Button(
                    onClick = {
                        if (newKeyInput.isNotBlank()) {
                            onAddKey(newKeyInput.trim())
                            newKeyInput = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                    modifier = Modifier.testTag("add_key_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bulk Add Toggle Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBulkInput = !showBulkInput }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = Color(0xFF81D4FA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showBulkInput) "Hide Bulk Add" else "Bulk add multiple API keys (one per line)",
                    fontSize = 12.sp,
                    color = Color(0xFF81D4FA),
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = showBulkInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = bulkKeysInput,
                        onValueChange = { bulkKeysInput = it },
                        placeholder = {
                            Text("Paste multiple keys:\nAIzaSyKey1...\nAIzaSyKey2...\nAIzaSyKey3...", fontSize = 12.sp, color = Color(0xFF666666))
                        },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_keys_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00B0FF),
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF1E1E1E)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (bulkKeysInput.isNotBlank()) {
                                onAddMultipleKeys(bulkKeysInput)
                                bulkKeysInput = ""
                                showBulkInput = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Import Keys to Pool", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 2. SILENCE PAUSE DURATION (LIVE TUNING)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Silence Pause Duration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF81D4FA)
                )
                Text(
                    text = String.format("%.1f s", tempSilenceDurationMs / 1000f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
            }
            Text(
                text = "Live adjustment: Pausing for this exact duration sends audio seamlessly.",
                fontSize = 12.sp,
                color = Color(0xFF888888)
            )

            Slider(
                value = tempSilenceDurationMs.toFloat(),
                onValueChange = {
                    val ms = it.toLong()
                    tempSilenceDurationMs = ms
                    onLiveSilenceDurationChanged(ms) // Instant live update to AudioRecorder!
                },
                valueRange = 500f..3500f,
                steps = 29,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("silence_duration_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E676),
                    activeTrackColor = Color(0xFF00E676),
                    inactiveTrackColor = Color(0xFF333333)
                )
            )

            // Preset chips for 1.0s, 1.5s, 2.0s, 2.5s, 3.0s
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(1000L to "1.0s", 1500L to "1.5s", 2000L to "2.0s", 2500L to "2.5s", 3000L to "3.0s").forEach { (ms, label) ->
                    FilterChip(
                        selected = tempSilenceDurationMs == ms,
                        onClick = {
                            tempSilenceDurationMs = ms
                            onLiveSilenceDurationChanged(ms)
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E676),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1F1F1F),
                            labelColor = Color(0xFFAAAAAA)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 3. FONT SIZE SECTION
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Font Size",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF81D4FA)
                )
                Text(
                    text = "${tempFontSize.roundToInt()} sp",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Slider(
                value = tempFontSize,
                onValueChange = { tempFontSize = it },
                valueRange = 14f..44f,
                steps = 14,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("font_size_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00B0FF),
                    activeTrackColor = Color(0xFF00B0FF),
                    inactiveTrackColor = Color(0xFF333333)
                )
            )

            // Live preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF000000))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Live Preview: Transcribed text appears like this.",
                    fontSize = tempFontSize.sp,
                    color = Color(0xFFE0E0E0),
                    lineHeight = (tempFontSize * 1.35f).sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 4. AI MODELS & CORRECTION SETTINGS
            // ==========================================
            Text(
                text = "Transcription Model",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF81D4FA)
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = tempModelName,
                onValueChange = { tempModelName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_name_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00B0FF),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val models = listOf("gemini-3.5-transcribe-live", "gemini-3.5-transcribe", "gemini-3.8-flash")
                models.forEach { modelOption ->
                    FilterChip(
                        selected = tempModelName == modelOption,
                        onClick = { tempModelName = modelOption },
                        label = { Text(modelOption, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0288D1),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1F1F1F),
                            labelColor = Color(0xFFAAAAAA)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Correction Model
            Text(
                text = "Smoothing & Grammar Model",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF81D4FA)
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = tempCorrectionModel,
                onValueChange = { tempCorrectionModel = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("correction_model_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00B0FF),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // AI Smoothing Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Contextual Smoothing",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Smooths sentence boundaries & punctuation using last 20 words (Flash Lite)",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
                Switch(
                    checked = tempEnableSmoothing,
                    onCheckedChange = { tempEnableSmoothing = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0288D1),
                        uncheckedThumbColor = Color(0xFF888888),
                        uncheckedTrackColor = Color(0xFF222222)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI Grammar & Word Choice Inspector Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Grammar & Word Choice Inspector",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Marks grammar errors in Red and word choices in Yellow for tap-to-fix",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
                Switch(
                    checked = tempEnableGrammarCheck,
                    onCheckedChange = { tempEnableGrammarCheck = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0288D1),
                        uncheckedThumbColor = Color(0xFF888888),
                        uncheckedTrackColor = Color(0xFF222222)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 5. MICROPHONE SPEECH SENSITIVITY
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Microphone Sensitivity",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF81D4FA)
                )
                Text(
                    text = when {
                        tempSensitivity <= 350 -> "High"
                        tempSensitivity <= 600 -> "Normal"
                        else -> "Low"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val presets = listOf("High" to 300, "Normal" to 450, "Low" to 750)
                presets.forEach { (label, value) ->
                    FilterChip(
                        selected = tempSensitivity == value,
                        onClick = {
                            tempSensitivity = value
                            onLiveSensitivityChanged(value)
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0288D1),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1F1F1F),
                            labelColor = Color(0xFFAAAAAA)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keep Screen On
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep Screen On",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Prevents device display from sleeping",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
                Switch(
                    checked = tempKeepScreenOn,
                    onCheckedChange = { tempKeepScreenOn = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0288D1),
                        uncheckedThumbColor = Color(0xFF888888),
                        uncheckedTrackColor = Color(0xFF222222)
                    )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ==========================================
            // BOTTOM ACTIONS
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        tempModelName = PreferencesManager.DEFAULT_MODEL
                        tempCorrectionModel = PreferencesManager.DEFAULT_CORRECTION_MODEL
                        tempFontSize = PreferencesManager.DEFAULT_FONT_SIZE
                        tempSilenceDurationMs = PreferencesManager.DEFAULT_SILENCE_DURATION_MS
                        tempSensitivity = PreferencesManager.DEFAULT_SENSITIVITY
                        tempKeepScreenOn = true
                        tempEnableSmoothing = true
                        tempEnableGrammarCheck = true
                        onLiveSilenceDurationChanged(PreferencesManager.DEFAULT_SILENCE_DURATION_MS)
                        onLiveSensitivityChanged(PreferencesManager.DEFAULT_SENSITIVITY)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_defaults_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA))
                ) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Defaults", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val activeKey = apiPool.getOrNull(activeKeyIndex)?.key ?: settings.apiKey
                        onSave(
                            activeKey,
                            tempModelName,
                            tempCorrectionModel,
                            tempFontSize,
                            tempSilenceDurationMs,
                            tempSensitivity,
                            tempKeepScreenOn,
                            tempEnableSmoothing,
                            tempEnableGrammarCheck
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_settings_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF), contentColor = Color.White)
                ) {
                    Text("Save & Close", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
