package uk.co.aaronburt.sharedroid.http

import org.json.JSONArray
import org.json.JSONObject

enum class BodyType { MULTIPART, BINARY }

/**
 * A "custom uploader": one POST or PUT request that sends the file, plus how to find the link in the reply.
 * The fields mirror ShareX's custom uploader so a `.sxcu` file can be imported onto this later.
 *
 * Values in [url], [query], [headers] and [fields] may use input tokens, see [Templates.input].
 * [linkTemplate] and [deletionTemplate] use reply tokens, see [ResponseTemplate].
 */
data class HttpConfig(
    val method: String = "POST",
    val url: String = "",
    val query: List<Pair<String, String>> = emptyList(),
    val headers: List<Pair<String, String>> = emptyList(),
    val bodyType: BodyType = BodyType.MULTIPART,
    /** Name of the form field that carries the file. Multipart only. */
    val fileField: String = "file",
    /** Extra form fields sent alongside the file. Multipart only. */
    val fields: List<Pair<String, String>> = emptyList(),
    val linkTemplate: String = DEFAULT_LINK,
    /** Optional. When it renders to a URL, that URL is requested to delete the upload. */
    val deletionTemplate: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("method", method)
        put("url", url)
        put("query", pairs(query))
        put("headers", pairs(headers))
        put("bodyType", bodyType.name)
        put("fileField", fileField)
        put("fields", pairs(fields))
        put("link", linkTemplate)
        put("deletion", deletionTemplate)
    }.toString()

    companion object {
        const val DEFAULT_LINK = "\$json:url\$"

        fun fromJson(text: String): HttpConfig {
            val o = JSONObject(text)
            return HttpConfig(
                method = o.optString("method", "POST"),
                url = o.optString("url"),
                query = readPairs(o.optJSONArray("query")),
                headers = readPairs(o.optJSONArray("headers")),
                bodyType = runCatching { BodyType.valueOf(o.optString("bodyType")) }.getOrDefault(BodyType.MULTIPART),
                fileField = o.optString("fileField", "file"),
                fields = readPairs(o.optJSONArray("fields")),
                linkTemplate = o.optString("link", DEFAULT_LINK),
                deletionTemplate = o.optString("deletion"),
            )
        }

        /** One `name<separator>value` per line. Blank lines are skipped; a line with no separator is a name with no value. */
        fun parsePairs(text: String, separator: Char): List<Pair<String, String>> = text.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@mapNotNull null
            val at = line.indexOf(separator)
            if (at < 0) line to "" else line.substring(0, at).trim() to line.substring(at + 1).trim()
        }.filter { it.first.isNotEmpty() }

        fun formatPairs(pairs: List<Pair<String, String>>, separator: Char): String =
            pairs.joinToString("\n") { (k, v) -> if (separator == ':') "$k: $v" else "$k$separator$v" }

        private fun pairs(list: List<Pair<String, String>>) = JSONArray().apply {
            list.forEach { (k, v) -> put(JSONArray().put(k).put(v)) }
        }

        private fun readPairs(array: JSONArray?): List<Pair<String, String>> =
            if (array == null) emptyList() else (0 until array.length()).mapNotNull { i ->
                array.optJSONArray(i)?.let { it.optString(0) to it.optString(1) }
            }
    }
}
