package uk.co.aaronburt.sharedroid

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import uk.co.aaronburt.sharedroid.data.AppSettings
import uk.co.aaronburt.sharedroid.data.Destination
import uk.co.aaronburt.sharedroid.data.LinkType
import uk.co.aaronburt.sharedroid.data.Providers
import uk.co.aaronburt.sharedroid.data.Upload
import uk.co.aaronburt.sharedroid.ftp.FtpConfig
import uk.co.aaronburt.sharedroid.ftp.FtpSecurity
import uk.co.aaronburt.sharedroid.http.BodyType
import uk.co.aaronburt.sharedroid.http.HttpConfig
import uk.co.aaronburt.sharedroid.http.HttpPreset
import uk.co.aaronburt.sharedroid.http.HttpPresets
import uk.co.aaronburt.sharedroid.http.RemotePaths
import uk.co.aaronburt.sharedroid.http.WebDavConfig
import uk.co.aaronburt.sharedroid.s3.KeyTemplate
import uk.co.aaronburt.sharedroid.s3.S3Config
import uk.co.aaronburt.sharedroid.upload.Clipboard
import uk.co.aaronburt.sharedroid.upload.ErrorText
import uk.co.aaronburt.sharedroid.upload.UploadActions
import uk.co.aaronburt.sharedroid.upload.UploadSource

enum class Screen { Home, Settings, Providers, Form, HttpForm, WebDavForm, FtpForm }

/** Everything typed into the add / edit destination form. */
class DestinationForm(val editing: Destination?, val provider: String) {
    var name by mutableStateOf(editing?.name ?: "")
    var endpoint by mutableStateOf(editing?.let { (if (it.useHttps) "" else "http://") + it.endpoint } ?: "")
    var region by mutableStateOf(editing?.region ?: "")
    var bucket by mutableStateOf(editing?.bucket ?: "")
    var pathStyle by mutableStateOf(editing?.pathStyle ?: false)
    var accessKey by mutableStateOf("")
    var secretKey by mutableStateOf("")
    var keyTemplate by mutableStateOf(editing?.keyTemplate ?: DEFAULT_TEMPLATE)
    var publicUrlBase by mutableStateOf(editing?.publicUrlBase ?: "")
    var linkType by mutableStateOf(editing?.linkType ?: LinkType.PUBLIC)
    var cacheControl by mutableStateOf(editing?.cacheControl ?: "")
    var storageClass by mutableStateOf(editing?.storageClass ?: "")
    var publicAcl by mutableStateOf(editing?.publicAcl ?: false)
    var stripLocation by mutableStateOf(editing?.stripLocation ?: true)

    var testing by mutableStateOf(false)
    var resultOk by mutableStateOf(true)
    var result by mutableStateOf<String?>(null)
    /** Null until a test has shown whether this key may delete. */
    var canDelete: Boolean? = null

    /** Fixed for the life of the form so the example key doesn't change on every keystroke. */
    private val sampleRandom = (1..64).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")

    /** Scheme-less host, and whether the user asked for plain HTTP (a local MinIO, say). */
    val host: String get() = endpoint.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
    val useHttps: Boolean get() = !endpoint.trim().startsWith("http://")

    fun exampleKey(): String {
        val fixed = Regex("\\{random(?::(\\d+))?\\}").replace(keyTemplate) { m ->
            sampleRandom.take((m.groupValues[1].toIntOrNull() ?: 8).coerceIn(1, 64))
        }.replace("{uuid}", "3f9a1c2e-77b4-4e0b-9d1a-5c2f8e6a1b03")
        return KeyTemplate.render(fixed, "IMG_4503.png").ifEmpty { "file.png" }
    }

    fun exampleUrl(): String {
        val key = exampleKey()
        val config = S3Config(host.ifEmpty { "endpoint" }, region, bucket.trim().ifEmpty { "bucket" }, "", "", pathStyle, useHttps)
        return if (linkType == LinkType.SIGNED) {
            "${config.objectUrl(key)}?X-Amz-Expires=${LinkType.SIGNED_EXPIRY_SECONDS}&X-Amz-Signature=…"
        } else {
            val base = publicUrlBase.trim().takeIf { it.isNotEmpty() }?.let { if ("://" in it) it else "https://$it" }
            config.copy(publicUrlBase = base).publicUrl(key)
        }
    }

    companion object {
        const val DEFAULT_TEMPLATE = "{yyyy}/{MM}/{dd}/{random:8}.{ext}"
        val TOKENS = listOf("{yyyy}", "{MM}", "{dd}", "{random:8}", "{filename}", "{uuid}", "{timestamp}", ".{ext}")
    }
}

/** Everything typed into the custom HTTP destination form. */
class HttpForm(val editing: Destination?, config: HttpConfig?, presetName: String? = null, val note: String? = null) {
    var name by mutableStateOf(editing?.name ?: presetName ?: "")
    var method by mutableStateOf(config?.method ?: "POST")
    var url by mutableStateOf(config?.url ?: "")
    var query by mutableStateOf(config?.let { HttpConfig.formatPairs(it.query, '=') } ?: "")
    var headers by mutableStateOf(config?.let { HttpConfig.formatPairs(it.headers, ':') } ?: "")
    var bodyType by mutableStateOf(config?.bodyType ?: BodyType.MULTIPART)
    var fileField by mutableStateOf(config?.fileField ?: "file")
    var fields by mutableStateOf(config?.let { HttpConfig.formatPairs(it.fields, '=') } ?: "")
    var link by mutableStateOf(config?.linkTemplate ?: HttpConfig.DEFAULT_LINK)
    var deletion by mutableStateOf(config?.deletionTemplate ?: "")

    var testing by mutableStateOf(false)
    var resultOk by mutableStateOf(true)
    var result by mutableStateOf<String?>(null)

    /** The host part of the URL, shown in lists. */
    val host: String get() = url.trim().substringAfter("://").substringBefore('/').substringBefore('?')

    fun toConfig() = HttpConfig(
        method = method,
        url = url.trim(),
        query = HttpConfig.parsePairs(query, '='),
        headers = HttpConfig.parsePairs(headers, ':'),
        bodyType = bodyType,
        fileField = fileField.trim().ifEmpty { "file" },
        fields = HttpConfig.parsePairs(fields, '='),
        linkTemplate = link.trim(),
        deletionTemplate = deletion.trim(),
    )
}

private fun withScheme(url: String): String = url.trim().let { if (it.isEmpty() || "://" in it) it else "https://$it" }

/** Everything typed into the WebDAV destination form. */
class WebDavForm(val editing: Destination?, config: WebDavConfig?) {
    var name by mutableStateOf(editing?.name ?: "")
    var url by mutableStateOf(config?.url ?: "")
    var username by mutableStateOf(config?.username ?: "")
    var password by mutableStateOf(config?.password ?: "")
    var path by mutableStateOf(config?.pathTemplate ?: WebDavConfig.DEFAULT_PATH)
    var publicUrlBase by mutableStateOf(config?.publicUrlBase ?: "")

    var testing by mutableStateOf(false)
    var resultOk by mutableStateOf(true)
    var result by mutableStateOf<String?>(null)

    val host: String get() = url.trim().substringAfter("://").substringBefore('/')

    fun toConfig() = WebDavConfig(
        url = url.trim(),
        username = username.trim(),
        password = password,
        pathTemplate = path.trim().ifEmpty { WebDavConfig.DEFAULT_PATH },
        publicUrlBase = withScheme(publicUrlBase),
    )

    fun exampleLink(): String {
        val config = toConfig()
        return config.linkFor(RemotePaths.preview(config.pathTemplate, "IMG_4503.png", "image/png"))
    }
}

/** Everything typed into the FTP destination form. */
class FtpForm(val editing: Destination?, config: FtpConfig?) {
    var name by mutableStateOf(editing?.name ?: "")
    var host by mutableStateOf(config?.host ?: "")
    var port by mutableStateOf((config?.port ?: FtpConfig.defaultPort(FtpSecurity.EXPLICIT)).toString())
    var security by mutableStateOf(config?.security ?: FtpSecurity.EXPLICIT)
    var passive by mutableStateOf(config?.passive ?: true)
    var username by mutableStateOf(config?.username ?: "")
    var password by mutableStateOf(config?.password ?: "")
    var path by mutableStateOf(config?.pathTemplate ?: FtpConfig.DEFAULT_PATH)
    var publicUrlBase by mutableStateOf(config?.publicUrlBase ?: "")

    var testing by mutableStateOf(false)
    var resultOk by mutableStateOf(true)
    var result by mutableStateOf<String?>(null)

    fun toConfig() = FtpConfig(
        host = host.trim().removePrefix("ftp://").removePrefix("ftps://").trimEnd('/'),
        port = port.toIntOrNull() ?: FtpConfig.defaultPort(security),
        security = security,
        username = username.trim(),
        password = password,
        passive = passive,
        pathTemplate = path.trim().ifEmpty { FtpConfig.DEFAULT_PATH },
        publicUrlBase = withScheme(publicUrlBase),
    )

    fun exampleLink(): String {
        val config = toConfig()
        return if (config.publicUrlBase.isEmpty()) "Enter the public URL base"
        else config.linkFor(RemotePaths.preview(config.pathTemplate, "IMG_4503.png", "image/png"))
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val context get() = container.context

    val settings: StateFlow<AppSettings?> = container.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val destinations: StateFlow<List<Destination>> =
        container.db.destinations().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val uploads: StateFlow<List<Upload>?> = container.db.uploads().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val active: StateFlow<Destination?> = combine(destinations, container.settings.flow) { all, s ->
        all.firstOrNull { it.id == s.activeDestinationId } ?: all.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- Navigation ----
    private val stack = mutableStateListOf(Screen.Home)
    val screen: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    var form by mutableStateOf<DestinationForm?>(null)
        private set

    var httpForm by mutableStateOf<HttpForm?>(null)
        private set

    var webDavForm by mutableStateOf<WebDavForm?>(null)
        private set

    var ftpForm by mutableStateOf<FtpForm?>(null)
        private set

    fun showTab(tab: Screen) {
        stack.clear()
        stack += Screen.Home
        if (tab != Screen.Home) stack += tab
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun addDestination() {
        if (screen != Screen.Settings) showTab(Screen.Settings)
        stack += Screen.Providers
    }

    fun chooseS3() {
        form = DestinationForm(null, Providers.S3_COMPATIBLE)
        stack += Screen.Form
    }

    fun chooseWebDav() {
        webDavForm = WebDavForm(null, null)
        stack += Screen.WebDavForm
    }

    fun chooseFtp() {
        ftpForm = FtpForm(null, null)
        stack += Screen.FtpForm
    }

    /** Moves the port between the usual defaults when the encryption mode changes, unless it was customised. */
    fun ftpSecurityChanged(form: FtpForm, security: FtpSecurity) {
        val old = FtpConfig.defaultPort(form.security)
        if (form.port.toIntOrNull() == old || form.port.isBlank()) form.port = FtpConfig.defaultPort(security).toString()
        form.security = security
    }

    /** Starts the form blank, or filled in from a known service. */
    fun chooseHttp(preset: HttpPreset?) {
        httpForm = HttpForm(null, preset?.config, preset?.name, preset?.note)
        stack += Screen.HttpForm
    }

    fun editDestination(destination: Destination) {
        val json = destination.httpJson?.let { runCatching { container.secrets.decrypt(it) }.getOrNull() }
        if (destination.provider == Providers.WEBDAV) {
            webDavForm = WebDavForm(destination, json?.let { runCatching { WebDavConfig.fromJson(it) }.getOrNull() })
            stack += Screen.WebDavForm
        } else if (destination.provider == Providers.FTP) {
            ftpForm = FtpForm(destination, json?.let { runCatching { FtpConfig.fromJson(it) }.getOrNull() })
            stack += Screen.FtpForm
        } else if (destination.provider == Providers.HTTP) {
            val config = runCatching { HttpConfig.fromJson(container.secrets.decrypt(destination.httpJson.orEmpty())) }.getOrNull()
            httpForm = HttpForm(destination, config)
            stack += Screen.HttpForm
        } else {
            form = DestinationForm(destination, destination.provider)
            stack += Screen.Form
        }
    }

    // ---- Toast ----
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null

    fun toast(message: String) {
        toastJob?.cancel()
        toast = message
        toastJob = viewModelScope.launch {
            delay(1800)
            toast = null
        }
    }

    // ---- Settings ----
    fun update(block: suspend uk.co.aaronburt.sharedroid.data.SettingsStore.() -> Unit) {
        viewModelScope.launch { container.settings.block() }
    }

    fun cycleDestination() {
        val all = destinations.value
        if (all.size < 2) {
            active.value?.let { toast("Uploading to ${it.name}") }
            return
        }
        val next = all[(all.indexOfFirst { it.id == active.value?.id } + 1) % all.size]
        update { setActiveDestination(next.id) }
        toast("Uploading to ${next.name}")
    }

    // ---- Uploading ----
    /** False, after sending the user to add one, when there is nowhere to upload to yet. */
    fun readyToUpload(): Boolean {
        if (destinations.value.isNotEmpty()) return true
        addDestination()
        toast("Add a destination first")
        return false
    }

    fun upload(sources: List<UploadSource>) {
        val destination = active.value ?: return
        val accepted = container.stager.stage(sources, destination)
        showTab(Screen.Home)
        toast(if (accepted == 0) "Couldn't read that file" else "Uploading to ${destination.name}")
    }

    fun pasteFromClipboard() {
        if (!readyToUpload()) return
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val uri = item?.uri
        val text = item?.coerceToText(context)?.toString()
        when {
            uri != null -> upload(listOf(UploadSource.Content(uri)))
            !text.isNullOrBlank() -> upload(listOf(UploadSource.Text(text)))
            else -> toast("The clipboard is empty")
        }
    }

    // ---- History ----
    var rowSheet by mutableStateOf<Upload?>(null)
    var deleting by mutableStateOf(false)
        private set

    fun copyLink(upload: Upload) {
        val url = upload.url ?: return
        toast(if (Clipboard.copy(context, url)) "Link copied" else "Couldn't copy the link")
    }

    fun retry(upload: Upload) {
        UploadActions.retry(context, upload.id)
        toast("Retrying upload")
    }

    fun removeFromHistory(upload: Upload) {
        UploadActions.remove(context, upload.id)
        rowSheet = null
        toast("Removed from history")
    }

    fun deleteFromBucket(upload: Upload) {
        if (deleting) return
        deleting = true
        viewModelScope.launch {
            val result = UploadActions.deleteFromBucket(context, upload)
            deleting = false
            rowSheet = null
            toast(
                when (result) {
                    UploadActions.DeleteResult.Deleted -> "Deleted from ${upload.destinationName}"
                    UploadActions.DeleteResult.NotAllowed -> "This key can't delete files"
                    is UploadActions.DeleteResult.Failed -> "Couldn't delete: ${result.reason}"
                },
            )
        }
    }

    // ---- Destination form ----
    /** The first thing wrong with the form, or null. */
    private fun problem(form: DestinationForm): String? = when {
        form.host.isEmpty() -> "Fill in the endpoint."
        form.bucket.isBlank() -> "Fill in the bucket."
        form.region.isBlank() -> "Fill in the region. Use \"auto\" if your provider doesn't have one."
        form.editing == null && (form.accessKey.isBlank() || form.secretKey.isBlank()) -> "Fill in both keys."
        else -> null
    }

    private fun config(form: DestinationForm): S3Config {
        val old = form.editing
        return S3Config(
            endpoint = form.host,
            region = form.region.trim(),
            bucket = form.bucket.trim(),
            accessKeyId = form.accessKey.trim().ifEmpty { old?.let { container.secrets.decrypt(it.accessKeyEnc) }.orEmpty() },
            secretAccessKey = form.secretKey.trim().ifEmpty { old?.let { container.secrets.decrypt(it.secretKeyEnc) }.orEmpty() },
            pathStyle = form.pathStyle,
            useHttps = form.useHttps,
        )
    }

    fun testConnection() {
        val form = form ?: return
        problem(form)?.let {
            form.resultOk = false
            form.result = it
            return
        }
        form.testing = true
        form.result = null
        viewModelScope.launch {
            try {
                val config = config(form)
                val outcome = runInterruptible(Dispatchers.IO) { container.s3.testConnection(config) }
                form.canDelete = outcome.canDelete
                form.resultOk = true
                form.result = if (outcome.canDelete) "Connected. Uploaded and deleted a test file."
                else "Connected. Uploads work, but this key can't delete files."
            } catch (e: Exception) {
                form.resultOk = false
                form.result = ErrorText.long(e)
            }
            form.testing = false
        }
    }

    fun save() {
        val form = form ?: return
        problem(form)?.let {
            form.resultOk = false
            form.result = it
            return
        }
        viewModelScope.launch {
            val old = form.editing
            val secrets = container.secrets
            val destination = Destination(
                id = old?.id ?: 0,
                name = form.name.trim().ifEmpty { form.bucket.trim() },
                provider = form.provider,
                endpoint = form.host,
                region = form.region.trim(),
                bucket = form.bucket.trim(),
                pathStyle = form.pathStyle,
                useHttps = form.useHttps,
                accessKeyEnc = form.accessKey.trim().takeIf { it.isNotEmpty() }?.let(secrets::encrypt) ?: old!!.accessKeyEnc,
                secretKeyEnc = form.secretKey.trim().takeIf { it.isNotEmpty() }?.let(secrets::encrypt) ?: old!!.secretKeyEnc,
                keyTemplate = form.keyTemplate.trim().ifEmpty { DestinationForm.DEFAULT_TEMPLATE },
                publicUrlBase = form.publicUrlBase.trim().takeIf { it.isNotEmpty() }?.let { if ("://" in it) it else "https://$it" },
                linkType = form.linkType,
                cacheControl = form.cacheControl.trim().ifEmpty { null },
                storageClass = form.storageClass.trim().ifEmpty { null },
                publicAcl = form.publicAcl,
                stripLocation = form.stripLocation,
                // New keys may have different rights, so an old "can't delete" only survives if the keys did.
                canDelete = form.canDelete ?: (if (form.accessKey.isBlank()) old?.canDelete else null) ?: true,
            )
            val dao = container.db.destinations()
            if (old == null) {
                val id = dao.insert(destination)
                if (destinations.value.isEmpty()) container.settings.setActiveDestination(id)
            } else {
                dao.update(destination)
            }
            toast("Destination saved")
            showTab(Screen.Settings)
        }
    }

    // ---- WebDAV and FTP destination forms ----
    private suspend fun saveRemote(editing: Destination?, name: String, provider: String, host: String, configJson: String) {
        val destination = Destination(
            id = editing?.id ?: 0,
            name = name.trim().ifEmpty { host },
            provider = provider,
            endpoint = host,
            region = "",
            bucket = "",
            pathStyle = false,
            useHttps = true,
            accessKeyEnc = "",
            secretKeyEnc = "",
            keyTemplate = "",
            publicUrlBase = null,
            linkType = LinkType.PUBLIC,
            cacheControl = null,
            storageClass = null,
            publicAcl = false,
            stripLocation = false,
            // The login is inside the config, so the whole thing is encrypted at rest.
            httpJson = container.secrets.encrypt(configJson),
        )
        val dao = container.db.destinations()
        if (editing == null) {
            val id = dao.insert(destination)
            if (destinations.value.isEmpty()) container.settings.setActiveDestination(id)
        } else {
            dao.update(destination)
        }
        toast("Destination saved")
        showTab(Screen.Settings)
    }

    private fun webDavProblem(form: WebDavForm): String? = when {
        !(form.url.trim().startsWith("https://") || form.url.trim().startsWith("http://")) ->
            "Enter the folder's full address, starting with https://"
        else -> null
    }

    private fun ftpProblem(form: FtpForm): String? = when {
        form.host.isBlank() -> "Enter the server's address."
        form.port.toIntOrNull()?.let { it in 1..65535 } != true -> "Enter a port between 1 and 65535."
        form.publicUrlBase.isBlank() -> "Enter the public URL base, where the uploaded files can be opened, so links can be built."
        else -> null
    }

    fun testWebDav() {
        val form = webDavForm ?: return
        webDavProblem(form)?.let {
            form.resultOk = false
            form.result = it
            return
        }
        form.testing = true
        form.result = null
        viewModelScope.launch {
            try {
                val config = form.toConfig()
                val outcome = runInterruptible(Dispatchers.IO) { container.webdav.test(config) }
                form.resultOk = true
                form.result = if (outcome.canDelete) "Connected. Uploaded and deleted a test file."
                else "Connected. Uploads work, but this account can't delete files."
            } catch (e: Exception) {
                form.resultOk = false
                form.result = ErrorText.long(e)
            }
            form.testing = false
        }
    }

    fun saveWebDav() {
        val form = webDavForm ?: return
        webDavProblem(form)?.let {
            form.resultOk = false
            form.result = it
            return
        }
        viewModelScope.launch { saveRemote(form.editing, form.name, Providers.WEBDAV, form.host, form.toConfig().toJson()) }
    }

    fun testFtp() {
        val form = ftpForm ?: return
        ftpProblem(form)?.let {
            form.resultOk = false
            form.result = it
            return
        }
        form.testing = true
        form.result = null
        viewModelScope.launch {
            try {
                val config = form.toConfig()
                val outcome = runInterruptible(Dispatchers.IO) { container.ftp.test(config) }
                form.resultOk = true
                form.result = if (outcome.canDelete) "Connected. Uploaded and deleted a test file."
                else "Connected. Uploads work, but this account can't delete files."
            } catch (e: Exception) {
                form.resultOk = false
                form.result = ErrorText.long(e)
            }
            form.testing = false
        }
    }

    fun saveFtp() {
        val form = ftpForm ?: return
        ftpProblem(form)?.let {
            form.resultOk = false
            form.result = it
            return
        }
        viewModelScope.launch { saveRemote(form.editing, form.name, Providers.FTP, form.toConfig().host, form.toConfig().toJson()) }
    }

    // ---- Custom HTTP destination form ----
    private fun httpProblem(form: HttpForm): String? = when {
        !(form.url.trim().startsWith("https://") || form.url.trim().startsWith("http://")) ->
            "Enter the full URL, starting with https://"
        HttpPresets.hasPlaceholder(form.toConfig()) -> "Replace the ${HttpPresets.PLACEHOLDER_PREFIX}… placeholder with your own value."
        form.link.isBlank() -> "Say where the link is in the reply, for example \$json:url\$."
        else -> null
    }

    fun testHttp() {
        val form = httpForm ?: return
        httpProblem(form)?.let {
            form.resultOk = false
            form.result = it
            return
        }
        form.testing = true
        form.result = null
        viewModelScope.launch {
            try {
                val config = form.toConfig()
                val bytes = "ShareDroid test upload\n".toByteArray()
                val sent = runInterruptible(Dispatchers.IO) {
                    container.http.upload(config, "sharedroid-test.txt", "text/plain", bytes.size.toLong(), { bytes.inputStream() })
                }
                val removed = sent.deletionUrl?.let { link ->
                    runCatching { runInterruptible(Dispatchers.IO) { container.http.delete(link) } }.isSuccess
                } ?: false
                form.resultOk = true
                form.result = "Uploaded a test file. Link: ${sent.url}" + if (removed) ". It was removed again." else ""
            } catch (e: Exception) {
                form.resultOk = false
                form.result = ErrorText.long(e)
            }
            form.testing = false
        }
    }

    fun saveHttp() {
        val form = httpForm ?: return
        httpProblem(form)?.let {
            form.resultOk = false
            form.result = it
            return
        }
        viewModelScope.launch {
            val destination = Destination(
                id = form.editing?.id ?: 0,
                name = form.name.trim().ifEmpty { form.host },
                provider = Providers.HTTP,
                endpoint = form.host,
                region = "",
                bucket = "",
                pathStyle = false,
                useHttps = true,
                accessKeyEnc = "",
                secretKeyEnc = "",
                keyTemplate = "",
                publicUrlBase = null,
                linkType = LinkType.PUBLIC,
                cacheControl = null,
                storageClass = null,
                publicAcl = false,
                stripLocation = false,
                // Headers often hold API tokens, so the whole config is encrypted at rest.
                httpJson = container.secrets.encrypt(form.toConfig().toJson()),
            )
            val dao = container.db.destinations()
            if (form.editing == null) {
                val id = dao.insert(destination)
                if (destinations.value.isEmpty()) container.settings.setActiveDestination(id)
            } else {
                dao.update(destination)
            }
            toast("Destination saved")
            showTab(Screen.Settings)
        }
    }

    fun deleteDestination(destination: Destination) {
        viewModelScope.launch {
            container.db.destinations().delete(destination.id)
            toast("Destination removed")
            showTab(Screen.Settings)
        }
    }
}
