package uk.co.aaronburt.sharedroid.ftp

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem

/** Runs against an in-process FTP server. Plain FTP only: FTPS is not exercised here. */
class FtpUploaderTest {
    private lateinit var server: FakeFtpServer
    private lateinit var files: UnixFakeFileSystem
    private val uploader = FtpUploader()

    @Before
    fun start() {
        files = UnixFakeFileSystem().apply { add(DirectoryEntry("/")) }
        server = FakeFtpServer().apply {
            serverControlPort = 0
            addUserAccount(UserAccount("aaron", "secret", "/"))
            fileSystem = files
            start()
        }
    }

    @After fun stop() { server.stop() }

    private fun config(password: String = "secret") =
        FtpConfig(host = "127.0.0.1", port = server.serverControlPort, security = FtpSecurity.NONE, username = "aaron", password = password)

    private fun content(path: String) = (files.getEntry(path) as FileEntry).createInputStream().readBytes().decodeToString()

    @Test
    fun uploadsIntoFoldersItCreates() {
        val bytes = "hello ftp".toByteArray()
        var last = 0L
        uploader.upload(config(), "shots/2026/pic.png", bytes.size.toLong(), { bytes.inputStream() }) { last = it }
        assertEquals("hello ftp", content("/shots/2026/pic.png"))
        assertEquals(bytes.size.toLong(), last)
    }

    @Test
    fun usesExistingFolders() {
        files.add(DirectoryEntry("/inbox"))
        val bytes = "x".toByteArray()
        uploader.upload(config(), "inbox/a.txt", 1, { bytes.inputStream() })
        assertEquals("x", content("/inbox/a.txt"))
    }

    @Test
    fun wrongPasswordIsReportedAs530() {
        try {
            uploader.upload(config("nope"), "a.txt", 1, { "x".toByteArray().inputStream() })
            fail("expected an error")
        } catch (e: FtpException) {
            assertEquals(530, e.replyCode)
        }
    }

    @Test
    fun deleteRemovesTheFileAndMissingFoldersAreFine() {
        uploader.upload(config(), "d/a.txt", 1, { "x".toByteArray().inputStream() })
        assertTrue(files.exists("/d/a.txt"))
        uploader.delete(config(), "d/a.txt")
        assertFalse(files.exists("/d/a.txt"))
        uploader.delete(config(), "never/was/here.txt")
    }

    @Test
    fun testConnectionUploadsAndCleansUp() {
        assertTrue(uploader.test(config()).canDelete)
        assertEquals(emptyList<String>(), files.listNames("/").toList())
    }

    @Test
    fun configRoundTripsAndBuildsLinks() {
        val config = FtpConfig(host = "ftp.example", security = FtpSecurity.IMPLICIT, port = 990, username = "u", password = "p", publicUrlBase = "https://f.example/")
        assertEquals(config, FtpConfig.fromJson(config.toJson()))
        assertEquals("https://f.example/a%20b/c.png", config.linkFor("a b/c.png"))
        assertEquals(990, FtpConfig.defaultPort(FtpSecurity.IMPLICIT))
        assertEquals(21, FtpConfig.defaultPort(FtpSecurity.EXPLICIT))
    }
}
