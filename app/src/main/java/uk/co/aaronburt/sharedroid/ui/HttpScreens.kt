package uk.co.aaronburt.sharedroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.aaronburt.sharedroid.HttpForm
import uk.co.aaronburt.sharedroid.MainViewModel
import uk.co.aaronburt.sharedroid.http.BodyType

@Composable
fun HttpFormScreen(vm: MainViewModel, form: HttpForm) {
    val editing = form.editing
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp).navigationBarsPadding(),
    ) {
        form.note?.let { note ->
            Group(Modifier.padding(top = 4.dp)) { PrefRow { RowText("About this service", note) } }
        }

        GroupTitle("Destination")
        Group {
            Field("Name") { SdTextField(form.name, { form.name = it }, placeholder = "My uploader", plainText = true) }
        }

        GroupTitle("Request")
        Group {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowText("Method")
                Segmented(listOf("POST" to "POST", "PUT" to "PUT"), form.method, { form.method = it })
            }
            RowDivider()
            Field("URL") { SdTextField(form.url, { form.url = it }, placeholder = "https://example.com/upload", mono = true) }
            RowDivider()
            Field("Headers", "(one per line, Name: value)") {
                SdTextField(form.headers, { form.headers = it }, placeholder = "Authorization: Bearer your-token", mono = true, lines = 3)
            }
            RowDivider()
            Field("Query parameters", "(one per line, name=value)") {
                SdTextField(form.query, { form.query = it }, placeholder = "folder=screenshots", mono = true, lines = 2)
            }
            RowDivider()
            PrefRow { RowText(null, "Everything on this screen, headers included, is encrypted on this device with the Android Keystore.") }
        }

        GroupTitle("Body")
        Group {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowText(
                    "Send the file as",
                    if (form.bodyType == BodyType.MULTIPART) "A form, like a browser upload" else "The file itself, with nothing around it",
                )
                Segmented(listOf(BodyType.MULTIPART to "Form", BodyType.BINARY to "Raw file"), form.bodyType, { form.bodyType = it })
            }
            if (form.bodyType == BodyType.MULTIPART) {
                RowDivider()
                Field("File field name") { SdTextField(form.fileField, { form.fileField = it }, placeholder = "file", mono = true) }
                RowDivider()
                Field("Extra form fields", "(one per line, name=value)") {
                    SdTextField(form.fields, { form.fields = it }, placeholder = "title={filename}", mono = true, lines = 3)
                }
            }
        }

        GroupTitle("Reply")
        Group {
            Field("Link from reply") { SdTextField(form.link, { form.link = it }, placeholder = "\$json:url\$", mono = true) }
            RowDivider()
            Field("Deletion link", "(optional)") { SdTextField(form.deletion, { form.deletion = it }, placeholder = "\$json:delete_url\$", mono = true) }
            RowDivider()
            PrefRow {
                RowText(
                    null,
                    "Reply tokens: \$json:data.link\$  \$header:Location\$  \$regex:id=(\\w+)|1\$  \$response\$\n" +
                        "Request tokens: {filename} {name} {ext} {mime} {uuid} {timestamp} {random:8}",
                )
            }
        }

        Row(Modifier.padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SdButton(if (form.testing) "Testing…" else "Test upload", Modifier.weight(1f), secondary = true, enabled = !form.testing, onClick = vm::testHttp)
            SdButton("Save", Modifier.weight(1f), onClick = vm::saveHttp)
        }
        Text(
            form.result.orEmpty(),
            Modifier.padding(horizontal = 6.dp).padding(bottom = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = if (form.resultOk) Sd.colors.ok else Sd.colors.warn, fontSize = 13.sp, lineHeight = 18.sp,
        )

        if (editing != null) {
            Text(
                if (confirmDelete) "Tap again to remove. Files already uploaded stay where they are." else "Remove this destination",
                Modifier.fillMaxWidth().clip(CircleShape)
                    .clickable(role = Role.Button) { if (confirmDelete) vm.deleteDestination(editing) else confirmDelete = true }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                color = Sd.colors.warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
