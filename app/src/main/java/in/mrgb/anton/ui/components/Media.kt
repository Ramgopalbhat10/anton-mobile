package `in`.mrgb.anton.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.mrgb.anton.data.api.AntonApi
import `in`.mrgb.anton.data.api.Attachments
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** Opens the full-screen image viewer, wherever an image is shown. */
val LocalViewer = staticCompositionLocalOf<((url: String, name: String) -> Unit)?> { null }

/** Provides the image store and hosts the one viewer the app shows. */
@Composable
fun MediaHost(images: ImageStore?, content: @Composable () -> Unit) {
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) }
    CompositionLocalProvider(LocalImages provides images, LocalViewer provides { url, name -> viewing = url to name }) {
        content()
        viewing?.let { (url, name) -> ImageViewer(url, name) { viewing = null } }
    }
}

/** An image from the server or a data URL; a quiet tile while it loads, a broken-image mark if it can't. */
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
        if (value == null) value = store?.let { runCatching { it.load(url, maxPx) }.getOrNull() } ?: false
    }
    when (val s = state) {
        is ImageBitmap -> Image(s, contentDescription, modifier, contentScale = contentScale)
        else -> Box(modifier.background(Anton.Raised), contentAlignment = Alignment.Center) {
            if (s == false) Icon(Lucide.Image, null, tint = Anton.IconDisabled, modifier = Modifier.size(18.dp))
        }
    }
}

/** Full-screen viewer: pinch or double-tap to zoom, drag to pan, tap ✕ or back to close. */
@Composable
fun ImageViewer(url: String, name: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val shown by animateFloatAsState(scale, label = "zoom")
        Box(Modifier.fillMaxSize().background(Color(0xF2000000))) {
            RemoteImage(
                url, name,
                maxPx = 2048,
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
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Image, null, tint = Anton.IconSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(name, style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                GhostIconButton(Lucide.X, "Close", tint = Anton.TextPrimary, size = 40.dp, iconSize = 20.dp, onClick = onDismiss)
            }
        }
    }
}

/**
 * The web's FileChip: a small raised tile with the file's mark (a thumbnail
 * for an image), its name and "kind · size". An image chip opens the viewer.
 */
@Composable
fun FileChip(name: String, note: String?, imageUrl: String? = null, modifier: Modifier = Modifier) {
    val viewer = LocalViewer.current
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier.widthIn(max = 260.dp).clip(shape).background(Anton.Raised).border(1.dp, Anton.BorderSubtle, shape)
            .then(if (imageUrl != null && viewer != null) Modifier.clickable { viewer(imageUrl, name) } else Modifier)
            .padding(start = 5.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val tile = RoundedCornerShape(6.dp)
        Box(Modifier.size(28.dp).clip(tile).background(Anton.Overlay).border(1.dp, Anton.BorderSubtle, tile), contentAlignment = Alignment.Center) {
            if (imageUrl != null) RemoteImage(imageUrl, null, Modifier.fillMaxSize(), ContentScale.Crop, maxPx = 96)
            else Icon(Lucide.FileText, null, tint = Anton.IconTertiary, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(name, style = Type.SmallMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            note?.let { Text(it, style = Type.Meta.copy(fontSize = 10.5.sp), color = Anton.TextTertiary, maxLines = 1) }
        }
    }
}

/** The files a sent message carried, as chips under it (src/web/components/attachments.tsx). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentAttachments(files: List<Attachments.Ref>, modifier: Modifier = Modifier) {
    if (files.isEmpty()) return
    val place = LocalFilePlace.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEach { f ->
            FileChip(f.name, "${f.kind} · ${Attachments.size(f.size)}", imageUrl = if (f.image) place.attachment(f) else null)
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
            .then(if (viewer != null) Modifier.clickable { viewer(url, name) } else Modifier),
    ) {
        RemoteImage(url, name, Modifier.widthIn(min = 120.dp, max = 280.dp).height(height).fillMaxWidth(), ContentScale.Crop)
        if (caption) {
            Row(
                Modifier.fillMaxWidth().hairlineTop().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Lucide.Image, null, tint = Anton.IconTertiary, modifier = Modifier.size(12.dp))
                Text(name.substringAfterLast('/'), style = Type.Meta, color = Anton.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Lucide.Maximize2, null, tint = Anton.IconDisabled, modifier = Modifier.size(11.dp))
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
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEach { (path, url, image) ->
            val folder = path.substringBeforeLast('/', "").ifBlank { null }
            val ext = path.substringAfterLast('.', "").uppercase().ifBlank { null }
            FileChip(
                path.substringAfterLast('/'),
                listOfNotNull(if (image) "${ext ?: "Image"} image" else ext, folder ?: "Library").joinToString(" · "),
                imageUrl = if (image) url else null,
            )
        }
    }
}
