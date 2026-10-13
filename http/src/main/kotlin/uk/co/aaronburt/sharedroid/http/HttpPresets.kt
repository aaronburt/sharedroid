package uk.co.aaronburt.sharedroid.http

/** A ready-made [HttpConfig] for a known upload service. Kept as plain data so more can be added, or imported, later. */
data class HttpPreset(
    val id: String,
    val name: String,
    /** One line shown in the picker. */
    val summary: String,
    /** Shown at the top of the form: what the service needs from the person. */
    val note: String?,
    val config: HttpConfig,
)

object HttpPresets {
    /** Anything starting with this in a preset is a value the person must replace before saving. */
    const val PLACEHOLDER_PREFIX = "YOUR_"

    val imgur = HttpPreset(
        id = "imgur",
        name = "Imgur",
        summary = "Anonymous image uploads. Needs a free Client ID.",
        note = "Imgur needs a free Client ID. Register an application at api.imgur.com (choose \"Anonymous usage without user " +
            "authorization\"), then replace YOUR_CLIENT_ID in the Authorization header below. Uploads are anonymous and public.",
        config = HttpConfig(
            url = "https://api.imgur.com/3/image",
            headers = listOf("Authorization" to "Client-ID ${PLACEHOLDER_PREFIX}CLIENT_ID"),
            fileField = "image",
            linkTemplate = "\$json:data.link\$",
        ),
    )

    val catbox = HttpPreset(
        id = "catbox",
        name = "Catbox",
        summary = "Free, no account. Files are kept.",
        note = "Anonymous and public: anyone with the link can open the file, and Catbox keeps it. Catbox's rules apply.",
        config = HttpConfig(
            url = "https://catbox.moe/user/api.php",
            fields = listOf("reqtype" to "fileupload"),
            fileField = "fileToUpload",
            linkTemplate = "\$response\$",
        ),
    )

    val litterbox = HttpPreset(
        id = "litterbox",
        name = "Litterbox",
        summary = "Free, no account. Files expire after 72 hours.",
        note = "Anonymous and public, and the file is deleted after 72 hours. Change the \"time\" field to 1h, 12h or 24h for " +
            "a shorter life.",
        config = HttpConfig(
            url = "https://litterbox.catbox.moe/resources/internals/api.php",
            fields = listOf("reqtype" to "fileupload", "time" to "72h"),
            fileField = "fileToUpload",
            linkTemplate = "\$response\$",
        ),
    )

    val all = listOf(imgur, catbox, litterbox)

    /** True when [config] still holds a value from a preset that the person was meant to fill in. */
    fun hasPlaceholder(config: HttpConfig): Boolean =
        (listOf(config.url) + config.headers.map { it.second } + config.query.map { it.second } + config.fields.map { it.second })
            .any { PLACEHOLDER_PREFIX in it }
}
