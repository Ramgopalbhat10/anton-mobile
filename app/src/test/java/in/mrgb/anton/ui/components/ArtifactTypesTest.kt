package `in`.mrgb.anton.ui.components

import `in`.mrgb.anton.data.api.Attachments
import `in`.mrgb.anton.data.api.PartDto
import org.junit.Assert.*
import org.junit.Test

class ArtifactTypesTest {
    @Test fun extensionlessImageChipKeepsFullSizeImageViewerAfterInlineSuppression() {
        val ref = Attachments.Ref("image", "uploads/1-image", "PNG image", 300, false)
        val inline = PartDto(type = "file", path = ref.path, url = "data:image/png;base64,AQID", mediaType = "image/png")
        assertTrue(Attachments.inlineImages(listOf(inline), listOf(ref)).isEmpty())
        val artifact = FilePlace(sessionId = "task").attachmentArtifact(ref)!!
        assertEquals("image", artifact.name)
        assertEquals("/api/sessions/task/output?path=uploads%2F1-image", artifact.url)
        assertEquals(Artifact.Kind.Image, artifact.kind)
    }

    @Test fun imageMetadataWinsEvenWhenProviderSuppliesAMisleadingExtension() {
        val ref = Attachments.Ref("photo.bin", "uploads/photo", "JPEG image", 300, false)
        assertEquals(Artifact.Kind.Image, FilePlace(sessionId = "task").attachmentArtifact(ref)!!.kind)
    }

    @Test fun mixedGalleryPreservesImageHintFileKindsNamesAndOrder() {
        val refs = listOf(
            Attachments.Ref("image", "image", "WebP image", 300, true),
            Attachments.Ref("notes.txt", "uploads/notes.txt", "Text", 20, false),
            Attachments.Ref("archive.zip", "uploads/archive.zip", "Archive", 30, false),
        )
        val artifacts = refs.map { FilePlace("task", "project").attachmentArtifact(it)!! }
        assertEquals(refs.map { it.name }, artifacts.map { it.name })
        assertEquals(listOf(Artifact.Kind.Image, Artifact.Kind.Text, Artifact.Kind.Other), artifacts.map { it.kind })
        assertEquals("/api/spaces/project/file?path=image", artifacts.first().url)
    }

    @Test fun existingExtensionBasedDetectionRemainsTheDefault() {
        for ((name, kind) in mapOf("photo.png" to Artifact.Kind.Image, "doc.md" to Artifact.Kind.Markdown,
            "page.html" to Artifact.Kind.Html, "script.kt" to Artifact.Kind.Text, "image" to Artifact.Kind.Other)) {
            assertEquals(kind, Artifact(name, "/file").kind)
        }
    }

    @Test fun knownImageCardsCanUseAnImageHintWithoutRenamingFiles() {
        val artifact = Artifact("image", "data:image/png;base64,AQID", Artifact.Kind.Image)
        assertEquals("image", artifact.name)
        assertEquals(Artifact.Kind.Image, artifact.kind)
        assertNull(FilePlace().attachmentArtifact(Attachments.Ref("image", "image", "PNG image", 1, false)))
    }
}
