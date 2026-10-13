package uk.co.aaronburt.sharedroid.s3

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Examples from the AWS S3 docs, "Signature Calculations for the Authorization Header". */
class SigV4SignerTest {
    private val accessKey = "AKIAIOSFODNN7EXAMPLE"
    private val secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    private val now = Instant.parse("2013-05-24T00:00:00Z")
    private val host = "examplebucket.s3.amazonaws.com"

    private fun signature(request: SigV4Signer.Request): String =
        SigV4Signer.sign(request, accessKey, secretKey, "us-east-1", now)
            .getValue("Authorization").substringAfter("Signature=")

    @Test
    fun getObject() {
        val request = SigV4Signer.Request(
            method = "GET",
            path = "/test.txt",
            headers = mapOf("Host" to host, "Range" to "bytes=0-9"),
            payloadHash = SigV4Signer.EMPTY_SHA256,
        )
        assertEquals("f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41", signature(request))
    }

    @Test
    fun putObject() {
        val request = SigV4Signer.Request(
            method = "PUT",
            path = "/test\$file.text",
            headers = mapOf(
                "Host" to host,
                "Date" to "Fri, 24 May 2013 00:00:00 GMT",
                "x-amz-storage-class" to "REDUCED_REDUNDANCY",
            ),
            payloadHash = SigV4Signer.sha256Hex("Welcome to Amazon S3.".toByteArray()),
        )
        assertEquals("98ad721746da40c64f1a55b78f14c238d841ea1380cd77a1b5971af0ece108bd", signature(request))
    }

    @Test
    fun getBucketLifecycle() {
        val request = SigV4Signer.Request(
            method = "GET",
            path = "/",
            query = mapOf("lifecycle" to ""),
            headers = mapOf("Host" to host),
            payloadHash = SigV4Signer.EMPTY_SHA256,
        )
        assertEquals("fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543", signature(request))
    }

    @Test
    fun listObjects() {
        val request = SigV4Signer.Request(
            method = "GET",
            path = "/",
            query = mapOf("max-keys" to "2", "prefix" to "J"),
            headers = mapOf("Host" to host),
            payloadHash = SigV4Signer.EMPTY_SHA256,
        )
        assertEquals("34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7", signature(request))
    }

    @Test
    fun encodePathKeepsSlashesAndEncodesTheRest() {
        assertEquals("/photos/my%20cat%2B%C3%A9.png", SigV4Signer.encodePath("/photos/my cat+é.png"))
    }

    /** From "Authenticating Requests: Using Query Parameters". */
    @Test
    fun presignedGet() {
        val query = SigV4Signer.presignGetQuery("/test.txt", host, accessKey, secretKey, "us-east-1", now, 86400)
        assertEquals(
            "X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request" +
                "&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host" +
                "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404",
            query,
        )
    }
}
