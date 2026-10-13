package uk.co.aaronburt.sharedroid.upload

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import uk.co.aaronburt.sharedroid.container
import uk.co.aaronburt.sharedroid.data.Providers
import uk.co.aaronburt.sharedroid.data.Upload
import uk.co.aaronburt.sharedroid.data.UploadStatus
import uk.co.aaronburt.sharedroid.ftp.FtpConfig
import uk.co.aaronburt.sharedroid.ftp.FtpException
import uk.co.aaronburt.sharedroid.http.HttpUploadException
import uk.co.aaronburt.sharedroid.http.WebDavConfig
import uk.co.aaronburt.sharedroid.s3.S3Exception
import java.io.File

/** Things that can be done to an upload from the history list or a notification. */
object UploadActions {
    fun retry(context: Context, uploadId: Long) {
        val container = context.container
        container.scope.launch {
            container.db.uploads().setStatus(uploadId, UploadStatus.QUEUED)
            Notifications.cancel(context, uploadId)
            UploadScheduler.enqueue(context, uploadId)
        }
    }

    /** Stops the upload if it is running and forgets it. The file at the destination, if any, is left alone. */
    fun remove(context: Context, uploadId: Long) {
        val container = context.container
        container.scope.launch {
            UploadScheduler.cancel(context, uploadId)
            val uploads = container.db.uploads()
            uploads.byId(uploadId)?.localPath?.let { File(it).delete() }
            uploads.delete(uploadId)
        }
    }

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        data object NotAllowed : DeleteResult
        data class Failed(val reason: String) : DeleteResult
    }

    /** Deletes the file from where it was uploaded and, if that worked, the history entry. */
    suspend fun deleteFromBucket(context: Context, upload: Upload): DeleteResult {
        val container = context.container
        val destination = container.db.destinations().byId(upload.destinationId)
            ?: return DeleteResult.Failed("That destination was removed")
        return try {
            when (destination.provider) {
                Providers.HTTP -> {
                    val link = upload.deleteUrl ?: return DeleteResult.Failed("Nothing to delete")
                    runInterruptible(Dispatchers.IO) { container.http.delete(link) }
                }
                Providers.WEBDAV -> {
                    val path = upload.objectKey ?: return DeleteResult.Failed("Nothing to delete")
                    val config = WebDavConfig.fromJson(container.secrets.decrypt(destination.httpJson.orEmpty()))
                    runInterruptible(Dispatchers.IO) { container.webdav.delete(config, path) }
                }
                Providers.FTP -> {
                    val path = upload.objectKey ?: return DeleteResult.Failed("Nothing to delete")
                    val config = FtpConfig.fromJson(container.secrets.decrypt(destination.httpJson.orEmpty()))
                    runInterruptible(Dispatchers.IO) { container.ftp.delete(config, path) }
                }
                else -> {
                    val key = upload.objectKey ?: return DeleteResult.Failed("Nothing to delete")
                    runInterruptible(Dispatchers.IO) { container.s3.deleteObject(container.s3Config(destination), key) }
                }
            }
            container.db.uploads().delete(upload.id)
            Notifications.cancel(context, upload.id)
            DeleteResult.Deleted
        } catch (e: S3Exception) {
            if (e.statusCode == 403) {
                container.db.destinations().setCanDelete(destination.id, false)
                DeleteResult.NotAllowed
            } else {
                DeleteResult.Failed(ErrorText.short(e))
            }
        } catch (e: HttpUploadException) {
            if (e.statusCode == 401 || e.statusCode == 403) DeleteResult.NotAllowed else DeleteResult.Failed(ErrorText.short(e))
        } catch (e: FtpException) {
            if (e.replyCode == 550) DeleteResult.NotAllowed else DeleteResult.Failed(ErrorText.short(e))
        } catch (e: Exception) {
            DeleteResult.Failed(ErrorText.short(e))
        }
    }
}
