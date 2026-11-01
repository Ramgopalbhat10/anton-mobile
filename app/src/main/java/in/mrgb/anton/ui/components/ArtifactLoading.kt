package `in`.mrgb.anton.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState

/** Each selection owns fresh loading state; disposed selections cannot publish stale results. */
@Composable
internal fun rememberArtifactBody(
    url: String,
    source: Any?,
    fetch: suspend (String) -> ByteArray?,
): State<Any?> = key(url, source) {
    produceState<Any?>(null) {
        value = fetch(url) ?: false
    }
}
