package uk.co.aaronburt.sharedroid.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val activeDestinationId: Long? = null,
    val trueBlack: Boolean = false,
    val matchWallpaper: Boolean = false,
    /** A preset name from the theme, or [ACCENT_CUSTOM]. */
    val accent: String = "teal",
    val customColor: Int = 0xFF7A3FC4.toInt(),
    val homeTitle: String = "",
    val uploadWithoutAsking: Boolean = false,
    val copyLinkWhenDone: Boolean = true,
    val stripLocation: Boolean = true,
    val askedForNotifications: Boolean = false,
) {
    companion object {
        const val ACCENT_CUSTOM = "custom"
        const val HOME_TITLE_MAX = 24
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            activeDestinationId = p[ACTIVE],
            trueBlack = p[TRUE_BLACK] ?: false,
            matchWallpaper = p[WALLPAPER] ?: false,
            accent = p[ACCENT] ?: "teal",
            customColor = p[CUSTOM] ?: 0xFF7A3FC4.toInt(),
            homeTitle = p[TITLE] ?: "",
            uploadWithoutAsking = p[NO_ASK] ?: false,
            copyLinkWhenDone = p[COPY] ?: true,
            stripLocation = p[STRIP] ?: true,
            askedForNotifications = p[ASKED_NOTIF] ?: false,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setActiveDestination(id: Long?) = edit { if (id == null) it.remove(ACTIVE) else it[ACTIVE] = id }
    suspend fun setTrueBlack(on: Boolean) = edit { it[TRUE_BLACK] = on }
    suspend fun setMatchWallpaper(on: Boolean) = edit { it[WALLPAPER] = on }
    suspend fun setAccent(name: String) = edit { it[ACCENT] = name }
    suspend fun setCustomColor(argb: Int) = edit { it[CUSTOM] = argb; it[ACCENT] = AppSettings.ACCENT_CUSTOM }
    suspend fun setHomeTitle(title: String) = edit { it[TITLE] = title.take(AppSettings.HOME_TITLE_MAX) }
    suspend fun setUploadWithoutAsking(on: Boolean) = edit { it[NO_ASK] = on }
    suspend fun setCopyLinkWhenDone(on: Boolean) = edit { it[COPY] = on }
    suspend fun setStripLocation(on: Boolean) = edit { it[STRIP] = on }
    suspend fun setAskedForNotifications() = edit { it[ASKED_NOTIF] = true }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val ACTIVE = longPreferencesKey("active_destination")
        val TRUE_BLACK = booleanPreferencesKey("true_black")
        val WALLPAPER = booleanPreferencesKey("match_wallpaper")
        val ACCENT = stringPreferencesKey("accent")
        val CUSTOM = intPreferencesKey("custom_color")
        val TITLE = stringPreferencesKey("home_title")
        val NO_ASK = booleanPreferencesKey("upload_without_asking")
        val COPY = booleanPreferencesKey("copy_link_when_done")
        val STRIP = booleanPreferencesKey("strip_location")
        val ASKED_NOTIF = booleanPreferencesKey("asked_for_notifications")
    }
}
