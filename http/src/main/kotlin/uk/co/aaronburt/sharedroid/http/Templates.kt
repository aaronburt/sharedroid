package uk.co.aaronburt.sharedroid.http

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.SecureRandom
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Tokens usable in requests and remote paths:
 * `{filename}` `{name}` `{ext}` `{mime}` `{uuid}` `{timestamp}` `{random:N}` and the date parts
 * `{yyyy}` `{yy}` `{MM}` `{dd}` `{HH}` `{mm}` `{ss}`.
 */
object Templates {
    private val TOKEN = Regex("\\{([a-zA-Z]+)(?::(\\d+))?\\}")
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val random = SecureRandom()

    fun input(template: String, fileName: String, mime: String, now: ZonedDateTime = ZonedDateTime.now()): String {
        val dot = fileName.lastIndexOf('.')
        val name = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot + 1) else ""
        return TOKEN.replace(template) { m ->
            when (m.groupValues[1]) {
                "yyyy" -> "%04d".format(now.year)
                "yy" -> "%02d".format(now.year % 100)
                "MM" -> "%02d".format(now.monthValue)
                "dd" -> "%02d".format(now.dayOfMonth)
                "HH" -> "%02d".format(now.hour)
                "mm" -> "%02d".format(now.minute)
                "ss" -> "%02d".format(now.second)
                "filename" -> fileName
                "name" -> name
                "ext" -> ext
                "mime" -> mime
                "uuid" -> UUID.randomUUID().toString()
                "timestamp" -> now.toEpochSecond().toString()
                "random" -> buildString {
                    repeat((m.groupValues[2].toIntOrNull() ?: 8).coerceIn(1, 64)) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
                }
                else -> m.value
            }
        }
    }
}

class HttpReply(val body: String, val headers: Map<String, String>, val url: String)

/**
 * Pulls values out of the server's reply, using the same syntax as ShareX:
 * `$response$` `$responseurl$` `$header:Name$` `$json:path.to[0].value$` `$regex:pattern|group$`.
 */
object ResponseTemplate {
    private val TOKEN = Regex("""\$(\w+)(?::([^$]*))?\$""")
    private val PATH_STEP = Regex("""([^.\[\]]+)|\[(\d+)]""")

    fun render(template: String, reply: HttpReply): String = TOKEN.replace(template) { m ->
        val arg = m.groupValues[2]
        when (m.groupValues[1].lowercase()) {
            "response" -> reply.body
            "responseurl" -> reply.url
            "header" -> reply.headers.entries.firstOrNull { it.key.equals(arg.trim(), ignoreCase = true) }?.value.orEmpty()
            "json" -> jsonPath(reply.body, arg.trim())
            "regex" -> regex(reply.body, arg)
            else -> m.value
        }
    }

    internal fun jsonPath(body: String, path: String): String {
        var node: Any? = runCatching { JSONTokener(body).nextValue() }.getOrNull() ?: return ""
        for (step in PATH_STEP.findAll(path.removePrefix("$").trimStart('.'))) {
            val key = step.groupValues[1]
            node = if (key.isNotEmpty()) {
                (node as? JSONObject)?.opt(key)
            } else {
                (node as? JSONArray)?.opt(step.groupValues[2].toInt())
            }
            if (node == null || node == JSONObject.NULL) return ""
        }
        return node.toString()
    }

    private fun regex(body: String, arg: String): String {
        val bar = arg.lastIndexOf('|')
        val spec = if (bar >= 0) arg.substring(bar + 1).trim() else ""
        val simpleGroup = spec.isNotEmpty() && spec.all { it.isLetterOrDigit() || it == '_' }
        val pattern = if (simpleGroup) arg.substring(0, bar) else arg
        val match = runCatching { Regex(pattern).find(body) }.getOrNull() ?: return ""
        if (!simpleGroup) return match.groups[if (match.groups.size > 1) 1 else 0]?.value.orEmpty()
        spec.toIntOrNull()?.let { index -> return match.groups.let { if (index < it.size) it[index]?.value else null }.orEmpty() }
        return runCatching { (match.groups as MatchNamedGroupCollection)[spec]?.value }.getOrNull().orEmpty()
    }
}
