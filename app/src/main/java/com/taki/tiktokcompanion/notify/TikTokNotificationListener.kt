package com.taki.tiktokcompanion.notify

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.taki.tiktokcompanion.data.VaultDb
import java.util.concurrent.Executors

/**
 * Captures only visible notification text from TikTok package IDs.
 * It cannot read TikTok's private database or recover notifications that Android never receives.
 */
class TikTokNotificationListener : NotificationListenerService() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var database: VaultDb

    override fun onCreate() {
        super.onCreate()
        database = VaultDb(applicationContext)
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
            }.onFailure {
                // Do not log notification text or other message content.
                Log.w(TAG, "Could not store a TikTok notification", it)
            }
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
        val SUPPORTED_TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically", // TikTok global
            "com.ss.android.ugc.trill"  // TikTok package used by some regional builds
        )
    }
}
