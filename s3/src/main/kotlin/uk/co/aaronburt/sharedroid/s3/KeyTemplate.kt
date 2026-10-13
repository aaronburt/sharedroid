package uk.co.aaronburt.sharedroid.s3

import java.security.SecureRandom
import java.time.ZonedDateTime
import java.util.UUID

/**
 * ShareX-style object key templates.
 *
 * Tokens: `{yyyy}` `{yy}` `{MM}` `{dd}` `{HH}` `{mm}` `{ss}` `{timestamp}` `{uuid}`
 * `{random}` / `{random:N}` (alphanumeric, default 8) `{filename}` (name without extension) `{ext}`.
 * Unknown tokens are left as written so typos are visible in the result.
 */
object KeyTemplate {
    const val DEFAULT = "{yyyy}/{MM}/{random:8}.{ext}"

    private val TOKEN = Regex("\\{([a-zA-Z]+)(?::(\\d+))?\\}")
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val random = SecureRandom()

    fun render(template: String, originalFilename: String, now: ZonedDateTime = ZonedDateTime.now()): String {
        val dot = originalFilename.lastIndexOf('.')
        val base = if (dot > 0) originalFilename.substring(0, dot) else originalFilename
        val ext = if (dot > 0) originalFilename.substring(dot + 1).lowercase() else "bin"

        val key = TOKEN.replace(template) { m ->
            val arg = m.groupValues[2].toIntOrNull()
            when (m.groupValues[1]) {
                "yyyy" -> "%04d".format(now.year)
                "yy" -> "%02d".format(now.year % 100)
                "MM" -> "%02d".format(now.monthValue)
                "dd" -> "%02d".format(now.dayOfMonth)
                "HH" -> "%02d".format(now.hour)
                "mm" -> "%02d".format(now.minute)
                "ss" -> "%02d".format(now.second)
                "timestamp" -> now.toEpochSecond().toString()
                "uuid" -> UUID.randomUUID().toString()
                "random" -> randomString(arg ?: 8)
                "filename" -> sanitize(base)
                "ext" -> ext
                else -> m.value
            }
        }
        return key.trimStart('/')
    }

    private fun randomString(length: Int) = buildString {
        repeat(length.coerceIn(1, 64)) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** Keeps filenames readable in URLs: spaces become dashes, path separators are removed. */
    private fun sanitize(name: String) = name.replace('/', '_').replace('\\', '_').replace(Regex("\\s+"), "-")
}
