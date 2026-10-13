package uk.co.aaronburt.sharedroid.upload

import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.aaronburt.sharedroid.AppContainer
import uk.co.aaronburt.sharedroid.data.Destination
import uk.co.aaronburt.sharedroid.data.Upload
import uk.co.aaronburt.sharedroid.data.UploadStatus
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Something the user asked to upload. */
sealed interface UploadSource {
    data class Content(val uri: Uri) : UploadSource
    data class Text(val text: String) : UploadSource
}

/**
 * Takes a private copy of each file, then queues it for upload.
 *
 * Access to a shared `content://` URI ends when the receiving activity closes, which is before a
 * background upload runs. So every stream is opened here, straight away, and copied into app storage;
 * an open stream stays readable after the grant has gone. The copy is also where location data is removed.
 */
class UploadStager(private val container: AppContainer) {
    private val context get() = container.context
    private val pendingDir: File get() = File(context.noBackupFilesDir, "pending").apply { mkdirs() }

    /** Returns how many items were accepted. Call from the main thread while the URIs are still readable. */
    fun stage(sources: List<UploadSource>, destination: Destination): Int {
        val opened = sources.mapNotNull { open(it) }
        opened.forEach { item ->
            container.scope.launch(Dispatchers.IO) { copyAndQueue(item, destination) }
        }
        return opened.size
    }

    private class Opened(val name: String, val mime: String, val stream: InputStream)

    private fun open(source: UploadSource): Opened? = when (source) {
        is UploadSource.Text -> {
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            Opened("text-$stamp.txt", "text/plain; charset=utf-8", source.text.byteInputStream())
        }
        is UploadSource.Content -> runCatching {
            val resolver = context.contentResolver
            val stream = resolver.openInputStream(source.uri) ?: return null
            val mime = resolver.getType(source.uri) ?: "application/octet-stream"
            var name: String? = null
            runCatching {
                resolver.query(source.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
                }
            }
            Opened(withExtension(name ?: source.uri.lastPathSegment ?: "file", mime), mime, stream)
        }.getOrNull()
    }

    /** Gives a name an extension when the source app left it off, so key templates using `{ext}` work. */
    private fun withExtension(name: String, mime: String): String {
        val clean = name.substringAfterLast('/')
        if (clean.substringAfterLast('.', "").isNotEmpty()) return clean
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.substringBefore(';').trim())
        return if (ext != null) "$clean.$ext" else clean
    }

    private suspend fun copyAndQueue(item: Opened, destination: Destination) {
        val uploads = container.db.uploads()
        val id = uploads.insert(
            Upload(
                destinationId = destination.id,
                destinationName = destination.name,
                fileName = item.name,
                mimeType = item.mime,
                size = 0,
                localPath = null,
                objectKey = null,
                url = null,
                status = UploadStatus.PREPARING,
                progress = 0,
                error = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        val file = File(pendingDir, id.toString())
        try {
            // Location is removed if either the app-wide switch or this destination's switch is on.
            val strip = item.mime == "image/jpeg" && (container.settings.current().stripLocation || destination.stripLocation)
            withContext(Dispatchers.IO) {
                item.stream.use { raw ->
                    file.outputStream().use { out ->
                        if (!strip) {
                            raw.copyTo(out)
                        } else {
                            val input = BufferedInputStream(raw)
                            input.mark(4)
                            val isJpeg = input.read() == 0xFF && input.read() == 0xD8
                            input.reset()
                            if (isJpeg) JpegStripper.strip(input, out) else input.copyTo(out)
                        }
                    }
                }
            }
            val row = uploads.byId(id)
            if (row == null) {
                // Removed from history while it was still being copied.
                file.delete()
                return
            }
            uploads.update(row.copy(size = file.length(), localPath = file.absolutePath, status = UploadStatus.QUEUED))
            UploadScheduler.enqueue(context, id, file.length())
        } catch (e: Exception) {
            file.delete()
            uploads.setStatus(id, UploadStatus.FAILED, error = "Couldn't read the file")
        }
    }
}
