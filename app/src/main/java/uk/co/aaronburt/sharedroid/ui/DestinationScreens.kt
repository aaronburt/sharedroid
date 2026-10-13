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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.aaronburt.sharedroid.DestinationForm
import uk.co.aaronburt.sharedroid.MainViewModel
import uk.co.aaronburt.sharedroid.http.HttpPresets
import uk.co.aaronburt.sharedroid.R
import uk.co.aaronburt.sharedroid.data.LinkType

@Composable
fun ProvidersScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
        Group(Modifier.padding(top = 4.dp)) { TypeRow("S3-compatible", "A bucket on Amazon S3, Cloudflare R2, Backblaze, Wasabi, MinIO and others", vm::chooseS3) }
        GroupTitle("Your own server")
        Group {
            TypeRow("WebDAV", "Nextcloud, ownCloud or any WebDAV folder", vm::chooseWebDav)
            RowDivider()
            TypeRow("FTP / FTPS", "A folder on an FTP server, plain or encrypted", vm::chooseFtp)
        }
        GroupTitle("Upload services")
        Group {
            HttpPresets.all.forEachIndexed { index, preset ->
                if (index > 0) RowDivider()
                TypeRow(preset.name, preset.summary) { vm.chooseHttp(preset) }
            }
        }
        GroupTitle("Something else")
        Group { TypeRow("Custom HTTP", "Start blank: POST or PUT the file to any URL, like a ShareX custom uploader") { vm.chooseHttp(null) } }
    }
}

@Composable
private fun TypeRow(name: String, description: String, onClick: () -> Unit) {
    PrefRow(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Thumb(null, size = 36.dp, letter = name.take(1))
        RowText(name, description, Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(16.dp), tint = Sd.colors.muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DestinationFormScreen(vm: MainViewModel, form: DestinationForm) {
    val editing = form.editing
    val savedHint = if (editing != null) "Saved. Leave blank to keep" else ""
    var advanced by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp).navigationBarsPadding(),
    ) {
        GroupTitle("Connection")
        Group {
            Field("Name") { SdTextField(form.name, { form.name = it }, placeholder = "Personal R2", plainText = true) }
            RowDivider()
            Field("Endpoint") { SdTextField(form.endpoint, { form.endpoint = it }, placeholder = "s3.eu-west-2.amazonaws.com") }
            RowDivider()
            Field("Region") { SdTextField(form.region, { form.region = it }, placeholder = "eu-west-2") }
            RowDivider()
            Field("Bucket") { SdTextField(form.bucket, { form.bucket = it }, placeholder = "my-uploads") }
            RowDivider()
            SwitchRow("Path-style addressing", "Turn on for MinIO and some self-hosted servers. Usually off.", form.pathStyle) { form.pathStyle = it }
        }

        GroupTitle("Credentials")
        Group {
            Field("Access key ID") { SdTextField(form.accessKey, { form.accessKey = it }, placeholder = savedHint) }
            RowDivider()
            Field("Secret access key") { SdTextField(form.secretKey, { form.secretKey = it }, placeholder = savedHint, password = true) }
            RowDivider()
            PrefRow { RowText(null, "Encrypted on this device with the Android Keystore. Never included in exports unless you choose to.") }
        }

        GroupTitle("File names")
        Group {
            Field("Name pattern") { SdTextField(form.keyTemplate, { form.keyTemplate = it }, mono = true) }
            FlowRow(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DestinationForm.TOKENS.forEach { token ->
                    Text(
                        token,
                        Modifier.clip(CircleShape).background(Sd.colors.accentSoft)
                            .clickable(role = Role.Button, onClickLabel = "Add $token") { form.keyTemplate += token }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        color = Sd.colors.accent, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                    )
                }
            }
            Preview("Example", form.exampleKey())
        }

        GroupTitle("Links")
        Group {
            Field("Public URL base", "(optional)") {
                SdTextField(form.publicUrlBase, { form.publicUrlBase = it }, placeholder = "https://i.example.com")
            }
            RowDivider()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowText("Link type", if (form.linkType == LinkType.SIGNED) "Works for 24 hours, for private buckets" else null)
                Segmented(listOf(LinkType.PUBLIC to "Public link", LinkType.SIGNED to "Expiring link"), form.linkType, { form.linkType = it })
            }
            Preview("Your link will look like", form.exampleUrl())
        }

        Group {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { advanced = !advanced }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Advanced", Modifier.weight(1f), color = Sd.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(painterResource(R.drawable.ic_chevron), if (advanced) "Collapse" else "Expand", Modifier.size(16.dp).rotate(if (advanced) -90f else 90f), tint = Sd.colors.muted)
            }
            if (advanced) {
                RowDivider()
                Field("Cache-Control") { SdTextField(form.cacheControl, { form.cacheControl = it }, placeholder = "public, max-age=31536000") }
                RowDivider()
                Field("Storage class") { SdTextField(form.storageClass, { form.storageClass = it }, placeholder = "STANDARD") }
                RowDivider()
                SwitchRow("Make files public (ACL)", "Leave off if your bucket blocks ACLs", form.publicAcl) { form.publicAcl = it }
                RowDivider()
                SwitchRow(
                    "Remove location from photos",
                    "Removes location and camera data from JPEG photos. Videos and other file types upload unchanged.",
                    form.stripLocation,
                ) { form.stripLocation = it }
            }
        }

        Row(Modifier.padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SdButton(if (form.testing) "Testing…" else "Test connection", Modifier.weight(1f), secondary = true, enabled = !form.testing, onClick = vm::testConnection)
            SdButton("Save", Modifier.weight(1f), onClick = vm::save)
        }
        Text(
            form.result.orEmpty(),
            Modifier.padding(horizontal = 6.dp).padding(bottom = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = if (form.resultOk) Sd.colors.ok else Sd.colors.warn, fontSize = 13.sp, lineHeight = 18.sp,
        )

        if (editing != null) {
            Text(
                if (confirmDelete) "Tap again to remove. Files already uploaded stay in the bucket." else "Remove this destination",
                Modifier.fillMaxWidth().clip(CircleShape)
                    .clickable(role = Role.Button) { if (confirmDelete) vm.deleteDestination(editing) else confirmDelete = true }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                color = Sd.colors.warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Preview(label: String, value: String) {
    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), color = Sd.colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        Text(value, color = Sd.colors.ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
    }
}
