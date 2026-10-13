package uk.co.aaronburt.sharedroid.upload

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class JpegStripperTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return bytes(0xFF, marker, length shr 8, length and 0xFF) + payload
    }

    /** Little-endian Exif with an orientation tag followed by text standing in for GPS and camera data. */
    private fun exif(orientation: Int): ByteArray =
        "Exif".toByteArray() + bytes(0, 0) +
            bytes('I'.code, 'I'.code, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00) +
            bytes(0x01, 0x00) +
            bytes(0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, orientation, 0x00, 0x00, 0x00) +
            bytes(0, 0, 0, 0) +
            "GPS 51.5074N 0.1278W Pixel 9".toByteArray()

    private val soi = bytes(0xFF, 0xD8)
    private val jfif = segment(0xE0, "JFIF".toByteArray() + bytes(0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
    private val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/".toByteArray() + bytes(0) + "<x:xmpmeta>home address</x:xmpmeta>".toByteArray())
    private val comment = segment(0xFE, "shot on my phone".toByteArray())
    private val quant = segment(0xDB, ByteArray(65) { 1 })
    // Scan data deliberately contains bytes that look like an Exif marker; it must pass through untouched.
    private val scan = bytes(0xFF, 0xDA, 0x00, 0x02) + bytes(1, 2, 3, 0xFF, 0x00, 0xFF, 0xE1, 4, 5) + bytes(0xFF, 0xD9)

    private fun strip(input: ByteArray): ByteArray? {
        val out = ByteArrayOutputStream()
        return if (JpegStripper.strip(input.inputStream(), out)) out.toByteArray() else null
    }

    private fun ByteArray.contains(text: String) = String(this, Charsets.ISO_8859_1).contains(text)

    @Test
    fun removesMetadataAndKeepsThePicture() {
        val out = strip(soi + jfif + segment(0xE1, exif(1)) + xmp + comment + quant + scan)!!
        assertFalse(out.contains("GPS"))
        assertFalse(out.contains("home address"))
        assertFalse(out.contains("shot on my phone"))
        assertArrayEquals(soi + jfif + quant + scan, out)
    }

    @Test
    fun keepsOrientationSoPortraitPhotosStayUpright() {
        val out = strip(soi + jfif + segment(0xE1, exif(6)) + quant + scan)!!
        assertFalse(out.contains("GPS"))
        val kept = out.copyOfRange(soi.size + jfif.size, out.size - quant.size - scan.size)
        assertEquals(36, kept.size)
        assertEquals(6, JpegStripper.orientation(kept.copyOfRange(4, kept.size)))
    }

    @Test
    fun leavesNonJpegAlone() {
        val out = ByteArrayOutputStream()
        assertFalse(JpegStripper.strip("PNG data".toByteArray().inputStream(), out))
        assertEquals(0, out.size())
    }

    @Test
    fun survivesTruncatedFile() {
        val whole = soi + jfif + segment(0xE1, exif(6))
        assertTrue(strip(whole.copyOfRange(0, whole.size - 10)) != null)
    }
}
