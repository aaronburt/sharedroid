package uk.co.aaronburt.sharedroid.upload

import uk.co.aaronburt.sharedroid.ftp.FtpException
import uk.co.aaronburt.sharedroid.http.HttpLinkException
import uk.co.aaronburt.sharedroid.http.HttpUploadException
import uk.co.aaronburt.sharedroid.s3.S3Exception
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Turns S3 and network failures into words a person can act on. */
object ErrorText {
    /** A few words, for a history row. */
    fun short(e: Throwable): String = when (e) {
        is S3Exception -> when (e.code) {
            "AccessDenied", "AllAccessDisabled" -> "Access denied"
            "SignatureDoesNotMatch" -> "Secret key rejected"
            "InvalidAccessKeyId" -> "Access key not recognised"
            "NoSuchBucket" -> "Bucket not found"
            "AuthorizationHeaderMalformed", "PermanentRedirect", "IllegalLocationConstraintException" -> "Wrong region"
            "RequestTimeTooSkewed" -> "Phone clock is wrong"
            "EntityTooLarge" -> "File too large"
            "AccessControlListNotSupported" -> "Bucket blocks ACLs"
            else -> if (e.statusCode == 403) "Access denied" else "Server said ${e.code ?: e.statusCode}"
        }
        is HttpUploadException -> when (e.statusCode) {
            401, 403 -> "Access denied"
            404 -> "Address not found"
            413 -> "File too large"
            429 -> "Rate limited"
            507 -> "Server is full"
            else -> "Server said ${e.statusCode}"
        }
        is HttpLinkException -> "No link in the reply"
        is FtpException -> when (e.replyCode) {
            530 -> "Login failed"
            550 -> "Permission denied"
            421 -> "Server busy"
            else -> "FTP error ${e.replyCode}"
        }
        is IllegalArgumentException -> "Check the destination's URL"
        is UnknownHostException -> "Endpoint not found"
        is ConnectException, is SocketTimeoutException -> "Couldn't connect"
        is java.net.UnknownServiceException -> "Plain http:// is blocked"
        is SSLException -> "Secure connection failed"
        is java.io.IOException -> "Network problem"
        else -> "Upload failed"
    }

    /** A sentence, for the destination form and notifications. */
    fun long(e: Throwable): String = when (e) {
        is S3Exception -> when (e.code) {
            "AccessDenied", "AllAccessDisabled" -> "Access denied. Check the keys and that they're allowed to write to this bucket."
            "SignatureDoesNotMatch" -> "The secret access key was rejected. Check it was pasted in full."
            "InvalidAccessKeyId" -> "The access key ID isn't recognised by this endpoint."
            "NoSuchBucket" -> "That bucket doesn't exist at this endpoint."
            "AuthorizationHeaderMalformed", "PermanentRedirect", "IllegalLocationConstraintException" ->
                "The region doesn't match the bucket. Check the region and endpoint."
            "RequestTimeTooSkewed" -> "The phone's clock is too far out. Set the time automatically and try again."
            "EntityTooLarge" -> "The file is too large to upload in one request."
            "AccessControlListNotSupported" -> "This bucket blocks ACLs. Turn off \"Make files public (ACL)\"."
            else -> e.message ?: "The server returned ${e.statusCode}."
        }
        is HttpUploadException -> "The server said ${e.statusCode}: ${e.message}"
        is HttpLinkException -> e.message ?: "The reply had no link."
        is FtpException -> when (e.replyCode) {
            530 -> "The server rejected the username or password."
            550 -> "The server refused it. Check the folder exists and this account may write there."
            else -> "The server said ${e.replyCode}: ${e.message}"
        }
        is IllegalArgumentException -> e.message ?: "Check the destination's settings."
        is UnknownHostException -> "Couldn't find the endpoint. Check the address and your connection."
        is ConnectException, is SocketTimeoutException -> "Couldn't connect to the endpoint."
        is java.net.UnknownServiceException -> "This build only allows secure https:// addresses. Use https://, or a debug build for plain http://."
        is SSLException -> "The secure connection to the endpoint failed."
        is java.io.IOException -> "Network problem: ${e.message ?: "the connection dropped"}."
        else -> e.message ?: "Something went wrong."
    }
}
