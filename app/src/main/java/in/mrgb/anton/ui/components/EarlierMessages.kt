package `in`.mrgb.anton.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.data.api.ConversationWindow
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

/**
 * The turns before the earliest one shown. Scrolling up to the start of the list reads the next page, which goes in
 * above without moving the message you are reading. Opening the conversation (scrolling down to the newest message)
 * reads nothing; a conversation too short to scroll offers a button instead. Sits over the top of the list.
 *
 * [keys] are the list's item keys in order, starting at its first item.
 */
@Composable
fun BoxScope.EarlierMessages(listState: LazyListState, older: ConversationWindow.Older, keys: List<Any>, onLoad: () -> Unit) {
    // The message at the top of the view when a page was asked for, and how far it was scrolled.
    var anchor by remember { mutableStateOf<Pair<Any, Int>?>(null) }
    val load = {
        anchor = keys.getOrNull(listState.firstVisibleItemIndex)?.let { it to listState.firstVisibleItemScrollOffset }
        onLoad()
    }
    // Once a page goes in above, that message goes back where it was.
    LaunchedEffect(keys.firstOrNull()) {
        val (key, offset) = anchor ?: return@LaunchedEffect
        anchor = null
        val index = keys.indexOf(key)
        if (index > 0 && listState.firstVisibleItemIndex != index) listState.scrollToItem(index, offset)
    }
    val atStart by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    LaunchedEffect(atStart, older) {
        // Only when you scrolled up to it (the list first scrolls down to the newest message, past the start), and not
        // while the last page is still going in.
        if (atStart && anchor == null && listState.lastScrolledBackward && older.more && !older.loading && !older.failed) load()
    }
    if (!older.loading && !(older.more && atStart)) return
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(shape).background(Anton.Raised).border(1.dp, Anton.BorderDefault, shape),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            older.loading -> Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(12.dp, Anton.TextTertiary)
                Text("Loading earlier messages", style = Type.Small, color = Anton.TextTertiary)
            }
            older.failed -> AntonButton("Earlier messages did not load. Try again", onClick = load, variant = ButtonVariant.Ghost, size = ButtonSize.Sm)
            else -> AntonButton("Show earlier messages", onClick = load, variant = ButtonVariant.Ghost, size = ButtonSize.Sm)
        }
    }
}
