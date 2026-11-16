package com.taki.tiktokcompanion.data

data class MessageRecord(
    val id: Long,
    val packageName: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val editedDisplay: String?,
    val note: String?
) {
    val displayText: String
        get() = editedDisplay?.takeIf { it.isNotBlank() } ?: body
}

data class DraftRecord(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class StreakSettings(
    val enabled: Boolean,
    val trigger: String,
    val replyTemplate: String
)
