package uk.co.aaronburt.sharedroid.s3

/**
 * Everything needed to upload to one S3-compatible bucket.
 *
 * [endpoint] is a host with no scheme, e.g. `s3.eu-west-2.amazonaws.com` or
 * `<account>.r2.cloudflarestorage.com`. A port may be included (`localhost:9000`).
 */
data class S3Config(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val pathStyle: Boolean = false,
    val useHttps: Boolean = true,
    val keyTemplate: String = KeyTemplate.DEFAULT,
    val acl: String? = null,
    val storageClass: String? = null,
    val cacheControl: String? = null,
    /** Base for returned links, e.g. a CDN or custom domain. Null builds the link from the endpoint. */
    val publicUrlBase: String? = null,
) {
    val scheme: String get() = if (useHttps) "https" else "http"

    /** Host the request is sent to: `bucket.endpoint` for virtual-hosted style, `endpoint` for path style. */
    val requestHost: String get() = if (pathStyle) endpoint else "$bucket.$endpoint"

    /** Path for an object key, with the bucket prefixed in path style. Not yet URI-encoded. */
    fun objectPath(key: String): String = if (pathStyle) "/$bucket/$key" else "/$key"

    fun objectUrl(key: String): String = "$scheme://$requestHost${SigV4Signer.encodePath(objectPath(key))}"

    /** The link handed back to the user after an upload. */
    fun publicUrl(key: String): String {
        val base = publicUrlBase?.trimEnd('/') ?: return objectUrl(key)
        return "$base${SigV4Signer.encodePath("/$key")}"
    }

    /** A link to a private object that stops working after [expiresSeconds] (at most 7 days). */
    fun presignedGetUrl(key: String, now: java.time.Instant, expiresSeconds: Long): String {
        val query = SigV4Signer.presignGetQuery(objectPath(key), requestHost, accessKeyId, secretAccessKey, region, now, expiresSeconds)
        return "${objectUrl(key)}?$query"
    }
}
