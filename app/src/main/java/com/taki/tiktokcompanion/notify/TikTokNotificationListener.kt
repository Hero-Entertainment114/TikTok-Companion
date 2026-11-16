package com.taki.tiktokcompanion.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.taki.tiktokcompanion.data.StreakSettings
import com.taki.tiktokcompanion.data.VaultDb
import java.util.concurrent.Executors

/**
 * Saves text Android exposes in TikTok notifications. The streak helper only mirrors a
 * TikTok-provided RemoteInput action into a Companion notification; the user must tap
 * the action and confirm the reply. It never sends unattended messages or fabricates data.
 */
class TikTokNotificationListener : NotificationListenerService() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var database: VaultDb
    private val lastStreakPromptAt = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        database = VaultDb(applicationContext)
        createStreakChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in SUPPORTED_TIKTOK_PACKAGES) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: Bundle.EMPTY
        val title = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
        ).mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull().orEmpty()

        val body = extractVisibleText(extras)
        if (body.isBlank()) return

        val key = runCatching { sbn.key }.getOrNull().orEmpty().ifBlank { "${sbn.id}:${sbn.tag.orEmpty()}" }
        val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        io.execute {
            runCatching {
                database.saveNotification(sbn.packageName, title, body, postedAt, key)
                maybePostStreakQuickReply(notification, title, body, database.getStreakSettings())
            }.onFailure {
                // Never log notification text or any message content.
                Log.w(TAG, "Could not process a TikTok notification", it)
            }
        }
    }

    private fun maybePostStreakQuickReply(
        notification: Notification,
        senderTitle: String,
        body: String,
        settings: StreakSettings
    ) {
        if (!settings.enabled || settings.trigger.isBlank()) return
        if (!body.contains(settings.trigger, ignoreCase = true)) return
        val senderKey = senderTitle.trim().lowercase().ifBlank { "tiktok" }
        val now = System.currentTimeMillis()
        val previousPrompt = lastStreakPromptAt[senderKey] ?: 0L
        if (now - previousPrompt < PROMPT_COOLDOWN_MS) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val action = notification.actions.orEmpty().firstOrNull { candidate ->
            val actionTitle = candidate.title?.toString().orEmpty().lowercase()
            val looksLikeReply = notification.category == Notification.CATEGORY_MESSAGE ||
                actionTitle.contains("reply") || actionTitle.contains("trả lời")
            candidate.actionIntent != null && looksLikeReply &&
                candidate.remoteInputs?.any { it.allowFreeFormInput } == true
        } ?: return
        val originalInput = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return

        val choices = settings.replyTemplate.trim().takeIf { it.isNotBlank() }?.let { arrayOf(it) }
        val inputBuilder = RemoteInput.Builder(originalInput.resultKey)
            .setLabel("Trả lời chuỗi…")
            .setAllowFreeFormInput(originalInput.allowFreeFormInput)
        if (choices != null) inputBuilder.setChoices(choices)
        originalInput.extras?.let { inputBuilder.addExtras(it) }
        val replyInput = inputBuilder.build()

        val replyAction = Notification.Action.Builder(
            action.icon ?: Icon.createWithResource(this, android.R.drawable.sym_action_chat),
            "Trả lời chuỗi",
            action.actionIntent
        )
            .addRemoteInput(replyInput)
            .setAllowGeneratedReplies(true)
            .build()

        val safeSender = senderTitle.ifBlank { "cuộc trò chuyện TikTok" }.take(64)
        val companionNotification = Notification.Builder(this, STREAK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Có thể giữ chuỗi với $safeSender")
            .setContentText("Nhấn ‘Trả lời chuỗi’ để mở trả lời nhanh của TikTok.")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(replyAction)
            .setTimeoutAfter(15 * 60 * 1000L)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(senderKey.hashCode(), companionNotification)
        lastStreakPromptAt[senderKey] = now
    }

    private fun createStreakChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                STREAK_CHANNEL_ID,
                "Gợi ý trả lời chuỗi",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo gợi ý dùng nút trả lời nhanh có sẵn của TikTok."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun extractVisibleText(extras: Bundle): String {
        // Messaging-style notifications may expose a list of message Bundles on Android 7+.
        val messageBundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val messageTexts = messageBundles.orEmpty().mapNotNull { item ->
            (item as? Bundle)?.getCharSequence("text")?.toString()?.trim()?.takeIf(String::isNotEmpty)
        }
        if (messageTexts.isNotEmpty()) return messageTexts.distinct().joinToString("\n")

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString().trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (lines.isNotEmpty()) return lines.distinct().joinToString("\n")

        return sequenceOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
        ).mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull().orEmpty()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TikTokCompanion"
        private const val STREAK_CHANNEL_ID = "streak_quick_reply"
        private const val PROMPT_COOLDOWN_MS = 30 * 60 * 1000L
        val SUPPORTED_TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically", // TikTok global
            "com.ss.android.ugc.trill"  // TikTok package used by some regional builds
        )
    }
}
