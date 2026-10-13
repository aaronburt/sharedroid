package uk.co.aaronburt.sharedroid.http

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.InputStream

/**
 * A WebDAV folder, such as a Nextcloud or ownCloud account.
 *
 * [url] is the address of the folder uploads go into, for Nextcloud `https://host/remote.php/dav/files/USER/`.
 * [publicUrlBase] is where those same files can be opened by whoever gets the link. If it is blank the link is the
 * WebDAV address itself, which needs the account's password to open.
 */
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val pathTemplate: String = DEFAULT_PATH,
    val publicUrlBase: String = "",
) {
    fun fileUrl(path: String): String = RemotePaths.joinUrl(url, path)

    fun linkFor(path: String): String =
        if (publicUrlBase.isNotBlank()) RemotePaths.joinUrl(publicUrlBase, path) else fileUrl(path)

    fun toJson(): String = JSONObject().apply {
        put("url", url)
        put("username", username)
        put("password", password)
        put("path", pathTemplate)
        put("publicUrlBase", publicUrlBase)
    }.toString()

    companion object {
        const val DEFAULT_PATH = "ShareDroid/{yyyy}/{MM}/{random:8}.{ext}"

        fun fromJson(text: String): WebDavConfig {
            val o = JSONObject(text)
            return WebDavConfig(
                url = o.optString("url"),
                username = o.optString("username"),
                password = o.optString("password"),
                pathTemplate = o.optString("path", DEFAULT_PATH),
                publicUrlBase = o.optString("publicUrlBase"),
            )
        }
    }
}

/** Uploads over WebDAV: a PUT per file, creating missing folders with MKCOL, and a DELETE to remove it. */
class WebDavUploader(private val client: OkHttpClient = OkHttpClient()) {

    fun upload(
        config: WebDavConfig,
        path: String,
        contentType: String,
        contentLength: Long,
        open: () -> InputStream,
        onProgress: (bytesSent: Long) -> Unit = {},
    ) {
        try {
            put(config, path, contentType, contentLength, open, onProgress)
        } catch (e: HttpUploadException) {
            // 409 Conflict means a parent folder is missing. Create them and try once more.
            if (e.statusCode != 409) throw e
            makeParents(config, path)
            put(config, path, contentType, contentLength, open, onProgress)
        }
    }

    /** A 404 counts as success: the file is already gone. */
    fun delete(config: WebDavConfig, path: String) {
        val request = authed(config, Request.Builder().url(config.fileUrl(path))).delete().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) throw failure(response.code, response.body?.string())
        }
    }

    /** Uploads and deletes a tiny file in the folder. A refused delete is reported rather than thrown. */
    fun test(config: WebDavConfig): RemoteTestResult {
        val path = ".sharedroid-test-${System.currentTimeMillis()}.txt"
        val bytes = "ok".toByteArray()
        put(config, path, "text/plain", bytes.size.toLong(), { bytes.inputStream() }, {})
        return try {
            delete(config, path)
            RemoteTestResult(canDelete = true)
        } catch (e: HttpUploadException) {
            if (e.statusCode in listOf(401, 403, 405)) RemoteTestResult(canDelete = false) else throw e
        }
    }

    private fun put(
        config: WebDavConfig,
        path: String,
        contentType: String,
        contentLength: Long,
        open: () -> InputStream,
        onProgress: (Long) -> Unit,
    ) {
        val body = StreamingBody(contentType, contentLength, open, onProgress)
        val request = authed(config, Request.Builder().url(config.fileUrl(path))).put(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw failure(response.code, response.body?.string())
        }
    }

    private fun makeParents(config: WebDavConfig, path: String) {
        var prefix = ""
        for (dir in RemotePaths.parentDirs(path)) {
            prefix = if (prefix.isEmpty()) dir else "$prefix/$dir"
            val request = authed(config, Request.Builder().url(config.fileUrl(prefix)))
                .method("MKCOL", ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(request).execute().use { response ->
                // 405 means the folder already exists.
                if (!response.isSuccessful && response.code != 405) throw failure(response.code, response.body?.string())
            }
        }
    }

    private fun authed(config: WebDavConfig, builder: Request.Builder): Request.Builder =
        if (config.username.isEmpty() && config.password.isEmpty()) builder
        else builder.header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))

    private fun failure(code: Int, body: String?): HttpUploadException {
        val text = body.orEmpty().replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(200)
        return HttpUploadException(code, text.ifEmpty { "HTTP $code" })
    }

    private class StreamingBody(
        private val contentType: String,
        private val length: Long,
        private val open: () -> InputStream,
        private val onProgress: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = contentType.toMediaTypeOrNull()
        override fun contentLength() = length
        override fun writeTo(sink: BufferedSink) {
            open().source().use { source ->
                var sent = 0L
                while (true) {
                    val read = source.read(sink.buffer, 64 * 1024L)
                    if (read == -1L) break
                    sink.emit()
                    sent += read
                    onProgress(sent)
                }
            }
        }
    }
}
