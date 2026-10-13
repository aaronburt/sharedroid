package uk.co.aaronburt.sharedroid.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import uk.co.aaronburt.sharedroid.MainActivity
import uk.co.aaronburt.sharedroid.R

object Clipboard {
    /** Best effort: Android can refuse clipboard writes from the background. Returns whether it worked. */
    fun copy(context: Context, url: String): Boolean = runCatching {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ShareDroid link", url))
    }.isSuccess
}

object Notifications {
    // Separate channels so progress can be silenced while results and errors stay on.
    private const val CHANNEL_PROGRESS = "progress"
    // Finished uploads are silent: the person is watching the app. (An Android channel's importance can't be
    // changed once created, so this is a new id and the old "results" channel is removed.)
    private const val CHANNEL_RESULTS = "results_quiet"
    private const val CHANNEL_RESULTS_OLD = "results"
    private const val CHANNEL_ERRORS = "errors"

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun createChannels(context: Context) {
        manager(context).deleteNotificationChannel(CHANNEL_RESULTS_OLD)
        manager(context).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PROGRESS, "Upload progress", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_RESULTS, "Finished uploads", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_ERRORS, "Failed uploads", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun progressId(uploadId: Long): Int = (uploadId * 2).toInt()
    private fun resultId(uploadId: Long): Int = (uploadId * 2 + 1).toInt()

    fun progress(context: Context, uploadId: Long, fileName: String, percent: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle("Uploading $fileName")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Cancel", receiver(context, uploadId, NotificationActions.CANCEL))
            .build()

    fun showProgress(context: Context, uploadId: Long, fileName: String, percent: Int) {
        manager(context).notify(progressId(uploadId), progress(context, uploadId, fileName, percent))
    }

    fun showResult(context: Context, uploadId: Long, fileName: String, url: String, copied: Boolean) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val share = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null)
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(if (copied) "Uploaded · Link copied" else "Uploaded $fileName")
            .setContentText(url)
            .setStyle(NotificationCompat.BigTextStyle().bigText(url))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Copy", receiver(context, uploadId, NotificationActions.COPY, url))
            .addAction(0, "Share", activity(context, uploadId, 1, share))
            .addAction(0, "Open", activity(context, uploadId, 2, view))
            .build()
        manager(context).cancel(progressId(uploadId))
        manager(context).notify(resultId(uploadId), notification)
    }

    fun showFailure(context: Context, uploadId: Long, fileName: String, reason: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle("Couldn't upload $fileName")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Retry", receiver(context, uploadId, NotificationActions.RETRY))
            .build()
        manager(context).cancel(progressId(uploadId))
        manager(context).notify(resultId(uploadId), notification)
    }

    fun cancel(context: Context, uploadId: Long) {
        manager(context).cancel(progressId(uploadId))
        manager(context).cancel(resultId(uploadId))
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun activity(context: Context, uploadId: Long, slot: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
        context, (uploadId * 4 + slot).toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun receiver(context: Context, uploadId: Long, action: String, url: String? = null): PendingIntent {
        val intent = Intent(context, NotificationActions::class.java)
            .setAction(action)
            .putExtra(NotificationActions.EXTRA_UPLOAD_ID, uploadId)
            .putExtra(NotificationActions.EXTRA_URL, url)
        val slot = listOf(NotificationActions.COPY, NotificationActions.RETRY, NotificationActions.CANCEL).indexOf(action)
        return PendingIntent.getBroadcast(
            context, (uploadId * 4 + slot).toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Handles the buttons on upload notifications. */
class NotificationActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uploadId = intent.getLongExtra(EXTRA_UPLOAD_ID, -1)
        when (intent.action) {
            COPY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return
                if (Clipboard.copy(context, url)) Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
            }
            RETRY -> {
                Notifications.cancel(context, uploadId)
                UploadScheduler.enqueue(context, uploadId)
            }
            CANCEL -> UploadActions.remove(context, uploadId)
        }
    }

    companion object {
        const val COPY = "uk.co.aaronburt.sharedroid.COPY"
        const val RETRY = "uk.co.aaronburt.sharedroid.RETRY"
        const val CANCEL = "uk.co.aaronburt.sharedroid.CANCEL"
        const val EXTRA_UPLOAD_ID = "upload_id"
        const val EXTRA_URL = "url"
    }
}
