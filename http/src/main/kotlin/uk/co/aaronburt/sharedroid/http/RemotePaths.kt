package uk.co.aaronburt.sharedroid.http

import java.net.URLEncoder

/** What a Test connection learned. [canDelete] is false when uploads work but deleting is refused. */
data class RemoteTestResult(val canDelete: Boolean)

/** Remote file paths for the folder-based destinations (WebDAV, FTP), built from the same tokens as everything else. */
object RemotePaths {
    private const val SAMPLE_RANDOM = "k3x9p2mqv7ht4wzc8yfdn6ra5bju2esg"
    private const val SAMPLE_UUID = "3f9a1c2e-77b4-4e0b-9d1a-5c2f8e6a1b03"
    private val RANDOM_TOKEN = Regex("\\{random(?::(\\d+))?\\}")

    /** Fills in the tokens and makes the result safe to use as a path below the destination's folder. */
    fun render(template: String, fileName: String, mime: String): String =
        clean(Templates.input(template, fileName, mime))

    /** Like [render] but with fixed random parts, so a preview doesn't change on every redraw. */
    fun preview(template: String, fileName: String, mime: String): String =
        render(
            template
                .replace(RANDOM_TOKEN) { m -> SAMPLE_RANDOM.take((m.groupValues[1].toIntOrNull() ?: 8).coerceIn(1, SAMPLE_RANDOM.length)) }
                .replace("{uuid}", SAMPLE_UUID),
            fileName, mime,
        )

    /** Drops empty and `.` segments and defuses `..`, so a file name can never climb out of the folder. */
    fun clean(path: String): String =
        path.replace('\\', '/').split('/')
            .filter { it.isNotEmpty() && it != "." }
            .joinToString("/") { if (it == "..") "_" else it }
            .ifEmpty { "file" }

    /** Percent-encodes each segment, keeping the slashes. */
    fun encode(path: String): String =
        path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    fun parentDirs(path: String): List<String> = path.split('/').dropLast(1)

    fun joinUrl(base: String, path: String): String = base.trimEnd('/') + "/" + encode(path)
}
