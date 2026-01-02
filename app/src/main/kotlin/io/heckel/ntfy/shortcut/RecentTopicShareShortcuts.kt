package io.heckel.ntfy.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.heckel.ntfy.R
import io.heckel.ntfy.ui.ShareActivity
import io.heckel.ntfy.util.shortUrl
import io.heckel.ntfy.util.splitTopicUrl
import java.security.MessageDigest

object RecentTopicShareShortcuts {
    const val SHARE_TARGET_CATEGORY = "io.heckel.ntfy.ksmaze.SHARE_TO_TOPIC"
    const val EXTRA_TOPIC_URL = "io.heckel.ntfy.ksmaze.extra.TOPIC_URL"

    fun update(context: Context, topicUrls: List<String>) {
        val shortcuts = topicUrls
            .asReversed()
            .distinct()
            .mapNotNull { topicUrl ->
                val (baseUrl, topic) = try {
                    splitTopicUrl(topicUrl)
                } catch (_: Exception) {
                    return@mapNotNull null
                }

                val shortLabel = topic
                val longLabel = shortUrl("$baseUrl/$topic")

                val intent = Intent(context, ShareActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(EXTRA_TOPIC_URL, topicUrl)
                }

                ShortcutInfoCompat.Builder(context, shortcutIdForTopicUrl(topicUrl))
                    .setShortLabel(shortLabel)
                    .setLongLabel(longLabel)
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(intent)
                    .setCategories(setOf(SHARE_TARGET_CATEGORY))
                    .build()
            }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    fun resolveTopicUrlFromShareIntent(intent: Intent?, lastShareTopicUrls: List<String>): String? {
        if (intent == null) return null

        intent.getStringExtra(EXTRA_TOPIC_URL)?.let { return it }

        val shortcutId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID) ?: return null
        return lastShareTopicUrls.firstOrNull { shortcutIdForTopicUrl(it) == shortcutId }
    }

    private fun shortcutIdForTopicUrl(topicUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(topicUrl.toByteArray())
        val hex = digest.take(8).joinToString(separator = "") { b -> "%02x".format(b) }
        return "share_topic_$hex"
    }
}
