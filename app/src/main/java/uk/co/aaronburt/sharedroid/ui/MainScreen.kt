package uk.co.aaronburt.sharedroid.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.sharedroid.MainViewModel
import uk.co.aaronburt.sharedroid.R
import uk.co.aaronburt.sharedroid.Screen
import uk.co.aaronburt.sharedroid.data.AppSettings
import uk.co.aaronburt.sharedroid.upload.UploadSource

private val NavHeight = 72.dp
private val PlusOverhang = 17.dp

@Composable
fun ShareDroidRoot(vm: MainViewModel, settings: AppSettings) {
    val active by vm.active.collectAsStateWithLifecycle()
    val screen = vm.screen
    val sub = screen != Screen.Home && screen != Screen.Settings

    BackHandler(enabled = vm.canGoBack) { vm.back() }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.upload(listOf(UploadSource.Content(uri)))
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.upload(listOf(UploadSource.Content(uri)))
    }

    Box(Modifier.fillMaxSize().background(Sd.colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AppBar(
                title = when (screen) {
                    Screen.Home -> settings.homeTitle.ifBlank { "ShareDroid" }
                    Screen.Settings -> "Settings"
                    Screen.Providers -> "Add destination"
                    Screen.HttpForm -> if (vm.httpForm?.editing == null) "Add destination" else "Edit destination"
                    Screen.WebDavForm -> if (vm.webDavForm?.editing == null) "Add destination" else "Edit destination"
                    Screen.FtpForm -> if (vm.ftpForm?.editing == null) "Add destination" else "Edit destination"
                    Screen.Form -> if (vm.form?.editing == null) "Add destination" else "Edit destination"
                },
                sub = sub,
                destination = active?.name,
                onBack = vm::back,
                onDestination = vm::cycleDestination,
            )
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Settings -> SettingsScreen(vm, settings)
                    Screen.Providers -> ProvidersScreen(vm)
                    Screen.HttpForm -> vm.httpForm?.let { HttpFormScreen(vm, it) }
                    Screen.WebDavForm -> vm.webDavForm?.let { WebDavFormScreen(vm, it) }
                    Screen.FtpForm -> vm.ftpForm?.let { FtpFormScreen(vm, it) }
                    Screen.Form -> vm.form?.let { DestinationFormScreen(vm, it) }
                }
            }
        }

        if (!sub) {
            BottomNav(
                selected = screen,
                onTab = vm::showTab,
                onPlus = {
                    if (vm.readyToUpload()) {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                },
                onFile = { if (vm.readyToUpload()) pickFile.launch("*/*") },
                onPaste = vm::pasteFromClipboard,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        AnimatedVisibility(
            vm.toast != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (sub) 24.dp else NavHeight + 30.dp),
        ) {
            var last by remember { mutableStateOf("") }
            vm.toast?.let { last = it }
            Text(
                last, Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(12.dp)).background(Sd.colors.ink).padding(horizontal = 14.dp, vertical = 9.dp),
                color = Sd.colors.bg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }

        vm.rowSheet?.let { UploadActionsSheet(vm, it) }
    }
}

@Composable
private fun AppBar(title: String, sub: Boolean, destination: String?, onBack: () -> Unit, onDestination: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp).height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sub) {
            IconBtn(R.drawable.ic_back, "Back", onClick = onBack)
        } else {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Sd.colors.accent), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_upload), null, Modifier.size(15.dp), tint = Sd.colors.onAccent)
            }
        }
        Text(
            title, Modifier.weight(1f).padding(start = 8.dp, end = 10.dp), color = Sd.colors.ink,
            fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (!sub && destination != null) {
            Row(
                Modifier.clip(CircleShape).background(Sd.colors.chip)
                    .clickable(role = Role.Button, onClickLabel = "Switch destination", onClick = onDestination)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Sd.colors.ok))
                Text(destination, color = Sd.colors.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomNav(
    selected: Screen,
    onTab: (Screen) -> Unit,
    onPlus: () -> Unit,
    onFile: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }

    Box(modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = PlusOverhang).fillMaxWidth().background(Sd.colors.nav)) {
            RowDivider()
            Row(Modifier.fillMaxWidth().height(NavHeight), verticalAlignment = Alignment.CenterVertically) {
                Tab(R.drawable.ic_home, "Home", selected == Screen.Home, Modifier.weight(1f)) { onTab(Screen.Home) }
                androidx.compose.foundation.layout.Spacer(Modifier.width(96.dp))
                Tab(R.drawable.ic_settings, "Settings", selected == Screen.Settings, Modifier.weight(1f)) { onTab(Screen.Settings) }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        // The raised +. Tap opens the photo picker; press and hold offers files and the clipboard.
        Box(Modifier.align(Alignment.TopCenter)) {
            Box(
                Modifier.size(66.dp).clip(CircleShape).background(Sd.colors.nav).padding(5.dp)
                    .shadow(8.dp, CircleShape, ambientColor = Sd.colors.accent, spotColor = Sd.colors.accent)
                    .clip(CircleShape).background(Sd.colors.accent)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = "Upload a photo",
                        onLongClickLabel = "More ways to upload",
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                        onClick = onPlus,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_plus), "Upload", Modifier.size(28.dp), tint = Sd.colors.onAccent)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp), containerColor = Sd.colors.surface) {
                DropdownMenuItem(
                    text = { Text("Choose a file", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                    onClick = { menu = false; onFile() },
                )
                DropdownMenuItem(
                    text = { Text("Paste from clipboard", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                    onClick = { menu = false; onPaste() },
                )
            }
        }
    }
}

@Composable
private fun Tab(icon: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(role = Role.Tab, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(25.dp), tint = if (selected) Sd.colors.accent else Sd.colors.muted)
        Text(label, color = if (selected) Sd.colors.ink else Sd.colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) Sd.colors.accent else androidx.compose.ui.graphics.Color.Transparent))
    }
}
