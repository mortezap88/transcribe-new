package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.GrammarSuggestion
import com.example.data.SuggestionType

@Composable
fun GrammarSuggestionDialog(
    suggestion: GrammarSuggestion,
    onApply: (GrammarSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    val isGrammar = suggestion.type == SuggestionType.GRAMMAR
    val accentColor = if (isGrammar) Color(0xFFEF5350) else Color(0xFFFFB300)
    val containerBg = if (isGrammar) Color(0xFF241010) else Color(0xFF261D07)
    val titleText = if (isGrammar) "Grammar Mistake" else "Word Choice Improvement"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("grammar_suggestion_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isGrammar) Icons.Default.ErrorOutline else Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = titleText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Comparison Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(containerBg)
                        .padding(14.dp)
                ) {
                    Column {
                        // Original
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Original:",
                                fontSize = 12.sp,
                                color = Color(0xFFAAAAAA),
                                modifier = Modifier.width(68.dp)
                            )
                            Text(
                                text = suggestion.original,
                                fontSize = 15.sp,
                                color = Color(0xFFE57373),
                                textDecoration = TextDecoration.LineThrough,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Replacement
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Suggest:",
                                fontSize = 12.sp,
                                color = Color(0xFFAAAAAA),
                                modifier = Modifier.width(68.dp)
                            )
                            Text(
                                text = suggestion.replacement,
                                fontSize = 16.sp,
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Explanation
                Text(
                    text = "Why:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9E9E9E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = suggestion.reason,
                    fontSize = 13.sp,
                    color = Color(0xFFE0E0E0),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA))
                    ) {
                        Text("Ignore", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onApply(suggestion)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("apply_correction_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
