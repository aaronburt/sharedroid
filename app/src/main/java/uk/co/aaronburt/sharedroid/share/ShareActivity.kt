package uk.co.aaronburt.sharedroid.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uk.co.aaronburt.sharedroid.MainActivity
import uk.co.aaronburt.sharedroid.container
import uk.co.aaronburt.sharedroid.data.AppSettings
import uk.co.aaronburt.sharedroid.data.Destination
import uk.co.aaronburt.sharedroid.ui.Group
import uk.co.aaronburt.sharedroid.ui.PrefRow
import uk.co.aaronburt.sharedroid.ui.RowDivider
import uk.co.aaronburt.sharedroid.ui.RowText
import uk.co.aaronburt.sharedroid.ui.Sd
import uk.co.aaronburt.sharedroid.ui.SdButton
import uk.co.aaronburt.sharedroid.ui.ShareDroidTheme
import uk.co.aaronburt.sharedroid.ui.providerLabel
import uk.co.aaronburt.sharedroid.upload.UploadSource

/**
 * Entry point for anything shared from another app. Shows a small sheet over that app,
 * hands the files to the uploader and gets out of the way; progress lives in the notification.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sources = sources(intent)
        if (sources.isEmpty()) {
            Toast.makeText(this, "Nothing to upload", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val settings = container.settings.current()
            val destinations = container.db.destinations().all()
            val active = container.activeDestination()

            if (active != null && settings.uploadWithoutAsking) {
                upload(sources, active)
                return@launch
            }
            setContent {
                ShareDroidTheme(settings) {
                    ShareSheet(sources.size, destinations, active, settings)
                }
            }
        }
    }

    private fun upload(sources: List<UploadSource>, destination: Destination) {
        val accepted = container.stager.stage(sources, destination)
        Toast.makeText(this, if (accepted == 0) "Couldn't read that file" else "Uploading to ${destination.name}", Toast.LENGTH_SHORT).show()
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ShareSheet(count: Int, destinations: List<Destination>, active: Destination?, settings: AppSettings) {
        var chosen by remember { mutableStateOf(active) }
        val sources = remember { sources(intent) }

        ModalBottomSheet(
            onDismissRequest = ::finish,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Sd.colors.surface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (count == 1) "Upload 1 item" else "Upload $count items",
                    color = Sd.colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                )
                val destination = chosen
                if (destination == null) {
                    Text(
                        "Add a destination in ShareDroid first, then share this again.",
                        Modifier.padding(bottom = 12.dp), color = Sd.colors.muted, fontSize = 13.sp,
                    )
                    SdButton("Open ShareDroid", Modifier.fillMaxWidth()) {
                        startActivity(Intent(this@ShareActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    }
                    return@Column
                }

                Text(
                    if (settings.copyLinkWhenDone) "The link is copied when it finishes." else "You'll get a notification with the link.",
                    Modifier.padding(bottom = 12.dp), color = Sd.colors.muted, fontSize = 13.sp,
                )
                if (destinations.size > 1) {
                    Group {
                        destinations.forEachIndexed { index, item ->
                            if (index > 0) RowDivider()
                            PrefRow(Modifier.clickable(role = Role.RadioButton) { chosen = item }) {
                                RowText(item.name, providerLabel(item), Modifier.weight(1f), wrap = false)
                                val on = item.id == destination.id
                                Box(
                                    Modifier.size(20.dp).border(2.dp, if (on) Sd.colors.accent else Sd.colors.line, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(Sd.colors.accent))
                                }
                            }
                        }
                    }
                }
                SdButton("Upload to ${destination.name}", Modifier.fillMaxWidth()) { upload(sources, destination) }
            }
        }
    }

    private fun sources(intent: Intent): List<UploadSource> = when (intent.action) {
        Intent.ACTION_SEND -> {
            val uri = intent.parcelable(Intent.EXTRA_STREAM)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            listOfNotNull(uri?.let(UploadSource::Content) ?: text?.takeIf { it.isNotBlank() }?.let(UploadSource::Text))
        }
        Intent.ACTION_SEND_MULTIPLE -> intent.parcelableList(Intent.EXTRA_STREAM).map(UploadSource::Content)
        else -> emptyList()
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelable(name: String): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(name, Uri::class.java) else getParcelableExtra(name)

    @Suppress("DEPRECATION")
    private fun Intent.parcelableList(name: String): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(name, Uri::class.java) else getParcelableArrayListExtra(name))
            .orEmpty()
}
