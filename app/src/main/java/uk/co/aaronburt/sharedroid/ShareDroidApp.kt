package uk.co.aaronburt.sharedroid

import android.app.Application
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.aaronburt.sharedroid.data.AppDatabase
import uk.co.aaronburt.sharedroid.data.Destination
import uk.co.aaronburt.sharedroid.data.MIGRATION_1_2
import uk.co.aaronburt.sharedroid.data.SecretBox
import uk.co.aaronburt.sharedroid.data.SettingsStore
import uk.co.aaronburt.sharedroid.ftp.FtpUploader
import uk.co.aaronburt.sharedroid.http.HttpUploader
import uk.co.aaronburt.sharedroid.http.WebDavUploader
import uk.co.aaronburt.sharedroid.s3.S3Config
import uk.co.aaronburt.sharedroid.s3.S3Uploader
import uk.co.aaronburt.sharedroid.upload.Notifications
import uk.co.aaronburt.sharedroid.upload.UploadStager

class ShareDroidApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}

/** The app's shared objects. Small enough that a DI framework would be more code than this. */
class AppContainer(val context: Context) {
    /** Outlives any screen, so copying a shared file can finish after its activity has gone. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "sharedroid.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    val settings = SettingsStore(context)
    val secrets = SecretBox()
    val s3 = S3Uploader()
    val http = HttpUploader()
    val webdav = WebDavUploader()
    val ftp = FtpUploader()
    val stager = UploadStager(this)

    fun s3Config(destination: Destination): S3Config = S3Config(
        endpoint = destination.endpoint,
        region = destination.region,
        bucket = destination.bucket,
        accessKeyId = secrets.decrypt(destination.accessKeyEnc),
        secretAccessKey = secrets.decrypt(destination.secretKeyEnc),
        pathStyle = destination.pathStyle,
        useHttps = destination.useHttps,
        keyTemplate = destination.keyTemplate,
        acl = if (destination.publicAcl) "public-read" else null,
        storageClass = destination.storageClass,
        cacheControl = destination.cacheControl,
        publicUrlBase = destination.publicUrlBase,
    )

    /** The destination uploads go to: the chosen one, or the first if that has been deleted. */
    suspend fun activeDestination(): Destination? {
        val all = db.destinations().all()
        val active = settings.current().activeDestinationId
        return all.firstOrNull { it.id == active } ?: all.firstOrNull()
    }
}

val Context.container: AppContainer get() = (applicationContext as ShareDroidApp).container
