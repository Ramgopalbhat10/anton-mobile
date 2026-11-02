package `in`.mrgb.anton.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AttachmentImagesTest {
    private val ref = Attachments.Ref("image.png", "uploads/42-image.png", "PNG image", 4, false)
    private fun image(path: String? = null, title: String? = null, url: String? = "data:image/png;base64,AQIDBA==") =
        PartDto(type = "file", path = path, title = title, url = url, mediaType = "image/png")

    @Test fun savedPathAssociationPrefersThePillForRawAndNormalizedMessages() {
        for (type in listOf("file", "data-file")) {
            val raw = Json.parseToJsonElement("""{"messages":[{"role":"user","parts":[{"type":"$type","filename":"${ref.path}","url":"data:image/png;base64,AQIDBA==","mediaType":"image/png"}]}]}""")
            val parts = Transcript.messagesOf(raw).single().parts
            assertTrue(Attachments.inlineImages(parts, listOf(ref)).isEmpty())
        }
        assertTrue(Attachments.inlineImages(listOf(image(path = ref.path)), listOf(ref)).isEmpty())
        assertTrue(Attachments.inlineImages(listOf(image(title = ref.path)), listOf(ref)).isEmpty())
    }

    @Test fun sameBasenameIsNotEnoughToHideAnUnrelatedOrLegacyImage() {
        val parts = listOf(image(path = ref.name, title = ref.name), image(path = "uploads/other/image.png"))
        assertEquals(parts, Attachments.inlineImages(parts, listOf(ref)))
    }

    @Test fun oneMatchingImageDoesNotHideOtherInlineImages() {
        val legacy = image(path = "unrelated.png")
        assertEquals(listOf(legacy), Attachments.inlineImages(listOf(image(path = ref.path), legacy), listOf(ref)))
    }

    @Test fun imageOnlyLegacyTranscriptsKeepTheirPreviews() {
        val parts = listOf(image(), image(url = "https://example.org/image.png"))
        assertEquals(parts, Attachments.inlineImages(parts, emptyList()))
    }

    @Test fun nonImageRefsAndNonImageOrMissingUrlPartsDoNotMatch() {
        val inline = image(path = ref.path)
        assertEquals(listOf(inline), Attachments.inlineImages(listOf(inline), listOf(ref.copy(kind = "File", path = "uploads/notes.txt"))))
        assertTrue(Attachments.inlineImages(listOf(image(url = null), image(url = ""), PartDto(type = "text"), inline.copy(mediaType = "application/pdf")), listOf(ref)).isEmpty())
    }

    @Test fun attachmentBlockKeepsAuthoritativeMetadataAndScopeAcrossReload() {
        val upload = AttachedFileDto("cost & score.png", "uploads/unique-cost.png", 1200, "PNG image")
        for (project in listOf(false, true)) {
            val split = Attachments.split(Attachments.withAttachments("What is this image about?", listOf(upload), project))
            assertEquals("What is this image about?", split.text)
            assertEquals(listOf(Attachments.Ref(upload.name, upload.path, upload.kind, upload.size, project)), split.files)
            assertTrue(split.files.single().image)
        }
    }
}
