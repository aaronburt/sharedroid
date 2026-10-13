package uk.co.aaronburt.sharedroid.http

import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WebDavTest {
    private lateinit var server: MockWebServer
    private val uploader = WebDavUploader()
    private val bytes = "hello".toByteArray()

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() { server.shutdown() }

    private fun config(base: String = "/dav/", public: String = "") =
        WebDavConfig(url = server.url(base).toString(), username = "aaron", password = "app pass", publicUrlBase = public)

    private fun send(config: WebDavConfig, path: String) =
        uploader.upload(config, path, "image/png", bytes.size.toLong(), { bytes.inputStream() })

    @Test
    fun putsTheFileWithBasicAuthAtAnEncodedPath() {
        server.enqueue(MockResponse().setResponseCode(201))
        send(config(), "Shots/pic one.png")
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/dav/Shots/pic%20one.png", request.path)
        assertEquals(Credentials.basic("aaron", "app pass"), request.getHeader("Authorization"))
        assertEquals("hello", request.body.readUtf8())
        assertEquals("image/png", request.getHeader("Content-Type"))
    }

    @Test
    fun createsMissingFoldersWhenTheServerSaysConflict() {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(201)) // MKCOL a
        server.enqueue(MockResponse().setResponseCode(405)) // MKCOL a/b already exists
        server.enqueue(MockResponse().setResponseCode(201)) // PUT again
        send(config(), "a/b/c.png")
        assertEquals(
            listOf("PUT /dav/a/b/c.png", "MKCOL /dav/a", "MKCOL /dav/a/b", "PUT /dav/a/b/c.png"),
            List(4) { server.takeRequest().let { "${it.method} ${it.path}" } },
        )
    }

    @Test
    fun badCredentialsAreReportedWithTheStatus() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("<d:error><s:message>Bad login</s:message></d:error>"))
        try {
            send(config(), "x.png")
            fail("expected an error")
        } catch (e: HttpUploadException) {
            assertEquals(401, e.statusCode)
            assertTrue(e.message!!, e.message!!.contains("Bad login"))
        }
    }

    @Test
    fun deleteAndTest() {
        server.enqueue(MockResponse().setResponseCode(404))
        uploader.delete(config(), "gone.png")
        assertEquals("DELETE", server.takeRequest().method)

        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(uploader.test(config()).canDelete)

        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(403))
        assertFalse(uploader.test(config()).canDelete)
    }

    @Test
    fun linksUsePublicBaseOrTheWebDavAddress() {
        val withBase = WebDavConfig(url = "https://cloud.example/dav/files/me/", publicUrlBase = "https://i.example.com/")
        assertEquals("https://i.example.com/a%20b/c.png", withBase.linkFor("a b/c.png"))
        assertEquals("https://cloud.example/dav/files/me/a%20b/c.png", withBase.copy(publicUrlBase = "").linkFor("a b/c.png"))
        assertEquals(withBase, WebDavConfig.fromJson(withBase.toJson()))
    }

    @Test
    fun pathsCannotClimbOutOfTheFolder() {
        assertEquals("a/_/b.png", RemotePaths.clean("a/../b.png"))
        assertEquals("_/etc/passwd", RemotePaths.clean("../etc/./passwd"))
        assertEquals("file", RemotePaths.clean("///"))
        assertTrue(RemotePaths.render("{yyyy}/{MM}/{filename}", "pic.png", "image/png").matches(Regex("""\d{4}/\d{2}/pic\.png""")))
        val template = "x/{random:8}.{ext}"
        assertEquals(RemotePaths.preview(template, "a.png", "image/png"), RemotePaths.preview(template, "a.png", "image/png"))
    }
}
