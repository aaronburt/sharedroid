package uk.co.aaronburt.sharedroid.s3

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

class S3UploaderTest {
    private val server = MockWebServer().apply { start() }

    private val config = S3Config(
        endpoint = "${server.hostName}:${server.port}",
        region = "us-east-1",
        bucket = "media",
        accessKeyId = "AKID",
        secretAccessKey = "secret",
        pathStyle = true,
        useHttps = false,
        acl = "public-read",
        publicUrlBase = "https://i.example.com/",
    )

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun putObjectSendsSignedStreamingRequest() {
        server.enqueue(MockResponse().setHeader("ETag", "\"abc\""))
        val data = "hello world".toByteArray()
        var progress = 0L

        val result = S3Uploader().putObject(config, "2026/a b.txt", "text/plain", data.size.toLong(), { data.inputStream() }) { progress = it }

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/media/2026/a%20b.txt", request.path)
        assertEquals("hello world", request.body.readUtf8())
        assertEquals("UNSIGNED-PAYLOAD", request.getHeader("x-amz-content-sha256"))
        assertEquals("public-read", request.getHeader("x-amz-acl"))
        val auth = request.getHeader("Authorization")!!
        assertTrue(auth, auth.startsWith("AWS4-HMAC-SHA256 Credential=AKID/"))
        assertTrue(auth, auth.contains("SignedHeaders=content-type;host;x-amz-acl;x-amz-content-sha256;x-amz-date,"))
        assertEquals(data.size.toLong(), progress)
        assertEquals("https://i.example.com/2026/a%20b.txt", result.url)
        assertEquals("\"abc\"", result.etag)
    }

    @Test
    fun errorsCarryTheS3Code() {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("<Error><Code>AccessDenied</Code><Message>Access Denied</Message></Error>"),
        )
        try {
            S3Uploader().putObject(config, "x", "text/plain", 1, { "x".byteInputStream() })
            fail("expected S3Exception")
        } catch (e: S3Exception) {
            assertEquals(403, e.statusCode)
            assertEquals("AccessDenied", e.code)
            assertEquals("AccessDenied: Access Denied", e.message)
        }
    }

    /** Runs against a real MinIO server when S3_IT_ENDPOINT is set, e.g. `localhost:9000`. */
    @Test
    fun minioRoundTrip() {
        val endpoint = System.getenv("S3_IT_ENDPOINT").orEmpty()
        assumeTrue("S3_IT_ENDPOINT not set", endpoint.isNotEmpty())
        val minio = S3Config(
            endpoint = endpoint,
            region = "us-east-1",
            bucket = System.getenv("S3_IT_BUCKET") ?: "sharedroid",
            accessKeyId = System.getenv("S3_IT_ACCESS_KEY") ?: "minioadmin",
            secretAccessKey = System.getenv("S3_IT_SECRET_KEY") ?: "minioadmin",
            pathStyle = true,
            useHttps = false,
        )
        val uploader = S3Uploader()
        uploader.testConnection(minio)

        val data = ByteArray(3 * 1024 * 1024) { it.toByte() }
        val result = uploader.putObject(minio, "it/big file.bin", "application/octet-stream", data.size.toLong(), { data.inputStream() })
        assertFalse(result.etag.isNullOrEmpty())
    }
}
