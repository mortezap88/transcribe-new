package com.example.data

import java.util.UUID

enum class SuggestionType {
    GRAMMAR,     // Red: Grammatical error (e.g. subject-verb agreement, tense)
    WORD_CHOICE  // Yellow: Stylistic improvement or better vocabulary choice
}

data class GrammarSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val original: String,
    val replacement: String,
    val type: SuggestionType,
    val reason: String
)
