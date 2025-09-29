package io.heckel.ntfy.util.emoji

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException

/**
 * Loads the emojis from a JSON database.
 *
 * This was originally written to load
 * https://github.com/vdurmont/emoji-java/blob/master/src/main/resources/emojis.json
 *
 * But now uses
 * https://github.com/github/gemoji/blob/master/db/emoji.json
 *
 * This class was originally written by Vincent DURMONT (vdurmont@gmail.com) as part of
 * https://github.com/vdurmont/emoji-java, but has since been heavily stripped and modified.
 */
object EmojiLoader {
    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun loadEmojis(stream: InputStream): MutableList<Emoji> {
        val emojisJSON = JSONArray(inputStreamToString(stream))
        val emojis: MutableList<Emoji> = ArrayList<Emoji>(emojisJSON.length())
        for (i in 0..<emojisJSON.length()) {
            val emoji = buildEmojiFromJSON(emojisJSON.getJSONObject(i))
            if (emoji != null) {
                emojis.add(emoji)
            }
        }
        return emojis
    }

    @Throws(IOException::class)
    private fun inputStreamToString(
        stream: InputStream
    ): String {
        val sb = StringBuilder()
        val isr = InputStreamReader(stream, "UTF-8")
        val br = BufferedReader(isr)
        var read: String?
        while ((br.readLine().also { read = it }) != null) {
            sb.append(read)
        }
        br.close()
        return sb.toString()
    }

    @Throws(UnsupportedEncodingException::class, JSONException::class)
    internal fun buildEmojiFromJSON(
        json: JSONObject
    ): Emoji? {
        if (!json.has("emoji")) {
            return null
        }

        val bytes = json.getString("emoji").toByteArray(charset("UTF-8"))
        val aliases = jsonArrayToStringList(json.getJSONArray("aliases"))
        return Emoji(aliases, *bytes)
    }

    @Throws(JSONException::class)
    private fun jsonArrayToStringList(array: JSONArray): MutableList<String> {
        val strings: MutableList<String> = ArrayList<String>(array.length())
        for (i in 0..<array.length()) {
            strings.add(array.getString(i))
        }
        return strings
    }
}
