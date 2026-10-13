package uk.co.aaronburt.sharedroid.http

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

/** The server answered with an error status. */
class HttpUploadException(val statusCode: Int, message: String) : Exception(message)

/** The upload worked but the reply didn't contain a link where the destination said it would be. */
class HttpLinkException(message: String) : Exception(message)

/** Sends a file to any URL as configured by an [HttpConfig]. The file is streamed, never held in memory. */
class HttpUploader(private val client: OkHttpClient = OkHttpClient()) {
    data class Result(val url: String, val deletionUrl: String?)

    fun upload(
        config: HttpConfig,
        fileName: String,
        contentType: String,
        contentLength: Long,
        open: () -> InputStream,
        onProgress: (bytesSent: Long) -> Unit = {},
    ): Result {
        fun fill(value: String) = Templates.input(value, fileName, contentType)

        val base = fill(config.url.trim()).toHttpUrlOrNull() ?: throw IllegalArgumentException("The URL isn't valid")
        val url = base.newBuilder().apply {
            config.query.filter { it.first.isNotBlank() }.forEach { (k, v) -> addQueryParameter(k, fill(v)) }
        }.build()

        val file = StreamingBody(contentType, contentLength, open, onProgress)
        val body: RequestBody = when (config.bodyType) {
            BodyType.BINARY -> file
            BodyType.MULTIPART -> MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                config.fields.filter { it.first.isNotBlank() }.forEach { (k, v) -> addFormDataPart(k, fill(v)) }
                addFormDataPart(config.fileField.ifBlank { "file" }, fileName, file)
            }.build()
        }

        val method = if (config.method.equals("PUT", ignoreCase = true)) "PUT" else "POST"
        val request = Request.Builder().url(url).method(method, body).apply {
            config.headers.filter { it.first.isNotBlank() }.forEach { (k, v) -> header(k, fill(v)) }
        }.build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpUploadException(response.code, snippet(text).ifEmpty { "HTTP ${response.code}" })

            val reply = HttpReply(text, response.headers.names().associateWith { response.header(it).orEmpty() }, response.request.url.toString())
            val link = ResponseTemplate.render(config.linkTemplate, reply).trim()
            if (link.isEmpty()) {
                throw HttpLinkException("The reply had no link at ${config.linkTemplate}. It said: ${snippet(text).ifEmpty { "(nothing)" }}")
            }
            val deletion = config.deletionTemplate.takeIf { it.isNotBlank() }?.let { ResponseTemplate.render(it, reply).trim() }
            return Result(link, deletion?.takeIf { it.isNotEmpty() })
        }
    }

    /** Requests a deletion link. A 404 counts as success: the file is already gone. */
    fun delete(url: String) {
        val request = Request.Builder().url(url.toHttpUrlOrNull() ?: throw IllegalArgumentException("The deletion URL isn't valid")).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw HttpUploadException(response.code, snippet(response.body?.string().orEmpty()).ifEmpty { "HTTP ${response.code}" })
            }
        }
    }

    private fun snippet(text: String) = text.trim().replace(Regex("\\s+"), " ").take(200)

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
