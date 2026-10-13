package uk.co.aaronburt.sharedroid.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.sharedroid.BuildConfig
import uk.co.aaronburt.sharedroid.MainViewModel
import uk.co.aaronburt.sharedroid.R
import uk.co.aaronburt.sharedroid.data.AppSettings
import uk.co.aaronburt.sharedroid.data.Destination
import uk.co.aaronburt.sharedroid.data.Providers

private const val SOURCE_URL = "https://github.com/aaronburt/sharedroid"

@Composable
fun SettingsScreen(vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val destinations by vm.destinations.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    var pickingColor by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 130.dp)) {
        GroupTitle("Destinations")
        Group {
            destinations.forEach { destination ->
                PrefRow(Modifier.clickable(role = Role.Button) { vm.editDestination(destination) }) {
                    Thumb(R.drawable.ic_cloud, size = 36.dp)
                    RowText(
                        destination.name,
                        providerLabel(destination) + if (destination.id == active?.id) " · Active" else "",
                        Modifier.weight(1f), wrap = false,
                    )
                    Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(16.dp), tint = Sd.colors.muted)
                }
                RowDivider()
            }
            PrefRow(Modifier.clickable(role = Role.Button, onClick = vm::addDestination)) {
                RowText("+ Add destination", titleColor = Sd.colors.accent)
            }
        }

        GroupTitle("Appearance")
        Group {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowText("Dark theme", "True black uses a pure black background, which saves some battery on OLED screens")
                Segmented(listOf(false to "Dark", true to "True black"), settings.trueBlack, { vm.update { setTrueBlack(it) } })
            }
            RowDivider()
            SwitchRow("Match wallpaper colours", "Use the colours from your wallpaper", settings.matchWallpaper) { vm.update { setMatchWallpaper(it) } }
            RowDivider()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowText("Home title", "Shown in the top bar of the app. Leave blank for ShareDroid.")
                // Kept locally so typing isn't interrupted by the save round trip.
                var title by remember { mutableStateOf(settings.homeTitle) }
                SdTextField(
                    title,
                    {
                        title = it.take(AppSettings.HOME_TITLE_MAX)
                        vm.update { setHomeTitle(title.trim()) }
                    },
                    placeholder = "ShareDroid", plainText = true, bold = true,
                )
            }
            RowDivider()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowText(
                    "Accent colour",
                    if (settings.matchWallpaper) "Using your wallpaper colours. Turn that off to choose." else "Choose one, or pick your own",
                )
                Row(Modifier.alpha(if (settings.matchWallpaper) 0.35f else 1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val enabled = !settings.matchWallpaper
                    AccentPresets.forEach { preset ->
                        Swatch(
                            androidx.compose.ui.graphics.SolidColor(if (Sd.colors.dark) preset.dark else preset.light),
                            preset.label, enabled && settings.accent == preset.name, enabled,
                        ) { vm.update { setAccent(preset.name) } }
                    }
                    Swatch(
                        Brush.sweepGradient(
                            listOf(Color(0xFFE5484D), Color(0xFFF5A524), Color(0xFF3EC46D), Color(0xFF2BB5C9), Color(0xFF4C6EF5), Color(0xFFB44AE0), Color(0xFFE5484D)),
                        ),
                        "Custom colour", enabled && settings.accent == AppSettings.ACCENT_CUSTOM, enabled,
                    ) { pickingColor = true }
                }
            }
        }

        GroupTitle("Uploading")
        Group {
            SwitchRow("Upload without asking", "Skip the confirm sheet when sharing from other apps", settings.uploadWithoutAsking) {
                vm.update { setUploadWithoutAsking(it) }
            }
            RowDivider()
            SwitchRow("Copy link when done", "Put the URL on the clipboard", settings.copyLinkWhenDone) { vm.update { setCopyLinkWhenDone(it) } }
            RowDivider()
            SwitchRow(
                "Remove location from photos",
                "Removes location and camera data from JPEG photos. Videos and other file types upload unchanged.",
                settings.stripLocation,
            ) { vm.update { setStripLocation(it) } }
        }

        GroupTitle("About")
        Group {
            PrefRow(Modifier.clickable(role = Role.Button) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) } }) {
                RowText(
                    "Version",
                    "${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (debug)" else ""} · Source on GitHub",
                    Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_external), "Opens in browser", Modifier.size(16.dp), tint = Sd.colors.muted)
            }
        }
    }

    if (pickingColor) {
        ColorPickerDialog(Color(settings.customColor), onDismiss = { pickingColor = false }) {
            vm.update { setCustomColor(it.toArgb()) }
            pickingColor = false
        }
    }
}

/** The provider guessed from the endpoint, then the bucket: "Cloudflare R2 · my-uploads". */
fun providerLabel(destination: Destination): String {
    if (destination.provider == Providers.HTTP) return "Custom HTTP · ${destination.endpoint}"
    if (destination.provider == Providers.WEBDAV) return "WebDAV · ${destination.endpoint}"
    if (destination.provider == Providers.FTP) return "FTP · ${destination.endpoint}"
    val host = destination.endpoint.lowercase()
    val provider = when {
        "r2.cloudflarestorage.com" in host -> "Cloudflare R2"
        "backblazeb2.com" in host -> "Backblaze B2"
        "wasabisys.com" in host -> "Wasabi"
        "digitaloceanspaces.com" in host -> "DigitalOcean Spaces"
        "amazonaws.com" in host -> "Amazon S3"
        else -> "S3-compatible"
    }
    return "$provider · ${destination.bucket}"
}

@Composable
private fun Swatch(fill: Brush, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).then(if (selected) Modifier.border(2.dp, Sd.colors.ink, CircleShape) else Modifier).padding(4.dp)
            .clip(CircleShape).background(fill).border(1.dp, Sd.colors.line, CircleShape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClickLabel = label, onClick = onClick),
    )
}

@Composable
private fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue.coerceIn(0f, 360f), saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sd.colors.surface,
        title = { Text("Custom colour", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(color).border(1.dp, Sd.colors.line, CircleShape))
                    Text(String.format("#%06X", color.toArgb() and 0xFFFFFF), color = Sd.colors.ink, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                }
                PickerSlider("Hue", hue, 0f..360f) { hue = it }
                PickerSlider("Saturation", saturation, 0f..1f) { saturation = it }
                PickerSlider("Brightness", value, 0f..1f) { value = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Sd.colors.muted) } },
    )
}

@Composable
private fun PickerSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, color = Sd.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Slider(value, onChange, valueRange = range)
    }
}
