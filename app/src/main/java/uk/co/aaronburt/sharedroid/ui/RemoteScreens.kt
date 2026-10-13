package uk.co.aaronburt.sharedroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.aaronburt.sharedroid.DestinationForm
import uk.co.aaronburt.sharedroid.FtpForm
import uk.co.aaronburt.sharedroid.MainViewModel
import uk.co.aaronburt.sharedroid.WebDavForm
import uk.co.aaronburt.sharedroid.ftp.FtpSecurity
import uk.co.aaronburt.sharedroid.http.RemotePaths

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PathPattern(value: String, onChange: (String) -> Unit, example: String) {
    Group {
        Field("Folder and name pattern") { SdTextField(value, onChange, mono = true) }
        FlowRow(
            Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DestinationForm.TOKENS.forEach { token ->
                Text(
                    token,
                    Modifier.clip(CircleShape).background(Sd.colors.accentSoft)
                        .clickable(role = Role.Button, onClickLabel = "Add $token") { onChange(value + token) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    color = Sd.colors.accent, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                )
            }
        }
        PreviewLine("Example", example)
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), color = Sd.colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        Text(value, color = Sd.colors.ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
    }
}

@Composable
private fun FormActions(
    testing: Boolean,
    result: String?,
    resultOk: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
    removable: Boolean,
    onRemove: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SdButton(if (testing) "Testing…" else "Test connection", Modifier.weight(1f), secondary = true, enabled = !testing, onClick = onTest)
        SdButton("Save", Modifier.weight(1f), onClick = onSave)
    }
    Text(
        result.orEmpty(),
        Modifier.padding(horizontal = 6.dp).padding(bottom = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
        color = if (resultOk) Sd.colors.ok else Sd.colors.warn, fontSize = 13.sp, lineHeight = 18.sp,
    )
    if (removable) {
        Text(
            if (confirm) "Tap again to remove. Files already uploaded stay where they are." else "Remove this destination",
            Modifier.fillMaxWidth().clip(CircleShape)
                .clickable(role = Role.Button) { if (confirm) onRemove() else confirm = true }
                .padding(horizontal = 6.dp, vertical = 12.dp),
            color = Sd.colors.warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun WebDavFormScreen(vm: MainViewModel, form: WebDavForm) {
    val editing = form.editing
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp).navigationBarsPadding(),
    ) {
        GroupTitle("Server")
        Group {
            Field("Name") { SdTextField(form.name, { form.name = it }, placeholder = "Nextcloud", plainText = true) }
            RowDivider()
            Field("Folder address") {
                SdTextField(form.url, { form.url = it }, placeholder = "https://cloud.example.com/remote.php/dav/files/you/", mono = true)
            }
            RowDivider()
            Field("Username") { SdTextField(form.username, { form.username = it }) }
            RowDivider()
            Field("Password", "(an app password is best)") { SdTextField(form.password, { form.password = it }, password = true) }
            RowDivider()
            PrefRow { RowText(null, "Encrypted on this device with the Android Keystore. Folders are created as needed.") }
        }

        GroupTitle("File names")
        PathPattern(form.path, { form.path = it }, RemotePaths.preview(form.path.ifBlank { "file.png" }, "IMG_4503.png", "image/png"))

        GroupTitle("Links")
        Group {
            Field("Public URL base", "(optional)") {
                SdTextField(form.publicUrlBase, { form.publicUrlBase = it }, placeholder = "https://files.example.com", mono = true)
            }
            RowDivider()
            PrefRow {
                RowText(
                    null,
                    if (form.publicUrlBase.isBlank()) "With no public address the link is the WebDAV address, which needs your login to open."
                    else "Where the same folder can be opened by whoever you send the link to.",
                )
            }
            PreviewLine("Your link will look like", form.exampleLink())
        }

        FormActions(form.testing, form.result, form.resultOk, vm::testWebDav, vm::saveWebDav, editing != null) { editing?.let(vm::deleteDestination) }
    }
}

@Composable
fun FtpFormScreen(vm: MainViewModel, form: FtpForm) {
    val editing = form.editing
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp).navigationBarsPadding(),
    ) {
        GroupTitle("Server")
        Group {
            Field("Name") { SdTextField(form.name, { form.name = it }, placeholder = "My server", plainText = true) }
            RowDivider()
            Field("Host") { SdTextField(form.host, { form.host = it }, placeholder = "ftp.example.com", mono = true) }
            RowDivider()
            Field("Port") { SdTextField(form.port, { form.port = it.filter(Char::isDigit).take(5) }, mono = true) }
            RowDivider()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowText(
                    "Encryption",
                    when (form.security) {
                        FtpSecurity.NONE -> "Not encrypted. The password is sent in the clear, so only use this on a network you trust."
                        FtpSecurity.EXPLICIT -> "Encrypted (FTPS). Port 21 on most servers."
                        FtpSecurity.IMPLICIT -> "Encrypted from the first byte (FTPS). Port 990 on most servers."
                    },
                )
                Segmented(
                    listOf(FtpSecurity.NONE to "None", FtpSecurity.EXPLICIT to "Explicit TLS", FtpSecurity.IMPLICIT to "Implicit TLS"),
                    form.security, { vm.ftpSecurityChanged(form, it) },
                )
            }
            RowDivider()
            SwitchRow("Passive mode", "Leave on unless your server needs active mode. Works behind most routers.", form.passive) { form.passive = it }
        }

        GroupTitle("Account")
        Group {
            Field("Username") { SdTextField(form.username, { form.username = it }) }
            RowDivider()
            Field("Password") { SdTextField(form.password, { form.password = it }, password = true) }
            RowDivider()
            PrefRow { RowText(null, "Encrypted on this device with the Android Keystore.") }
        }

        GroupTitle("File names")
        PathPattern(form.path, { form.path = it }, RemotePaths.preview(form.path.ifBlank { "file.png" }, "IMG_4503.png", "image/png"))

        GroupTitle("Links")
        Group {
            Field("Public URL base") {
                SdTextField(form.publicUrlBase, { form.publicUrlBase = it }, placeholder = "https://files.example.com", mono = true)
            }
            RowDivider()
            PrefRow { RowText(null, "Where the uploaded folder can be opened over the web. FTP has no links of its own.") }
            PreviewLine("Your link will look like", form.exampleLink())
        }

        FormActions(form.testing, form.result, form.resultOk, vm::testFtp, vm::saveFtp, editing != null) { editing?.let(vm::deleteDestination) }
    }
}
