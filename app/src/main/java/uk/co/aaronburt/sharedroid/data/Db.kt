package uk.co.aaronburt.sharedroid.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One configured bucket. The two key fields hold ciphertext from [SecretBox], never the plain keys. */
@Entity(tableName = "destinations")
data class Destination(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Always [Providers.S3_COMPATIBLE] for now. Kept so other destination types can be added later. */
    val provider: String,
    val endpoint: String,
    val region: String,
    val bucket: String,
    val pathStyle: Boolean,
    val useHttps: Boolean,
    val accessKeyEnc: String,
    val secretKeyEnc: String,
    val keyTemplate: String,
    val publicUrlBase: String?,
    /** [LinkType.PUBLIC] or [LinkType.SIGNED]. */
    val linkType: String,
    val cacheControl: String?,
    val storageClass: String?,
    val publicAcl: Boolean,
    val stripLocation: Boolean,
    /** False once the bucket has refused a delete, so the app stops offering it. */
    val canDelete: Boolean = true,
    /** Encrypted config JSON for every provider except S3: HTTP, WebDAV and FTP. */
    val httpJson: String? = null,
)

object Providers {
    const val S3_COMPATIBLE = "s3c"
    const val HTTP = "http"
    const val WEBDAV = "webdav"
    const val FTP = "ftp"
}

object LinkType {
    const val PUBLIC = "public"
    const val SIGNED = "signed"

    /** How long an expiring link works for. */
    const val SIGNED_EXPIRY_SECONDS = 86_400L
}

object UploadStatus {
    /** Being copied out of the source app. */
    const val PREPARING = "preparing"
    const val QUEUED = "queued"
    const val UPLOADING = "uploading"
    const val DONE = "done"
    const val FAILED = "failed"
}

@Entity(tableName = "uploads")
data class Upload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destinationId: Long,
    val destinationName: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    /** Private copy of the file, kept until the upload succeeds so a retry is possible. */
    val localPath: String?,
    val objectKey: String?,
    val url: String?,
    val status: String,
    val progress: Int,
    val error: String?,
    val createdAt: Long,
    /** Where to request deletion, for destinations that provide one. */
    val deleteUrl: String? = null,
) {
    val inProgress: Boolean get() = status == UploadStatus.PREPARING || status == UploadStatus.QUEUED || status == UploadStatus.UPLOADING
}

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations ORDER BY id")
    fun observeAll(): Flow<List<Destination>>

    @Query("SELECT * FROM destinations ORDER BY id")
    suspend fun all(): List<Destination>

    @Query("SELECT * FROM destinations WHERE id = :id")
    suspend fun byId(id: Long): Destination?

    @Insert
    suspend fun insert(destination: Destination): Long

    @Update
    suspend fun update(destination: Destination)

    @Query("UPDATE destinations SET canDelete = :canDelete WHERE id = :id")
    suspend fun setCanDelete(id: Long, canDelete: Boolean)

    @Query("DELETE FROM destinations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface UploadDao {
    @Query("SELECT * FROM uploads ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Upload>>

    @Query("SELECT * FROM uploads WHERE id = :id")
    suspend fun byId(id: Long): Upload?

    @Insert
    suspend fun insert(upload: Upload): Long

    @Update
    suspend fun update(upload: Upload)

    @Query("UPDATE uploads SET status = :status, progress = :progress, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, progress: Int = 0, error: String? = null)

    @Query("UPDATE uploads SET progress = :progress WHERE id = :id AND status = 'uploading'")
    suspend fun setProgress(id: Long, progress: Int)

    /** Puts an interrupted upload back in the queue. Does nothing if it has since finished or failed. */
    @Query("UPDATE uploads SET status = 'queued' WHERE id = :id AND status = 'uploading'")
    suspend fun requeue(id: Long)

    @Query("DELETE FROM uploads WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Destination::class, Upload::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun destinations(): DestinationDao
    abstract fun uploads(): UploadDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE destinations ADD COLUMN httpJson TEXT")
        db.execSQL("ALTER TABLE uploads ADD COLUMN deleteUrl TEXT")
    }
}
