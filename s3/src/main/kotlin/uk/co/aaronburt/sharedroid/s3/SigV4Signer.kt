package uk.co.aaronburt.sharedroid.s3

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 for S3, header-based signing.
 *
 * Kept free of Android and HTTP-client types so it can be tested against AWS's
 * published examples on a plain JVM.
 */
object SigV4Signer {
    const val ALGORITHM = "AWS4-HMAC-SHA256"
    const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
    val EMPTY_SHA256: String = sha256Hex(ByteArray(0))

    private val AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    data class Request(
        val method: String,
        /** Raw path, e.g. `/photos/a b.png`. Encoded by the signer. */
        val path: String,
        val query: Map<String, String> = emptyMap(),
        /** Must include `host`. Names are case-insensitive. */
        val headers: Map<String, String>,
        /** Hex SHA-256 of the body, [UNSIGNED_PAYLOAD], or [EMPTY_SHA256]. */
        val payloadHash: String,
    )

    /**
     * Returns the headers to add to the request: `x-amz-date`, `x-amz-content-sha256` and `Authorization`.
     */
    fun sign(
        request: Request,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        now: Instant,
        service: String = "s3",
    ): Map<String, String> {
        val amzDate = AMZ_DATE.format(now)
        val dateStamp = DATE_STAMP.format(now)

        val headers = request.headers.mapKeys { it.key.lowercase() } +
            mapOf("x-amz-date" to amzDate, "x-amz-content-sha256" to request.payloadHash)
        val (canonicalRequest, signedHeaders) = canonicalRequest(request.copy(headers = headers))

        val scope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = listOf(ALGORITHM, amzDate, scope, sha256Hex(canonicalRequest.toByteArray())).joinToString("\n")

        val signingKey = listOf(dateStamp, region, service, "aws4_request")
            .fold("AWS4$secretAccessKey".toByteArray()) { key, part -> hmac(key, part) }
        val signature = hmac(signingKey, stringToSign).toHex()

        return mapOf(
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to request.payloadHash,
            "Authorization" to "$ALGORITHM Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature",
        )
    }

    /**
     * Query-string signing for a presigned GET. Returns the full query string, signature included.
     * S3 accepts at most 7 days for [expiresSeconds].
     */
    fun presignGetQuery(
        path: String,
        host: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        now: Instant,
        expiresSeconds: Long,
        service: String = "s3",
    ): String {
        val amzDate = AMZ_DATE.format(now)
        val dateStamp = DATE_STAMP.format(now)
        val scope = "$dateStamp/$region/$service/aws4_request"
        val query = mapOf(
            "X-Amz-Algorithm" to ALGORITHM,
            "X-Amz-Credential" to "$accessKeyId/$scope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to expiresSeconds.coerceIn(1, 604800).toString(),
            "X-Amz-SignedHeaders" to "host",
        )
        val (canonicalRequest, _) = canonicalRequest(Request("GET", path, query, mapOf("host" to host), UNSIGNED_PAYLOAD))
        val stringToSign = listOf(ALGORITHM, amzDate, scope, sha256Hex(canonicalRequest.toByteArray())).joinToString("\n")
        val signingKey = listOf(dateStamp, region, service, "aws4_request")
            .fold("AWS4$secretAccessKey".toByteArray()) { key, part -> hmac(key, part) }
        val canonicalQuery = query.map { (k, v) -> encode(k) to encode(v) }.sortedBy { it.first }
            .joinToString("&") { "${it.first}=${it.second}" }
        return "$canonicalQuery&X-Amz-Signature=${hmac(signingKey, stringToSign).toHex()}"
    }

    internal fun canonicalRequest(request: Request): Pair<String, String> {
        val headers = request.headers
            .map { (k, v) -> k.lowercase() to v.trim().replace(Regex("\\s+"), " ") }
            .sortedBy { it.first }
        val signedHeaders = headers.joinToString(";") { it.first }
        val canonicalQuery = request.query
            .map { (k, v) -> encode(k) to encode(v) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }
        val canonical = listOf(
            request.method.uppercase(),
            encodePath(request.path),
            canonicalQuery,
            headers.joinToString("") { "${it.first}:${it.second}\n" },
            signedHeaders,
            request.payloadHash,
        ).joinToString("\n")
        return canonical to signedHeaders
    }

    /** URI-encodes each path segment once, as S3 expects. */
    fun encodePath(path: String): String = path.split('/').joinToString("/") { encode(it) }

    /** RFC 3986 encoding: only unreserved characters are left as-is. */
    fun encode(value: String): String = buildString {
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt().toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
                append(c)
            } else {
                append('%').append("%02X".format(b.toInt() and 0xff))
            }
        }
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data.toByteArray(Charsets.UTF_8))
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
