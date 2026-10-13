package uk.co.aaronburt.sharedroid.http

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Checks that each preset builds the request its service documents and reads the link from a reply shaped like the
 * service's. This does not contact the real services.
 */
class HttpPresetsTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() { server.shutdown() }

    private fun run(preset: HttpPreset, reply: String): Pair<HttpUploader.Result, String> {
        server.enqueue(MockResponse().setBody(reply))
        val bytes = "pixels".toByteArray()
        val config = preset.config.copy(url = server.url("/").toString())
        val result = HttpUploader().upload(config, "photo.png", "image/png", bytes.size.toLong(), { bytes.inputStream() })
        return result to server.takeRequest().body.readUtf8()
    }

    @Test
    fun imgurSendsClientIdAndImageFieldAndReadsTheLink() {
        server.enqueue(MockResponse().setBody("""{"data":{"link":"https://i.imgur.com/abc.png","deletehash":"h"},"success":true,"status":200}"""))
        val bytes = "pixels".toByteArray()
        val config = HttpPresets.imgur.config.copy(
            url = server.url("/3/image").toString(),
            headers = listOf("Authorization" to "Client-ID abc123"),
        )
        val result = HttpUploader().upload(config, "photo.png", "image/png", bytes.size.toLong(), { bytes.inputStream() })
        assertEquals("https://i.imgur.com/abc.png", result.url)
        val request = server.takeRequest()
        assertEquals("Client-ID abc123", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("name=\"image\"; filename=\"photo.png\""))
    }

    @Test
    fun catboxAndLitterboxSendTheirFieldsAndTakeThePlainTextLink() {
        for ((preset, expectedFields) in listOf(
            HttpPresets.catbox to listOf("reqtype"),
            HttpPresets.litterbox to listOf("reqtype", "time"),
        )) {
            val (result, body) = run(preset, "https://files.catbox.moe/xyz.png\n")
            assertEquals("https://files.catbox.moe/xyz.png", result.url)
            assertTrue(body.contains("name=\"fileToUpload\"; filename=\"photo.png\""))
            expectedFields.forEach { assertTrue("$it in ${preset.id}", body.contains("name=\"$it\"")) }
        }
    }

    @Test
    fun onlyImgurNeedsSomethingFilledIn() {
        assertTrue(HttpPresets.hasPlaceholder(HttpPresets.imgur.config))
        assertFalse(HttpPresets.hasPlaceholder(HttpPresets.imgur.config.copy(headers = listOf("Authorization" to "Client-ID real"))))
        assertFalse(HttpPresets.hasPlaceholder(HttpPresets.catbox.config))
        assertFalse(HttpPresets.hasPlaceholder(HttpPresets.litterbox.config))
    }

    @Test
    fun presetIdsAreUnique() {
        assertEquals(HttpPresets.all.size, HttpPresets.all.map { it.id }.toSet().size)
    }
}
