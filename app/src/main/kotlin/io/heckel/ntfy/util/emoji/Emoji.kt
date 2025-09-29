package io.heckel.ntfy.util.emoji

import java.io.UnsupportedEncodingException
import java.util.Collections

/**
 * This class represents an emoji.
 *
 * This class was originally written by Vincent DURMONT (vdurmont@gmail.com) as part of
 * https://github.com/vdurmont/emoji-java, but has since been heavily stripped and modified.
 */
class Emoji(aliases: MutableList<String>, vararg bytes: Byte) {
    @JvmField
    val aliases: MutableList<String>
    public var unicode: String? = null

    init {
        this.aliases = Collections.unmodifiableList<String>(aliases)
        try {
            this.unicode = String(bytes, charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(e)
        }
    }
}
