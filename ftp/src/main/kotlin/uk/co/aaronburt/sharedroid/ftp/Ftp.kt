package uk.co.aaronburt.sharedroid.ftp

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.json.JSONObject
import uk.co.aaronburt.sharedroid.http.RemotePaths
import uk.co.aaronburt.sharedroid.http.RemoteTestResult
import java.io.FilterInputStream
import java.io.InputStream

enum class FtpSecurity {
    /** Plain FTP. Everything, including the password, crosses the network unencrypted. */
    NONE,

    /** FTPS that starts plain and upgrades with AUTH TLS. Usually on port 21. */
    EXPLICIT,

    /** FTPS that is encrypted from the first byte. Usually on port 990. */
    IMPLICIT,
}

data class FtpConfig(
    val host: String = "",
    val port: Int = 21,
    val security: FtpSecurity = FtpSecurity.EXPLICIT,
    val username: String = "",
    val password: String = "",
    val passive: Boolean = true,
    val pathTemplate: String = DEFAULT_PATH,
    /** Where the uploaded files can be opened from, such as `https://files.example.com`. */
    val publicUrlBase: String = "",
) {
    fun linkFor(path: String): String = RemotePaths.joinUrl(publicUrlBase, path)

    fun toJson(): String = JSONObject().apply {
        put("host", host)
        put("port", port)
        put("security", security.name)
        put("username", username)
        put("password", password)
        put("passive", passive)
        put("path", pathTemplate)
        put("publicUrlBase", publicUrlBase)
    }.toString()

    companion object {
        const val DEFAULT_PATH = "ShareDroid/{yyyy}/{MM}/{random:8}.{ext}"

        fun defaultPort(security: FtpSecurity) = if (security == FtpSecurity.IMPLICIT) 990 else 21

        fun fromJson(text: String): FtpConfig {
            val o = JSONObject(text)
            val security = runCatching { FtpSecurity.valueOf(o.optString("security")) }.getOrDefault(FtpSecurity.EXPLICIT)
            return FtpConfig(
                host = o.optString("host"),
                port = o.optInt("port", defaultPort(security)),
                security = security,
                username = o.optString("username"),
                password = o.optString("password"),
                passive = o.optBoolean("passive", true),
                pathTemplate = o.optString("path", DEFAULT_PATH),
                publicUrlBase = o.optString("publicUrlBase"),
            )
        }
    }
}

/** The server refused something. [replyCode] is the FTP reply, for example 530 for a failed login. */
class FtpException(val replyCode: Int, message: String) : Exception(message)

/** Uploads over FTP, FTPS explicit or FTPS implicit. A fresh connection is used for each operation. */
class FtpUploader {

    fun upload(
        config: FtpConfig,
        path: String,
        contentLength: Long,
        open: () -> InputStream,
        onProgress: (bytesSent: Long) -> Unit = {},
    ) = session(config) { client ->
        val dirs = RemotePaths.parentDirs(path)
        for (dir in dirs) {
            if (!client.changeWorkingDirectory(dir)) {
                if (!client.makeDirectory(dir) || !client.changeWorkingDirectory(dir)) throw refused(client, "Couldn't create the folder $dir")
            }
        }
        val name = path.substringAfterLast('/')
        val stream = open().let { source ->
            object : FilterInputStream(source) {
                private var sent = 0L
                override fun read(): Int = super.read().also { if (it >= 0) onProgress(++sent) }
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { if (it > 0) { sent += it; onProgress(sent) } }
            }
        }
        stream.use { if (!client.storeFile(name, it)) throw refused(client, "The server refused the file") }
    }

    fun delete(config: FtpConfig, path: String) = session(config) { client ->
        for (dir in RemotePaths.parentDirs(path)) {
            // A missing folder means there is nothing to delete.
            if (!client.changeWorkingDirectory(dir)) return@session
        }
        if (!client.deleteFile(path.substringAfterLast('/'))) throw refused(client, "The server refused to delete the file")
    }

    /** Uploads and deletes a tiny file. A refused delete is reported rather than thrown. */
    fun test(config: FtpConfig): RemoteTestResult {
        val name = ".sharedroid-test-${System.currentTimeMillis()}.txt"
        val bytes = "ok".toByteArray()
        upload(config, name, bytes.size.toLong(), { bytes.inputStream() })
        return try {
            delete(config, name)
            RemoteTestResult(canDelete = true)
        } catch (e: FtpException) {
            RemoteTestResult(canDelete = false)
        }
    }

    private fun refused(client: FTPClient, fallback: String): FtpException {
        val reply = client.replyString.orEmpty().trim()
        return FtpException(client.replyCode, reply.ifEmpty { fallback })
    }

    private fun <T> session(config: FtpConfig, block: (FTPClient) -> T): T {
        val client: FTPClient = when (config.security) {
            FtpSecurity.NONE -> FTPClient()
            FtpSecurity.EXPLICIT -> FTPSClient(false)
            FtpSecurity.IMPLICIT -> FTPSClient(true)
        }
        client.connectTimeout = 15_000
        client.defaultTimeout = 30_000
        client.bufferSize = 64 * 1024
        try {
            client.connect(config.host, config.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) throw refused(client, "The server refused the connection")
            if (!client.login(config.username, config.password)) throw refused(client, "Login failed")
            if (client is FTPSClient) {
                // Encrypt the data connection too, not just the login.
                client.execPBSZ(0)
                client.execPROT("P")
            }
            if (config.passive) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            return block(client)
        } finally {
            runCatching { if (client.isConnected) client.logout() }
            runCatching { client.disconnect() }
        }
    }
}
