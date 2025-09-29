package io.heckel.ntfy.util.emoji

import io.heckel.ntfy.util.emoji.EmojiLoader.loadEmojis

/**
 * Holds the loaded emojis and provides search functions.
 *
 * This class was originally written by Vincent DURMONT (vdurmont@gmail.com) as part of
 * https://github.com/vdurmont/emoji-java, but has since been heavily stripped and modified.
 */
object EmojiManager {
    private const val PATH =
        "/emoji.json" // https://github.com/github/gemoji/blob/master/db/emoji.json
    private val EMOJIS_BY_ALIAS: MutableMap<String, Emoji> = HashMap<String, Emoji>()

    init {
        try {
            val stream = EmojiLoader::class.java.getResourceAsStream(PATH)!!
            val emojis: MutableList<Emoji> = loadEmojis(stream)
            for (emoji in emojis) {
                for (alias in emoji.aliases) {
                    EMOJIS_BY_ALIAS.put(alias, emoji)
                }
            }
            stream.close()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun getForAlias(alias: String?): Emoji? {
        if (alias == null || alias.isEmpty()) {
            return null
        }
        return EMOJIS_BY_ALIAS.get(trimAlias(alias))
    }

    private fun trimAlias(alias: String): String {
        val len = alias.length
        return alias.substring(
            if (alias.get(0) == ':') 1 else 0,
            if (alias.get(len - 1) == ':') len - 1 else len
        )
    }
}
