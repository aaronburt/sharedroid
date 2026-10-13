package uk.co.aaronburt.sharedroid.upload

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Removes location and camera metadata from a JPEG without re-encoding the picture.
 *
 * Drops the Exif and XMP segments (APP1), Photoshop/IPTC (APP13) and comments. Exif also
 * carries the orientation, and losing that would turn portrait photos sideways, so a
 * minimal Exif segment holding only the orientation is written back.
 */
object JpegStripper {
    private const val SOI = 0xD8
    private const val SOS = 0xDA
    private const val APP1 = 0xE1
    private const val APP13 = 0xED
    private const val COM = 0xFE
    private val EXIF_HEADER = byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0)

    /** Copies [input] to [output] without metadata. Returns false, writing nothing, if it isn't a JPEG. */
    fun strip(input: InputStream, output: OutputStream): Boolean {
        val data = DataInputStream(input)
        val b0 = data.read()
        val b1 = data.read()
        if (b0 != 0xFF || b1 != SOI) return false
        output.write(0xFF)
        output.write(SOI)

        try {
            while (true) {
                var marker = data.readUnsignedByte()
                if (marker != 0xFF) {
                    // Not a marker where one must be: stop rewriting and copy the rest untouched.
                    output.write(marker)
                    break
                }
                while (marker == 0xFF) marker = data.readUnsignedByte()

                if (marker == SOS) {
                    // Compressed image data follows. Nothing after this point is metadata we remove.
                    output.write(0xFF)
                    output.write(marker)
                    break
                }
                if (marker in 0xD0..0xD9 || marker == 0x01) {
                    output.write(0xFF)
                    output.write(marker)
                    continue
                }

                val length = data.readUnsignedShort()
                val payload = ByteArray((length - 2).coerceAtLeast(0))
                data.readFully(payload)

                when (marker) {
                    APP1 -> orientation(payload)?.let { output.write(orientationSegment(it)) }
                    APP13, COM -> Unit
                    else -> {
                        output.write(0xFF)
                        output.write(marker)
                        output.write(length shr 8)
                        output.write(length and 0xFF)
                        output.write(payload)
                    }
                }
            }
        } catch (_: EOFException) {
            return true
        }
        data.copyTo(output)
        return true
    }

    /** Reads the Exif orientation (1 to 8) from an APP1 payload. Null if absent, or already upright. */
    internal fun orientation(app1: ByteArray): Int? {
        if (app1.size < 14 || !app1.copyOfRange(0, 6).contentEquals(EXIF_HEADER)) return null
        val tiff = 6
        val little = when {
            app1[tiff] == 'I'.code.toByte() && app1[tiff + 1] == 'I'.code.toByte() -> true
            app1[tiff] == 'M'.code.toByte() && app1[tiff + 1] == 'M'.code.toByte() -> false
            else -> return null
        }
        fun u16(at: Int): Int? {
            if (at < 0 || at + 2 > app1.size) return null
            val a = app1[at].toInt() and 0xFF
            val b = app1[at + 1].toInt() and 0xFF
            return if (little) (b shl 8) or a else (a shl 8) or b
        }
        fun u32(at: Int): Int? {
            val lo = u16(if (little) at else at + 2) ?: return null
            val hi = u16(if (little) at + 2 else at) ?: return null
            return (hi shl 16) or lo
        }

        val ifd = tiff + (u32(tiff + 4) ?: return null)
        val count = u16(ifd) ?: return null
        for (i in 0 until count) {
            val entry = ifd + 2 + i * 12
            if (u16(entry) == 0x0112) return u16(entry + 8)?.takeIf { it in 2..8 }
        }
        return null
    }

    private fun orientationSegment(orientation: Int): ByteArray = byteArrayOf(
        0xFF.toByte(), APP1.toByte(), 0x00, 0x22,
        *EXIF_HEADER,
        'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
        0x00, 0x01,
        0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
    )
}
