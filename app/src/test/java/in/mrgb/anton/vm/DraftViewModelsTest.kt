package `in`.mrgb.anton.vm

import androidx.lifecycle.viewModelScope
import `in`.mrgb.anton.data.TaskAttachmentServer
import `in`.mrgb.anton.data.api.PendingUpload
import `in`.mrgb.anton.ui.components.DraftImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DraftViewModelsTest {
    @Test fun threadViewModelOwnsAndRetainsDraftAcrossFailedSendsUntilSuccess() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            TaskAttachmentServer().use { server ->
                val vm = ThreadViewModel(server.repo, "chat-task")
                try {
                    val draft = vm.draft
                    assertSame(vm.viewModelScope, draft.readScope)
                    val image = DraftImage(PendingUpload("image", "image/png", byteArrayOf(1, 2)), null)
                    draft.edit("Describe this")
                    draft.attachments.images += image
                    server.uploadStatus = 413
                    vm.send(draft.text, draft.attachments.snapshot()).join()
                    assertSame(draft, vm.draft)
                    assertEquals("Describe this", vm.draft.text)
                    assertSame(image, vm.draft.attachments.images.single())
                    assertTrue(vm.draft.error!!.contains("413"))
                    server.uploadStatus = 200
                    vm.send(draft.text, draft.attachments.snapshot()).join()
                    assertEquals("", vm.draft.text)
                    assertEquals(0, vm.draft.attachments.count)
                    draft.edit("Choose A")
                    vm.answer("Choose A").join()
                    assertEquals("Choose A", draft.text)
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun coordinatorDraftsSurviveScreenSwitchesAndAreIsolatedPerProject() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            TaskAttachmentServer().use { server ->
                val vm = ProjectsViewModel(server.repo)
                try {
                    val first = vm.coordinatorDraft("first")
                    val second = vm.coordinatorDraft("second")
                    assertSame(vm.viewModelScope, first.readScope)
                    first.edit("First project")
                    second.edit("Second project")
                    server.admissionStatus = 503
                    vm.sendCoordinator("first", first.text, first.attachments.snapshot()).join()
                    assertSame(first, vm.coordinatorDraft("first"))
                    assertEquals("First project", first.text)
                    assertTrue(first.error!!.contains("503"))
                    assertEquals("Second project", second.text)
                    assertNull(second.error)
                    server.admissionStatus = 200
                    vm.sendCoordinator("first", first.text, first.attachments.snapshot()).join()
                    assertEquals("", first.text)
                    assertEquals("Second project", second.text)
                    first.edit("Choose A")
                    vm.answerCoordinator("first", "Choose A").join()
                    assertEquals("Choose A", first.text)
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }
}
