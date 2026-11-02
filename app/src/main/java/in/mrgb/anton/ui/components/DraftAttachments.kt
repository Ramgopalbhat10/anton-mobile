package `in`.mrgb.anton.ui.components

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.data.api.Attachments
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.PendingUpload
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* The pick limits the web's attachments.ts keeps. */
const val MAX_IMAGES = 4
private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
private const val MAX_FILES = 10
private const val MAX_FILE_BYTES = 20 * 1024 * 1024
private val IMAGE_MIMES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

/** An image the model sees, with a decoded thumbnail for the tray. */
class DraftImage(val upload: PendingUpload, val preview: ImageBitmap?)

private fun sentAsImage(u: PendingUpload) = u.mime in IMAGE_MIMES && u.bytes.size <= MAX_IMAGE_BYTES

/** A name not already taken in the draft, so two files never land on one path (the web's freeName). */
private fun freeName(name: String, taken: Set<String>): String {
    if (name !in taken) return name
    val dot = name.lastIndexOf('.')
    val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    var index = 2
    while ("$base-$index$ext" in taken) index++
    return "$base-$index$ext"
}

private fun decode(bytes: ByteArray, maxPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/**
 * What a composer holds besides its text — the mobile counterpart of the web's
 * useAttachments: images the model sees go with the message, every other file
 * uploads as the message sends and is named in its body.
 */
class DraftAttachments(
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
) {
    val images = mutableStateListOf<DraftImage>()
    val files = mutableStateListOf<PendingUpload>()
    var notice by mutableStateOf<String?>(null); private set
    val count get() = images.size + files.size
    val full get() = images.size >= MAX_IMAGES && files.size >= MAX_FILES

    private fun read(uri: Uri): PendingUpload? = runCatching {
        val named = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val mime = resolver.getType(uri)?.takeUnless { it == "application/octet-stream" }
            ?: named?.substringAfterLast('.', "")?.lowercase()
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        PendingUpload(named ?: if (mime.startsWith("image/")) "image" else "file", mime, bytes)
    }.getOrNull()

    /** The picked files, sorted like the web's readAttachments: seeable images as images, the rest as files. */
    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val picked = withContext(Dispatchers.IO) { uris.mapNotNull(::read) }
            val fits = picked.filter { it.bytes.size <= MAX_FILE_BYTES }
            val pictures = fits.filter(::sentAsImage)
            val others = fits.filter { !sentAsImage(it) }
            val keptPictures = pictures.take((MAX_IMAGES - images.size).coerceAtLeast(0))
            val keptOthers = others.take((MAX_FILES - files.size).coerceAtLeast(0))
            val taken = files.mapTo(mutableSetOf()) { it.name }
            val named = keptOthers.map { f ->
                val n = freeName(f.name, taken)
                taken += n
                if (n == f.name) f else f.copy(name = n)
            }
            val previews = withContext(Dispatchers.IO) { keptPictures.map { DraftImage(it, decode(it.bytes, 168)) } }
            images += previews
            files += named
            notice = when {
                fits.size < picked.size -> "Files up to 20 MB can be attached."
                keptPictures.size < pictures.size -> "Up to $MAX_IMAGES images per message."
                keptOthers.size < others.size -> "Up to $MAX_FILES files per message."
                else -> null
            }
        }
    }

    /** Empties the draft's attachments and hands them over, for sending. */
    fun take(): OutgoingDraft {
        val out = OutgoingDraft(images.map { it.upload }, files.toList())
        images.clear()
        files.clear()
        notice = null
        return out
    }

    /** Puts back attachments a send could not deliver, unless new ones were added since. */
    fun restore(taken: OutgoingDraft) {
        if (images.isEmpty()) scope.launch {
            val previews = withContext(Dispatchers.IO) { taken.images.map { DraftImage(it, decode(it.bytes, 168)) } }
            if (images.isEmpty()) images += previews
        }
        if (files.isEmpty()) files += taken.files
    }

    fun removeImage(image: DraftImage) { images -= image }
    fun removeFile(file: PendingUpload) { files -= file }
}

@Composable
fun rememberDraftAttachments(): DraftAttachments {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val scope = rememberCoroutineScope()
    return remember { DraftAttachments(resolver, scope) }
}

/** The two system pickers a composer offers: any files, or photos/screenshots only. */
class ComposerPicker internal constructor(val files: () -> Unit, val images: () -> Unit)

@Composable
fun rememberComposerPicker(onPick: (List<Uri>) -> Unit): ComposerPicker {
    // Snapshots/previews have no activity result registry; the buttons just never fire there.
    if (LocalActivityResultRegistryOwner.current == null) return remember { ComposerPicker({}, {}) }
    val any = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { onPick(it.orEmpty()) }
    val pics = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { onPick(it.orEmpty()) }
    return remember(any, pics) {
        ComposerPicker(
            files = { any.launch("*/*") },
            images = { pics.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/** A draft file as a chip: its glyph, name and size, with a remove button. */
@Composable
private fun DraftFileChip(file: PendingUpload, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(36.dp).widthIn(max = 240.dp).clip(shape).background(Anton.WellBg).border(1.dp, Anton.BorderSubtle, shape)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (Attachments.isImagePath(file.name)) Lucide.Image else Lucide.FileText, null,
            tint = Anton.IconSecondary, modifier = Modifier.size(14.dp),
        )
        Column(Modifier.weight(1f, fill = false)) {
            Text(file.name, style = Type.SmallMedium.copy(fontSize = 11.5.sp), color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Attachments.size(file.bytes.size.toLong()), style = Type.Meta.copy(fontSize = 10.5.sp), color = Anton.TextTertiary, maxLines = 1)
        }
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(Lucide.X, "Remove ${file.name}", tint = Anton.IconTertiary, modifier = Modifier.size(10.dp))
        }
    }
}

/** The draft's images as thumbnails and its files as chips, each removable — the web's AttachmentTray. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentTray(atts: DraftAttachments, modifier: Modifier = Modifier) {
    if (atts.images.isEmpty() && atts.files.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        atts.images.forEach { image ->
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(8.dp))) {
                if (image.preview != null) Image(image.preview, image.upload.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Anton.Raised), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Image, null, tint = Anton.IconTertiary, modifier = Modifier.size(18.dp))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99000000)).clickable(role = androidx.compose.ui.semantics.Role.Button) { atts.removeImage(image) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.X, "Remove ${image.upload.name}", tint = Anton.TextPrimary, modifier = Modifier.size(10.dp)) }
            }
        }
        atts.files.forEach { file -> DraftFileChip(file) { atts.removeFile(file) } }
    }
}
