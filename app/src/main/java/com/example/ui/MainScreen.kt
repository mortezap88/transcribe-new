package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.GrammarSuggestion
import com.example.data.SuggestionType
import com.example.ui.components.GrammarSuggestionDialog
import com.example.ui.components.MistakesArchiveSheet
import com.example.ui.components.SettingsBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(viewModel: TranscriptionViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsState()
    val apiPool by viewModel.apiPool.collectAsState()
    val activeKeyIndex by viewModel.activeKeyIndex.collectAsState()

    val transcript by viewModel.transcript.collectAsState()
    val grammarSuggestions by viewModel.grammarSuggestions.collectAsState()
    val groupedMistakes by viewModel.groupedMistakes.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val inFlightCount by viewModel.inFlightCount.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showMistakesArchive by remember { mutableStateOf(false) }
    var selectedSuggestion by remember { mutableStateOf<GrammarSuggestion?>(null) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.startListening()
        }
    }

    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && !isListening) {
            viewModel.startListening()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error)
                viewModel.dismissError()
            }
        }
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(transcript, inFlightCount) {
        if (transcript.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.statusBars.asPaddingValues())
            ) {
                // ====================================================
                // TOP ACTION BAR (Clean & uncluttered)
                // 1. Listening Status Toggle Pill
                // 2. Which API is in use Toggle Pill
                // 3. Circular Mic Toggle Button
                // 4. Circular Settings Gear Button
                // ====================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Group: Audio Status Pill & Which API Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. LISTENING STATUS TOGGLE PILL
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF141414))
                                .clickable {
                                    if (hasMicPermission) {
                                        viewModel.toggleListening()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("status_indicator")
                        ) {
                            if (isListening) {
                                val dotColor = if (audioLevel > 0.15f) Color(0xFF00E676) else Color(0xFF00B0FF)
                                val dynamicScale = (1f + audioLevel * 1.5f).coerceAtMost(2.2f)
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(if (audioLevel > 0.05f) dynamicScale else pulseScale)
                                        .background(dotColor, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF757575), CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = statusMessage,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isListening) Color(0xFFB0BEC5) else Color(0xFF757575)
                            )

                            if (inFlightCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color(0xFF00B0FF)
                                )
                            }
                        }

                        // 2. WHICH API WE'RE USING PILL
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF141414))
                                .clickable { showSettingsSheet = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("api_pool_indicator")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "API Pool",
                                tint = if (apiPool.isNotEmpty()) Color(0xFF00B0FF) else Color(0xFFFFB74D),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (apiPool.isNotEmpty()) {
                                    "Pool: ${activeKeyIndex + 1}/${apiPool.size}"
                                } else {
                                    "No Keys"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (apiPool.isNotEmpty()) Color(0xFFE0E0E0) else Color(0xFFFFB74D)
                            )
                        }
                    }

                    // Right Group: Copy, Clear, Circular Mic Toggle, Circular Settings Gear
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (transcript.isNotBlank()) {
                            // Copy Button
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Transcribed Speech", transcript)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("copy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = Color(0xFF9E9E9E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Clear Button (only button that wipes transcript)
                            IconButton(
                                onClick = { viewModel.clearTranscript() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear transcript",
                                    tint = Color(0xFF9E9E9E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 3. CIRCULAR MIC TOGGLE BUTTON
                        val micBorderColor = if (isListening) Color(0xFF00E676) else Color(0xFF424242)
                        val micBgColor = if (isListening) Color(0xFF132A1C) else Color(0xFF1E1E1E)
                        val micIconTint = if (isListening) Color(0xFF00E676) else Color(0xFF9E9E9E)

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(micBgColor)
                                .border(1.5.dp, micBorderColor, CircleShape)
                                .clickable {
                                    if (hasMicPermission) {
                                        viewModel.toggleListening()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                .testTag("mic_toggle_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = if (isListening) "Deactivate Mic" else "Activate Mic",
                                tint = micIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 4. CIRCULAR SETTINGS GEAR BUTTON
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E1E))
                                .border(1.dp, Color(0xFF333333), CircleShape)
                                .clickable { showSettingsSheet = true }
                                .testTag("settings_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (apiPool.isEmpty()) Color(0xFFFFB74D) else Color(0xFFE0E0E0),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Missing API key warning
                if (apiPool.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF261D0F))
                            .clickable { showSettingsSheet = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Gemini API keys to the pool in Settings.",
                            fontSize = 12.sp,
                            color = Color(0xFFFFCC80),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Main Scrolling Content Area (Completely uncluttered!)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (!hasMicPermission) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = null,
                                tint = Color(0xFF757575),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Microphone Permission Needed",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Grant microphone access for real-time speech transcription with silence detection.",
                                fontSize = 13.sp,
                                color = Color(0xFF9E9E9E),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("grant_permission_button")
                            ) {
                                Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (transcript.isBlank()) {
                        // Empty State: "a black screen waiting to hear"
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Spacer(modifier = Modifier.height(48.dp))
                            Text(
                                text = "Waiting to hear...",
                                fontSize = settings.fontSizeSp.sp,
                                color = Color(0xFF424242),
                                fontWeight = FontWeight.Normal,
                                lineHeight = (settings.fontSizeSp * 1.4f).sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Speak freely.\n• Dispatches audio on ${String.format("%.1f", settings.silenceDurationMs / 1000f)}s pause without mic interruption\n• Flash Lite contextual boundary smoothing\n• Tap red or yellow highlights to study and correct mistakes",
                                fontSize = 13.sp,
                                color = Color(0xFF333333),
                                lineHeight = 21.sp
                            )
                        }
                    } else {
                        // Render transcript with interactive highlight pills for Grammar (Red) and Word Choice (Yellow)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            InteractiveTranscriptView(
                                transcript = transcript,
                                suggestions = grammarSuggestions,
                                fontSizeSp = settings.fontSizeSp,
                                onSuggestionClick = { suggestion ->
                                    selectedSuggestion = suggestion
                                }
                            )

                            // In-flight indicator
                            if (inFlightCount > 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.alpha(0.6f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF00B0FF), CircleShape)
                                            .scale(pulseScale)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "transcribing...",
                                        fontSize = (settings.fontSizeSp * 0.7f).coerceAtLeast(11f).sp,
                                        color = Color(0xFF00B0FF)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(56.dp))
                        }
                    }
                }
            }
        }
    }

    // Interactive Suggestion Inspection Dialog (Red = Grammar, Yellow = Word Choice)
    selectedSuggestion?.let { suggestion ->
        GrammarSuggestionDialog(
            suggestion = suggestion,
            onApply = { applied ->
                viewModel.applySuggestion(applied)
                selectedSuggestion = null
            },
            onDismiss = {
                selectedSuggestion = null
            }
        )
    }

    // Mistakes & Corrections Study Archive Sheet (Organized by Day!)
    if (showMistakesArchive) {
        MistakesArchiveSheet(
            groupedMistakes = groupedMistakes,
            onDismiss = { showMistakesArchive = false },
            onDeleteDay = { dayKey -> viewModel.deleteArchiveDay(dayKey) },
            onClearAll = { viewModel.clearArchive() }
        )
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        SettingsBottomSheet(
            settings = settings,
            apiPool = apiPool,
            activeKeyIndex = activeKeyIndex,
            totalMistakesCount = groupedMistakes.values.sumOf { it.size },
            totalDaysCount = groupedMistakes.size,
            onOpenMistakesArchive = {
                showMistakesArchive = true
                showSettingsSheet = false
            },
            onDismiss = { showSettingsSheet = false },
            onAddKey = { viewModel.addKeyToPool(it) },
            onAddMultipleKeys = { viewModel.addMultipleKeysToPool(it) },
            onRemoveKey = { viewModel.removeKeyFromPool(it) },
            onSelectActiveKey = { viewModel.selectActiveKey(it) },
            onResetPoolStatuses = { viewModel.resetPoolStatuses() },
            onLiveSilenceDurationChanged = { viewModel.setLiveSilenceDuration(it) },
            onLiveSensitivityChanged = { viewModel.setLiveSensitivity(it) },
            onSave = { apiKey, modelName, correctionModel, fontSizeSp, silenceDurationMs, sensitivityThreshold, keepScreenOn, enableSmoothing, enableGrammarCheck ->
                viewModel.updateSettings(
                    apiKey = apiKey,
                    modelName = modelName,
                    correctionModelName = correctionModel,
                    fontSizeSp = fontSizeSp,
                    silenceDurationMs = silenceDurationMs,
                    sensitivityThreshold = sensitivityThreshold,
                    keepScreenOn = keepScreenOn,
                    enableSmoothing = enableSmoothing,
                    enableGrammarCheck = enableGrammarCheck
                )
            }
        )
    }
}

/**
 * Renders the transcript text with interactive highlight pills for grammar mistakes (Red)
 * and word choice improvements (Yellow). Tapping on any highlighted segment triggers the inspector dialog!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveTranscriptView(
    transcript: String,
    suggestions: List<GrammarSuggestion>,
    fontSizeSp: Float,
    onSuggestionClick: (GrammarSuggestion) -> Unit
) {
    if (suggestions.isEmpty()) {
        Text(
            text = transcript,
            fontSize = fontSizeSp.sp,
            color = Color(0xFFF5F5F5),
            fontWeight = FontWeight.Normal,
            lineHeight = (fontSizeSp * 1.42f).sp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("transcript_display")
        )
        return
    }

    val words = transcript.split(" ")
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transcript_display"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var wordIndex = 0
        while (wordIndex < words.size) {
            val remainingText = words.subList(wordIndex, words.size).joinToString(" ")
            val matchingSuggestion = suggestions.firstOrNull { remainingText.startsWith(it.original) }

            if (matchingSuggestion != null) {
                val matchedWordsCount = matchingSuggestion.original.split(" ").size
                val isGrammar = matchingSuggestion.type == SuggestionType.GRAMMAR
                val textColor = if (isGrammar) Color(0xFFFF8A80) else Color(0xFFFFD54F)
                val bgColor = if (isGrammar) Color(0xFF3E1414) else Color(0xFF38290B)
                val borderColor = if (isGrammar) Color(0xFFEF5350) else Color(0xFFFFB300)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                        .clickable { onSuggestionClick(matchingSuggestion) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = matchingSuggestion.original,
                        fontSize = fontSizeSp.sp,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        lineHeight = (fontSizeSp * 1.35f).sp
                    )
                }

                wordIndex += maxOf(1, matchedWordsCount)
            } else {
                Text(
                    text = words[wordIndex],
                    fontSize = fontSizeSp.sp,
                    color = Color(0xFFF5F5F5),
                    fontWeight = FontWeight.Normal,
                    lineHeight = (fontSizeSp * 1.42f).sp
                )
                wordIndex++
            }
        }
    }
}
