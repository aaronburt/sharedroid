package uk.co.aaronburt.sharedroid.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.sharedroid.MainViewModel
import uk.co.aaronburt.sharedroid.R
import uk.co.aaronburt.sharedroid.data.Providers
import uk.co.aaronburt.sharedroid.data.Upload
import uk.co.aaronburt.sharedroid.data.UploadStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: MainViewModel) {
    val uploads by vm.uploads.collectAsStateWithLifecycle()
    val destinations by vm.destinations.collectAsStateWithLifecycle()
    val list = uploads ?: return

    if (list.isEmpty()) {
        if (destinations.isEmpty()) {
            EmptyState(
                "Nowhere to upload yet",
                "Add a destination, then share any photo, video or file to ShareDroid.",
                "Add a destination", vm::addDestination,
            )
        } else {
            EmptyState("Nothing uploaded yet", "Tap + to upload a photo, or share something to ShareDroid from another app.")
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                "Recent", Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
                color = Sd.colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        items(list, key = { it.id }) { upload -> UploadRow(upload, vm) }
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Thumb(R.drawable.ic_upload)
        Text(title, color = Sd.colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(body, color = Sd.colors.muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        if (action != null) SdButton(action, Modifier.padding(top = 8.dp).fillMaxWidth(), onClick = onAction)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UploadRow(upload: Upload, vm: MainViewModel) {
    val haptics = LocalHapticFeedback.current
    val failed = upload.status == UploadStatus.FAILED
    val done = upload.status == UploadStatus.DONE

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onLongClickLabel = "More actions",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.rowSheet = upload
                },
                onClick = {
                    when {
                        failed -> vm.retry(upload)
                        done -> vm.copyLink(upload)
                    }
                },
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(if (upload.inProgress) R.drawable.ic_upload else iconFor(upload.mimeType))
        Column(Modifier.weight(1f)) {
            Text(upload.fileName, color = Sd.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (upload.status) {
                    UploadStatus.PREPARING -> "Preparing"
                    UploadStatus.QUEUED -> "Waiting to upload"
                    UploadStatus.UPLOADING -> "Uploading · ${upload.progress}%"
                    UploadStatus.FAILED -> "${upload.error ?: "Upload failed"} · Tap to retry"
                    else -> "${formatSize(upload.size)} · ${formatWhen(upload.createdAt)}"
                },
                Modifier.padding(top = 2.dp), color = if (failed) Sd.colors.warn else Sd.colors.muted,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (upload.inProgress) {
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(Sd.colors.line)) {
                    Box(Modifier.fillMaxWidth(upload.progress / 100f).fillMaxHeight().background(Sd.colors.accent))
                }
            }
        }
        when {
            failed -> IconBtn(R.drawable.ic_retry, "Retry upload") { vm.retry(upload) }
            done -> IconBtn(R.drawable.ic_copy, "Copy link") { vm.copyLink(upload) }
        }
    }
}

/** Long-press actions for one upload. Deleting from the bucket takes a second tap to confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadActionsSheet(vm: MainViewModel, upload: Upload) {
    val context = LocalContext.current
    val destinations by vm.destinations.collectAsStateWithLifecycle()
    val destination = destinations.firstOrNull { it.id == upload.destinationId }
    val done = upload.status == UploadStatus.DONE && upload.url != null
    var armed by remember(upload.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { vm.rowSheet = null },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Sd.colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                upload.fileName, Modifier.padding(horizontal = 4.dp).padding(bottom = 4.dp), color = Sd.colors.muted,
                fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (done) {
                SheetButton("Copy link") {
                    vm.copyLink(upload)
                    vm.rowSheet = null
                }
                SheetButton("Open in browser") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upload.url))) }
                    vm.rowSheet = null
                }
            }
            SheetButton(if (upload.inProgress) "Cancel upload" else "Remove from history") { vm.removeFromHistory(upload) }
            val http = destination?.provider == Providers.HTTP
            val removable = if (http) upload.deleteUrl != null else upload.objectKey != null
            if (done && destination != null && removable) {
                if (http || destination.canDelete) {
                    SheetButton(
                        when {
                            vm.deleting -> "Deleting…"
                            armed -> "Tap again to delete"
                            else -> "Delete from ${destination.name}"
                        },
                        danger = true, armed = armed,
                    ) {
                        if (armed) vm.deleteFromBucket(upload) else armed = true
                    }
                } else {
                    Text(
                        "Your key for this destination can't delete files, so this option is hidden.",
                        Modifier.padding(horizontal = 4.dp), color = Sd.colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { vm.rowSheet = null }.padding(13.dp), contentAlignment = Alignment.Center) {
                Text("Cancel", color = Sd.colors.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, danger: Boolean = false, armed: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (armed) Sd.colors.warn else Sd.colors.bg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = when {
                armed -> Color.White
                danger -> Sd.colors.warn
                else -> Sd.colors.ink
            },
        )
    }
}

private fun iconFor(mime: String): Int = when {
    mime.startsWith("image/") -> R.drawable.ic_image
    mime.startsWith("video/") -> R.drawable.ic_video
    else -> R.drawable.ic_file
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.UK, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.UK, "%.1f MB", bytes / 1e6)
    bytes >= 1_000 -> "${bytes / 1000} KB"
    else -> "$bytes B"
}

fun formatWhen(millis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - millis) / 60_000
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        day == today -> "${minutes / 60} h ago"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
        else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK))
    }
}

