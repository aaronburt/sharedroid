package uk.co.aaronburt.sharedroid.s3

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.time.Clock

class S3Exception(val statusCode: Int, val code: String?, message: String) : Exception(message)

/**
 * Single-request PutObject uploads. The body is streamed and sent as UNSIGNED-PAYLOAD,
 * so large files are never read twice or held in memory. Multipart uploads come later.
 */
class S3Uploader(
    private val client: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
) {
    data class Result(val key: String, val url: String, val etag: String?)

    fun putObject(
        config: S3Config,
        key: String,
        contentType: String,
        contentLength: Long,
        open: () -> InputStream,
        onProgress: (bytesSent: Long) -> Unit = {},
    ): Result {
        val extra = buildMap {
            put("content-type", contentType)
            config.acl?.let { put("x-amz-acl", it) }
            config.storageClass?.let { put("x-amz-storage-class", it) }
            config.cacheControl?.let { put("cache-control", it) }
        }
        val body = StreamingBody(contentType, contentLength, open, onProgress)
        val response = execute(config, "PUT", key, extra, SigV4Signer.UNSIGNED_PAYLOAD, body)
        return Result(key, config.publicUrl(key), response)
    }

    fun deleteObject(config: S3Config, key: String) {
        execute(config, "DELETE", key, emptyMap(), SigV4Signer.EMPTY_SHA256, null)
    }

    /** What [testConnection] learned. [canDelete] is false when the key may write but not delete. */
    data class TestResult(val canDelete: Boolean)

    /**
     * Uploads and deletes a tiny object to prove the credentials can write to the bucket.
     * Throws if the upload fails. A refused delete is reported, not thrown, because uploads still work.
     */
    fun testConnection(config: S3Config): TestResult {
        val key = ".sharedroid-test-${System.currentTimeMillis()}"
        val bytes = "ok".toByteArray()
        val hash = SigV4Signer.sha256Hex(bytes)
        execute(config, "PUT", key, mapOf("content-type" to "text/plain"), hash, bytes.toRequestBody("text/plain".toMediaTypeOrNull()))
        return try {
            deleteObject(config, key)
            TestResult(canDelete = true)
        } catch (e: S3Exception) {
            if (e.statusCode == 403) TestResult(canDelete = false) else throw e
        }
    }

    /** Returns the ETag header of a successful response. */
    private fun execute(
        config: S3Config,
        method: String,
        key: String,
        headers: Map<String, String>,
        payloadHash: String,
        body: RequestBody?,
    ): String? {
        val path = config.objectPath(key)
        val signed = SigV4Signer.sign(
            SigV4Signer.Request(method, path, emptyMap(), headers + ("host" to config.requestHost), payloadHash),
            config.accessKeyId,
            config.secretAccessKey,
            config.region,
            clock.instant(),
        )
        val request = Request.Builder()
            .url(config.objectUrl(key))
            .method(method, body)
            .apply { (headers + signed).forEach { (k, v) -> header(k, v) } }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) return response.header("ETag")
            val xml = response.body?.string().orEmpty()
            val code = Regex("<Code>(.*?)</Code>").find(xml)?.groupValues?.get(1)
            val message = Regex("<Message>(.*?)</Message>").find(xml)?.groupValues?.get(1)
            throw S3Exception(response.code, code, listOfNotNull(code, message).joinToString(": ").ifEmpty { "HTTP ${response.code}" })
        }
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
