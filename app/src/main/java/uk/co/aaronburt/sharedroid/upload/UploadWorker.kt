package uk.co.aaronburt.sharedroid.upload

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import uk.co.aaronburt.sharedroid.container
import uk.co.aaronburt.sharedroid.data.LinkType
import uk.co.aaronburt.sharedroid.data.Providers
import uk.co.aaronburt.sharedroid.ftp.FtpConfig
import uk.co.aaronburt.sharedroid.http.HttpConfig
import uk.co.aaronburt.sharedroid.http.RemotePaths
import uk.co.aaronburt.sharedroid.http.WebDavConfig
import uk.co.aaronburt.sharedroid.data.UploadStatus
import uk.co.aaronburt.sharedroid.s3.KeyTemplate
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Starts and stops background uploads.
 *
 * Android 14 and later have user-initiated data transfer jobs, which are meant for exactly this:
 * they aren't cut off after a few minutes, so large videos finish. They can only be started while
 * the app is visible, so anything else, and every older version, goes through WorkManager.
 */
object UploadScheduler {
    private fun name(uploadId: Long) = "upload-$uploadId"

    fun enqueue(context: Context, uploadId: Long, size: Long = 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && scheduleTransferJob(context, uploadId, size)) return

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(KEY_UPLOAD_ID to uploadId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(uploadId), ExistingWorkPolicy.REPLACE, request)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun scheduleTransferJob(context: Context, uploadId: Long, size: Long): Boolean = runCatching {
        val job = JobInfo.Builder(jobId(uploadId), ComponentName(context, UploadJobService::class.java))
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(JobInfo.NETWORK_BYTES_UNKNOWN.toLong(), size.coerceAtLeast(1))
            .setExtras(PersistableBundle().apply { putLong(KEY_UPLOAD_ID, uploadId) })
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job) == JobScheduler.RESULT_SUCCESS
    }.getOrDefault(false)

    fun cancel(context: Context, uploadId: Long) {
        context.getSystemService(JobScheduler::class.java).cancel(jobId(uploadId))
        WorkManager.getInstance(context).cancelUniqueWork(name(uploadId))
        Notifications.cancel(context, uploadId)
    }

    // Kept clear of the small ids WorkManager hands to its own jobs.
    private fun jobId(uploadId: Long): Int = 1_000_000 + (uploadId % 1_000_000_000).toInt()

    const val KEY_UPLOAD_ID = "upload_id"
}

/** Uploads one staged file. The history row is the source of truth; this only moves it along. */
object UploadRunner {
    enum class Outcome { FINISHED, RETRY }

    /**
     * [mayRetry] lets a dropped connection be tried again later instead of being reported as a failure.
     * A failure is recorded on the row and shown as a notification, and still counts as [Outcome.FINISHED]:
     * trying again after that is the user's choice.
     */
    suspend fun run(context: Context, uploadId: Long, mayRetry: Boolean): Outcome {
        val container = context.container
        val uploads = container.db.uploads()
        val upload = uploads.byId(uploadId) ?: return Outcome.FINISHED
        if (upload.status == UploadStatus.DONE) return Outcome.FINISHED

        suspend fun fail(short: String, long: String): Outcome {
            uploads.setStatus(uploadId, UploadStatus.FAILED, error = short)
            Notifications.showFailure(context, uploadId, upload.fileName, long)
            return Outcome.FINISHED
        }

        val destination = container.db.destinations().byId(upload.destinationId)
            ?: return fail("Destination was removed", "The destination for this upload was removed.")
        val file = upload.localPath?.let(::File)?.takeIf { it.exists() }
            ?: return fail("File no longer available", "The file is no longer available. Share it again.")

        return try {
            val total = file.length().coerceAtLeast(1)
            var lastPercent = 0
            var progress: Job? = null
            val onProgress: (Long) -> Unit = { sent ->
                val percent = (sent * 100 / total).toInt().coerceIn(0, 99)
                if (percent >= lastPercent + 2) {
                    lastPercent = percent
                    progress = container.scope.launch {
                        uploads.setProgress(uploadId, percent)
                        Notifications.showProgress(context, uploadId, upload.fileName, percent)
                    }
                }
            }

            var key: String? = null
            var deleteUrl: String? = null
            val url: String
            when (destination.provider) {
                Providers.HTTP -> {
                    val config = HttpConfig.fromJson(container.secrets.decrypt(destination.httpJson.orEmpty()))
                    uploads.update(upload.copy(status = UploadStatus.UPLOADING, progress = 0, error = null))
                    val sent = runInterruptible(Dispatchers.IO) {
                        container.http.upload(config, upload.fileName, upload.mimeType, file.length(), { file.inputStream() }, onProgress)
                    }
                    url = sent.url
                    deleteUrl = sent.deletionUrl
                }
                Providers.WEBDAV -> {
                    val config = WebDavConfig.fromJson(container.secrets.decrypt(destination.httpJson.orEmpty()))
                    // Keep the path across retries so a second attempt overwrites the first.
                    val path = upload.objectKey ?: RemotePaths.render(config.pathTemplate, upload.fileName, upload.mimeType)
                    key = path
                    uploads.update(upload.copy(objectKey = path, status = UploadStatus.UPLOADING, progress = 0, error = null))
                    runInterruptible(Dispatchers.IO) {
                        container.webdav.upload(config, path, upload.mimeType, file.length(), { file.inputStream() }, onProgress)
                    }
                    url = config.linkFor(path)
                }
                Providers.FTP -> {
                    val config = FtpConfig.fromJson(container.secrets.decrypt(destination.httpJson.orEmpty()))
                    val path = upload.objectKey ?: RemotePaths.render(config.pathTemplate, upload.fileName, upload.mimeType)
                    key = path
                    uploads.update(upload.copy(objectKey = path, status = UploadStatus.UPLOADING, progress = 0, error = null))
                    runInterruptible(Dispatchers.IO) {
                        container.ftp.upload(config, path, file.length(), { file.inputStream() }, onProgress)
                    }
                    url = config.linkFor(path)
                }
                else -> {
                    val config = container.s3Config(destination)
                    // Keep the key across retries so a second attempt overwrites the first instead of leaving a stray object.
                    val objectKey = upload.objectKey ?: KeyTemplate.render(config.keyTemplate, upload.fileName)
                    key = objectKey
                    uploads.update(upload.copy(objectKey = objectKey, status = UploadStatus.UPLOADING, progress = 0, error = null))
                    runInterruptible(Dispatchers.IO) {
                        container.s3.putObject(config, objectKey, upload.mimeType, file.length(), { file.inputStream() }, onProgress)
                    }
                    url = if (destination.linkType == LinkType.SIGNED) {
                        config.presignedGetUrl(objectKey, Instant.now(), LinkType.SIGNED_EXPIRY_SECONDS)
                    } else {
                        config.publicUrl(objectKey)
                    }
                }
            }
            // A late progress update must not land after, and replace, the finished notification.
            progress?.join()

            uploads.update(
                upload.copy(
                    objectKey = key, url = url, deleteUrl = deleteUrl, size = file.length(), localPath = null,
                    status = UploadStatus.DONE, progress = 100, error = null,
                ),
            )
            file.delete()

            val copied = container.settings.current().copyLinkWhenDone &&
                withContext(Dispatchers.Main) { Clipboard.copy(context, url) }
            Notifications.showResult(context, uploadId, upload.fileName, url, copied)
            Outcome.FINISHED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is IOException && mayRetry) {
                uploads.setStatus(uploadId, UploadStatus.QUEUED)
                Outcome.RETRY
            } else {
                fail(ErrorText.short(e), ErrorText.long(e))
            }
        }
    }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val uploadId get() = inputData.getLong(UploadScheduler.KEY_UPLOAD_ID, -1)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val upload = applicationContext.container.db.uploads().byId(uploadId)
        return ForegroundInfo(
            Notifications.progressId(uploadId),
            Notifications.progress(applicationContext, uploadId, upload?.fileName ?: "Upload", 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    override suspend fun doWork(): Result {
        // Not allowed while the app is in the background on some versions; the upload still runs without it.
        runCatching { setForeground(getForegroundInfo()) }
        return when (UploadRunner.run(applicationContext, uploadId, mayRetry = runAttemptCount < MAX_NETWORK_RETRIES)) {
            UploadRunner.Outcome.RETRY -> Result.retry()
            UploadRunner.Outcome.FINISHED -> Result.success()
        }
    }

    private companion object {
        const val MAX_NETWORK_RETRIES = 2
    }
}

/** Android 14+ user-initiated data transfer job. Only ever scheduled on those versions; see [UploadScheduler]. */
class UploadJobService : JobService() {
    private val running = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val uploadId = params.extras.getLong(UploadScheduler.KEY_UPLOAD_ID, -1)
        // The system requires a notification for the whole life of a user-initiated job.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setNotification(
                params,
                Notifications.progressId(uploadId),
                Notifications.progress(this, uploadId, "Upload", 0),
                JOB_END_NOTIFICATION_POLICY_REMOVE,
            )
        }
        running[params.jobId] = container.scope.launch {
            UploadRunner.run(applicationContext, uploadId, mayRetry = false)
            jobFinished(params, false)
        }
        return true
    }

    /** The system stopped the job, usually because the connection dropped. Ask for it to be run again. */
    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        val uploadId = params.extras.getLong(UploadScheduler.KEY_UPLOAD_ID, -1)
        container.scope.launch { container.db.uploads().requeue(uploadId) }
        return true
    }
}
