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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArchivedMistake
import com.example.data.SuggestionType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MistakesArchiveSheet(
    groupedMistakes: Map<String, List<ArchivedMistake>>,
    onDismiss: () -> Unit,
    onDeleteDay: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFilter by remember { mutableStateOf<SuggestionType?>(null) } // null = All
    var expandedDays by remember { mutableStateOf(groupedMistakes.keys.toSet()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF101010),
        contentColor = Color(0xFFEEEEEE),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = Color(0xFF00B0FF),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Mistakes Archive",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Study your daily mistakes & improvements",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (groupedMistakes.isNotEmpty()) {
                        IconButton(
                            onClick = onClearAll,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear all mistakes",
                                tint = Color(0xFF757575),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips (All, Red Grammar, Yellow Word Choice)
            if (groupedMistakes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All Mistakes", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00B0FF),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E1E1E),
                            labelColor = Color(0xFFAAAAAA)
                        )
                    )

                    FilterChip(
                        selected = selectedFilter == SuggestionType.GRAMMAR,
                        onClick = {
                            selectedFilter = if (selectedFilter == SuggestionType.GRAMMAR) null else SuggestionType.GRAMMAR
                        },
                        label = { Text("Grammar Only (Red)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEF5350),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E1E1E),
                            labelColor = Color(0xFFEF9A9A)
                        )
                    )

                    FilterChip(
                        selected = selectedFilter == SuggestionType.WORD_CHOICE,
                        onClick = {
                            selectedFilter = if (selectedFilter == SuggestionType.WORD_CHOICE) null else SuggestionType.WORD_CHOICE
                        },
                        label = { Text("Word Choice Only (Yellow)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFB300),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E1E1E),
                            labelColor = Color(0xFFFFE082)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Empty state
            if (groupedMistakes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.HistoryEdu,
                            contentDescription = null,
                            tint = Color(0xFF424242),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No mistakes recorded yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF888888)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "As you speak, every grammar mistake and word choice\nimprovement is automatically archived here for night study.",
                            fontSize = 12.sp,
                            color = Color(0xFF555555),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // List of Days (Only days that have at least one item!)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(groupedMistakes.keys.toList()) { displayDate ->
                        val allDayItems = groupedMistakes[displayDate] ?: emptyList()
                        val filteredItems = when (selectedFilter) {
                            null -> allDayItems
                            else -> allDayItems.filter { it.type == selectedFilter }
                        }

                        if (filteredItems.isNotEmpty()) {
                            val isExpanded = expandedDays.contains(displayDate)
                            val grammarCount = allDayItems.count { it.type == SuggestionType.GRAMMAR }
                            val wordChoiceCount = allDayItems.count { it.type == SuggestionType.WORD_CHOICE }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF161616))
                                    .border(1.dp, Color(0xFF262626), RoundedCornerShape(14.dp))
                            ) {
                                // Day Header (Click to toggle expand)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedDays = if (isExpanded) {
                                                expandedDays - displayDate
                                            } else {
                                                expandedDays + displayDate
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayDate,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (grammarCount > 0) {
                                                Text(
                                                    text = "$grammarCount Grammar (Red)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFEF5350)
                                                )
                                            }
                                            if (wordChoiceCount > 0) {
                                                Text(
                                                    text = "$wordChoiceCount Improvement (Yellow)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFFFB300)
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onDeleteDay(displayDate) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete day",
                                                tint = Color(0xFF666666),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = Color(0xFF888888),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Expanded items for this day
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .padding(bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        filteredItems.forEach { mistake ->
                                            MistakeCard(mistake = mistake)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeCard(mistake: ArchivedMistake) {
    val isGrammar = mistake.type == SuggestionType.GRAMMAR
    val badgeColor = if (isGrammar) Color(0xFFEF5350) else Color(0xFFFFB300)
    val cardBg = if (isGrammar) Color(0xFF221212) else Color(0xFF241A0B)
    val borderColor = if (isGrammar) Color(0xFF4A1A1A) else Color(0xFF4D3814)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            // Top Row: Type Badge + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(badgeColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGrammar) "Grammar Error" else "Word Choice Suggestion",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                Text(
                    text = mistake.timeFormatted,
                    fontSize = 11.sp,
                    color = Color(0xFF757575)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Original -> Better
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mistake.original,
                    fontSize = 14.sp,
                    color = Color(0xFFE57373),
                    textDecoration = TextDecoration.LineThrough,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "→",
                    fontSize = 14.sp,
                    color = Color(0xFF888888)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = mistake.replacement,
                    fontSize = 15.sp,
                    color = Color(0xFF81C784),
                    fontWeight = FontWeight.Bold
                )
            }

            if (mistake.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = mistake.reason,
                    fontSize = 12.sp,
                    color = Color(0xFFB0BEC5),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
