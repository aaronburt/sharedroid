package uk.co.aaronburt.sharedroid.http

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpUploaderTest {
    private lateinit var server: MockWebServer
    private val uploader = HttpUploader()
    private val bytes = "hello world".toByteArray()

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() { server.shutdown() }

    private fun send(config: HttpConfig, name: String = "pic one.png", mime: String = "image/png") =
        uploader.upload(config, name, mime, bytes.size.toLong(), { bytes.inputStream() })

    @Test
    fun multipartWithFieldsHeadersAndJsonLink() {
        server.enqueue(MockResponse().setBody("""{"data":{"link":"https://i.example/a.png","del":"https://i.example/del/9"}}"""))
        val result = send(
            HttpConfig(
                url = server.url("/upload").toString(),
                query = listOf("album" to "{ext}"),
                headers = listOf("Authorization" to "Bearer abc", "X-Name" to "{name}"),
                fileField = "image",
                fields = listOf("title" to "{filename}"),
                linkTemplate = "\$json:data.link\$",
                deletionTemplate = "\$json:data.del\$",
            ),
        )
        assertEquals("https://i.example/a.png", result.url)
        assertEquals("https://i.example/del/9", result.deletionUrl)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/upload?album=png", request.path)
        assertEquals("Bearer abc", request.getHeader("Authorization"))
        assertEquals("pic one", request.getHeader("X-Name"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("name=\"image\"; filename=\"pic one.png\""))
        assertTrue(body, body.contains("name=\"title\""))
        assertTrue(body.contains("hello world"))
    }

    @Test
    fun rawBinaryPutAndHeaderLink() {
        server.enqueue(MockResponse().setHeader("Location", "https://files.example/x").setBody("created"))
        val result = send(
            HttpConfig(method = "PUT", url = server.url("/").toString() + "files/{filename}", bodyType = BodyType.BINARY, linkTemplate = "\$header:location\$"),
            name = "a.txt", mime = "text/plain",
        )
        assertEquals("https://files.example/x", result.url)
        assertNull(result.deletionUrl)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/files/a.txt", request.path)
        assertEquals("hello world", request.body.readUtf8())
        assertEquals("text/plain", request.getHeader("Content-Type"))
    }

    @Test
    fun regexAndWholeBodyLinks() {
        server.enqueue(MockResponse().setBody("OK id=ab12 done"))
        assertEquals(
            "https://x.example/ab12",
            send(HttpConfig(url = server.url("/").toString(), linkTemplate = "https://x.example/\$regex:id=(\\w+)|1\$")).url,
        )
        server.enqueue(MockResponse().setBody("https://plain.example/z\n"))
        assertEquals("https://plain.example/z", send(HttpConfig(url = server.url("/").toString(), linkTemplate = "\$response\$")).url)
    }

    @Test
    fun errorStatusCarriesTheServersWords() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad token"}"""))
        try {
            send(HttpConfig(url = server.url("/").toString()))
            fail("expected an error")
        } catch (e: HttpUploadException) {
            assertEquals(401, e.statusCode)
            assertTrue(e.message!!.contains("bad token"))
        }
    }

    @Test
    fun missingLinkIsReportedWithTheReply() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        try {
            send(HttpConfig(url = server.url("/").toString(), linkTemplate = "\$json:url\$"))
            fail("expected an error")
        } catch (e: HttpLinkException) {
            assertTrue(e.message!!.contains("{\"ok\":true}"))
        }
    }

    @Test
    fun deleteTreatsNotFoundAsDone() {
        server.enqueue(MockResponse().setResponseCode(404))
        uploader.delete(server.url("/gone").toString())
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            uploader.delete(server.url("/broken").toString())
            fail("expected an error")
        } catch (e: HttpUploadException) {
            assertEquals(500, e.statusCode)
        }
    }

    @Test
    fun configSurvivesJsonAndTextEditing() {
        val config = HttpConfig(
            method = "PUT", url = "https://h.example/up", query = listOf("a" to "1"), headers = listOf("X-Key" to "k: v"),
            bodyType = BodyType.BINARY, fileField = "f", fields = listOf("t" to "x=y"), linkTemplate = "\$response\$", deletionTemplate = "d",
        )
        assertEquals(config, HttpConfig.fromJson(config.toJson()))
        assertEquals(
            listOf("Authorization" to "Bearer a:b", "X" to ""),
            HttpConfig.parsePairs("Authorization: Bearer a:b\n\n X \n: nope", ':'),
        )
        assertEquals("k: v", HttpConfig.formatPairs(listOf("k" to "v"), ':'))
        assertEquals("k=v", HttpConfig.formatPairs(listOf("k" to "v"), '='))
    }

    @Test
    fun jsonPathHandlesArraysAndMisses() {
        val body = """{"files":[{"url":"u0"},{"url":"u1"}],"n":null}"""
        assertEquals("u1", ResponseTemplate.jsonPath(body, "files[1].url"))
        assertEquals("u0", ResponseTemplate.jsonPath(body, "\$.files[0].url"))
        assertEquals("", ResponseTemplate.jsonPath(body, "files[5].url"))
        assertEquals("", ResponseTemplate.jsonPath(body, "n"))
        assertEquals("", ResponseTemplate.jsonPath("not json", "a"))
    }
}
