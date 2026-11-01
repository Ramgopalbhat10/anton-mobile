package `in`.mrgb.anton.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.util.Base64
import android.util.LruCache
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.mrgb.anton.data.api.AntonApi
import `in`.mrgb.anton.data.api.Attachments
import `in`.mrgb.anton.ui.screens.mdColors
import `in`.mrgb.anton.ui.screens.mdPadding
import `in`.mrgb.anton.ui.screens.mdType
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

/**
 * Images without an image library: authenticated bytes from OkHttp, decoded
 * at the size they're shown and kept in a small LRU. Data URLs (the images a
 * message carries itself) decode in place.
 */
class ImageStore(private val api: AntonApi) {
    private val cache = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(url: String, maxPx: Int): ImageBitmap? = cache.get("$maxPx@$url")

    /** Raw bytes of any file — the artifact viewer's text/html/image bodies. */
    suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            if (url.startsWith("data:")) Base64.decode(url.substringAfter(','), Base64.DEFAULT) else api.bytes(url)
        }.getOrNull()
    }

    /** The file's absolute web URL — the artifact viewer's "Open in web". */
    fun absolute(url: String): String = if (url.startsWith("http") || url.startsWith("data:")) url else api.url(url)

    suspend fun load(url: String, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get("$maxPx@$url")?.let { return@withContext it }
        val bytes = if (url.startsWith("data:")) Base64.decode(url.substringAfter(','), Base64.DEFAULT) else api.bytes(url)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        bmp.asImageBitmap().also { cache.put("$maxPx@$url", it) }
    }
}

val LocalImages = staticCompositionLocalOf<ImageStore?> { null }

/**
 * Where a transcript's files are served: a task's Library (outputs and the
 * files you attached under uploads/) or its project's Library.
 */
data class FilePlace(val sessionId: String? = null, val spaceId: String? = null) {
    private fun enc(path: String) = URLEncoder.encode(path, "UTF-8").replace("+", "%20")
    fun output(path: String): String? = sessionId?.let { "/api/sessions/$it/output?path=${enc(path)}" }
    fun attachment(ref: Attachments.Ref): String? =
        if (ref.project) spaceId?.let { "/api/spaces/$it/file?path=${enc(ref.path)}" } else output(ref.path)
}

val LocalFilePlace = compositionLocalOf { FilePlace() }

/** A file a reply made or a message carried — one item of the artifact slider. */
data class Artifact(val name: String, val url: String) {
    enum class Kind { Image, Html, Markdown, Text, Other }

    val kind: Kind
        get() = when (name.substringAfterLast('.', "").lowercase()) {
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> Kind.Image
            "html", "htm", "svg" -> Kind.Html
            "md", "markdown" -> Kind.Markdown
            "txt", "log", "json", "xml", "yaml", "yml", "toml", "csv", "patch", "diff",
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "css",
            "sh", "bash", "c", "cc", "cpp", "h", "hpp", "go", "rs", "rb", "php", "sql",
            "ini", "cfg", "conf", "env", "gradle", "properties" -> Kind.Text
            else -> Kind.Other
        }
}

/** Opens the artifact slider on a group of files, wherever a file is tapped. */
val LocalViewer = staticCompositionLocalOf<((items: List<Artifact>, index: Int) -> Unit)?> { null }

/** Provides the image store and hosts the one viewer the app shows. */
@Composable
fun MediaHost(images: ImageStore?, content: @Composable () -> Unit) {
    var viewing by remember { mutableStateOf<Pair<List<Artifact>, Int>?>(null) }
    CompositionLocalProvider(LocalImages provides images, LocalViewer provides { items, index -> viewing = items to index }) {
        content()
        viewing?.let { (items, index) -> ArtifactViewer(items, index) { viewing = null } }
    }
}

/** An image from the server or a data URL; a quiet tile while it loads, a broken-image mark if it can't. */
// AGP 8.7/Kotlin 2 lint misresolves keyed produceState despite the value assignment below.
// Remove after a toolchain upgrade: https://issuetracker.google.com/issues/368420773
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    maxPx: Int = 720,
) {
    val store = LocalImages.current
    val state by produceState<Any?>(store?.cached(url, maxPx), url, maxPx, store) {
        value = store?.cached(url, maxPx)
            ?: store?.let { runCatching { it.load(url, maxPx) }.getOrNull() }
            ?: false
    }
    when (val s = state) {
        is ImageBitmap -> Image(s, contentDescription, modifier, contentScale = contentScale)
        else -> Box(modifier.background(Anton.Raised), contentAlignment = Alignment.Center) {
            if (s == false) Icon(Lucide.Image, null, tint = Anton.IconDisabled, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The artifact slider: a right-side panel over the app — file name left, ‹ N of M ›
 * navigation, a copy or download action by type, and close, over the file
 * itself rendered (markdown, html, images, text) or offered as open-in-web
 * when there's no preview for its kind.
 */
@Composable
fun ArtifactViewer(items: List<Artifact>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
            ArtifactPanel(items, start, onDismiss, Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 540.dp).fillMaxWidth())
        }
    }
}

/** The slider's panel — apart from the dialog so snapshots can render it. */
@Composable
fun ArtifactPanel(items: List<Artifact>, start: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(start.coerceIn(0, items.lastIndex)) }
    val a = items[index]
    val store = LocalImages.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val body by produceState<Any?>(null, a.url, store) {
        if (value == null) value = store?.fetch(a.url) ?: false
    }
    Column(modifier.background(Anton.Canvas)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.FileText, null, tint = Anton.IconSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(a.name.substringAfterLast('/'), style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (items.size > 1) {
                GhostIconButton(Lucide.ChevronLeft, "Previous", tint = if (index > 0) Anton.IconSecondary else Anton.IconDisabled, iconSize = 18.dp) { if (index > 0) index-- }
                Text("${index + 1} of ${items.size}", style = Type.Meta, color = Anton.TextTertiary)
                GhostIconButton(Lucide.ChevronRight, "Next", tint = if (index < items.lastIndex) Anton.IconSecondary else Anton.IconDisabled, iconSize = 18.dp) { if (index < items.lastIndex) index++ }
            }
            when (a.kind) {
                Artifact.Kind.Image, Artifact.Kind.Other ->
                    GhostIconButton(Lucide.Download, "Download", tint = Anton.IconSecondary, iconSize = 18.dp) { download(context, scope, store, a) }
                else ->
                    GhostIconButton(Lucide.Copy, "Copy", tint = Anton.IconSecondary, iconSize = 18.dp) {
                        (body as? ByteArray)?.let { clipboard.setText(AnnotatedString(String(it))) ; toast(context, "Copied") }
                    }
            }
            GhostIconButton(Lucide.X, "Close", tint = Anton.IconSecondary, iconSize = 20.dp, onClick = onDismiss)
        }
        Box(Modifier.weight(1f).fillMaxWidth().hairlineTop(Anton.BorderSubtle)) {
            when {
                body == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(20.dp) }
                body == false -> ArtifactFallback(store, a, context, scope, error = true)
                else -> when (a.kind) {
                    Artifact.Kind.Image -> ZoomedImage(a.url, a.name)
                    Artifact.Kind.Html -> HtmlView(String(body as ByteArray))
                    Artifact.Kind.Markdown -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Markdown(String(body as ByteArray), colors = mdColors(), typography = mdType(), padding = mdPadding())
                    }
                    Artifact.Kind.Text -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(String(body as ByteArray), style = Type.Code.copy(fontSize = 12.5.sp, lineHeight = 19.sp), color = Anton.TextSecondary, softWrap = false)
                    }
                    Artifact.Kind.Other -> ArtifactFallback(store, a, context, scope, error = false)
                }
            }
        }
    }
}

/** An image in the slider: pinch or double-tap to zoom, drag to pan. */
@Composable
private fun ZoomedImage(url: String, name: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val shown by animateFloatAsState(scale, label = "zoom")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RemoteImage(
            url, name, maxPx = 2048,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                }
                .graphicsLayer(scaleX = shown, scaleY = shown, translationX = offset.x, translationY = offset.y),
        )
    }
}

/** Html artifacts render in a real web view, scripts on, links sent to the browser. */
@Composable
private fun HtmlView(html: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    }
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}

/** The file the slider can't preview: an offer to open it on the web or save it. */
@Composable
private fun ArtifactFallback(store: ImageStore?, a: Artifact, context: Context, scope: kotlinx.coroutines.CoroutineScope, error: Boolean) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Lucide.File, null, tint = Anton.IconDisabled, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(12.dp))
        Text(a.name.substringAfterLast('/'), style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            if (error) "This file couldn't be loaded." else "There's no in-app preview for this file type.",
            style = Type.Small, color = Anton.TextTertiary,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            store?.let { s ->
                AntonButton("Open in web", variant = ButtonVariant.Outline, size = ButtonSize.Sm, icon = Lucide.ExternalLink,
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.absolute(a.url)))) })
            }
            AntonButton("Download", variant = ButtonVariant.Outline, size = ButtonSize.Sm, icon = Lucide.Download,
                onClick = { download(context, scope, store, a) })
        }
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun download(context: Context, scope: kotlinx.coroutines.CoroutineScope, store: ImageStore?, a: Artifact) {
    scope.launch {
        val data = store?.fetch(a.url)
        if (data == null) { toast(context, "Couldn't download the file"); return@launch }
        val name = a.name.substringAfterLast('/')
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?.also { uri -> context.contentResolver.openOutputStream(uri)?.use { it.write(data) } }
                    ?: error("no uri")
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name).writeBytes(data)
            }
        }.onSuccess { toast(context, "Saved to Downloads") }
            .onFailure { toast(context, "Couldn't save the file") }
    }
}

/**
 * The web's FileChip: a small raised tile with the file's mark (a thumbnail
 * for an image), its name and "kind · size". Tapping opens the artifact slider.
 */
@Composable
fun FileChip(name: String, note: String?, imageUrl: String? = null, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val viewer = LocalViewer.current
    val open = onOpen ?: if (imageUrl != null && viewer != null) { { viewer(listOf(Artifact(name, imageUrl)), 0) } } else null
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier.widthIn(max = 260.dp).clip(shape).background(Anton.Raised).border(1.dp, Anton.BorderSubtle, shape)
            .then(if (open != null) Modifier.clickable { open() } else Modifier)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val tile = RoundedCornerShape(6.dp)
        Box(Modifier.size(28.dp).clip(tile).background(Anton.Overlay).border(1.dp, Anton.BorderSubtle, tile), contentAlignment = Alignment.Center) {
            if (imageUrl != null) RemoteImage(imageUrl, null, Modifier.fillMaxSize(), ContentScale.Crop, maxPx = 96)
            else Icon(Lucide.FileText, null, tint = Anton.IconTertiary, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(name, style = Type.SmallMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            note?.let { Text(it, style = Type.Meta.copy(fontSize = 11.5.sp), color = Anton.TextTertiary, maxLines = 1) }
        }
    }
}

/** The files a sent message carried, as chips under it (src/web/components/attachments.tsx). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentAttachments(files: List<Attachments.Ref>, modifier: Modifier = Modifier) {
    if (files.isEmpty()) return
    val place = LocalFilePlace.current
    val viewer = LocalViewer.current
    val artifacts = files.map { f -> place.attachment(f)?.let { Artifact(f.name, it) } }
    val openable = artifacts.filterNotNull()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEachIndexed { i, f ->
            FileChip(
                f.name, "${f.kind} · ${Attachments.size(f.size)}", imageUrl = if (f.image) artifacts[i]?.url else null,
                onOpen = artifacts[i]?.let { a -> { viewer?.invoke(openable, openable.indexOf(a)) } },
            )
        }
    }
}

/** An image card that opens the viewer: a screenshot, an image a reply made or one you sent. */
@Composable
fun ImageCard(url: String, name: String, modifier: Modifier = Modifier, height: Dp = 150.dp, caption: Boolean = true) {
    val viewer = LocalViewer.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.width(IntrinsicSize.Max).widthIn(max = 280.dp).clip(shape).background(Anton.CardBg).border(1.dp, Anton.CardBorder, shape)
            .then(if (viewer != null) Modifier.clickable { viewer(listOf(Artifact(name, url)), 0) } else Modifier),
    ) {
        RemoteImage(url, name, Modifier.widthIn(min = 120.dp, max = 280.dp).height(height).fillMaxWidth(), ContentScale.Crop)
        if (caption) {
            Row(
                Modifier.fillMaxWidth().hairlineTop().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Lucide.Image, null, tint = Anton.IconTertiary, modifier = Modifier.size(13.dp))
                Text(name.substringAfterLast('/'), style = Type.Meta, color = Anton.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Lucide.Maximize2, null, tint = Anton.IconDisabled, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** What a reply made for you, under its words, as file chips. */
@Composable
fun ReplyOutputs(paths: List<String>) {
    val place = LocalFilePlace.current
    val shown = paths.mapNotNull { p -> place.output(p)?.let { p to it } }
    if (shown.isEmpty()) return
    ReplyFiles(shown.map { (path, url) -> Triple(path, url, Attachments.isImagePath(path)) })
}

/** Files a reply made, as the same small chips a sent message's attachments use; an image chip opens the viewer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyFiles(files: List<Triple<String, String, Boolean>>) {
    val viewer = LocalViewer.current
    val artifacts = files.map { (path, url) -> Artifact(path.substringAfterLast('/'), url) }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEachIndexed { i, (path, url, image) ->
            val folder = path.substringBeforeLast('/', "").ifBlank { null }
            val ext = path.substringAfterLast('.', "").uppercase().ifBlank { null }
            FileChip(
                path.substringAfterLast('/'),
                listOfNotNull(if (image) "${ext ?: "Image"} image" else ext, folder ?: "Library").joinToString(" · "),
                imageUrl = if (image) url else null,
                onOpen = { viewer?.invoke(artifacts, i) },
            )
        }
    }
}
