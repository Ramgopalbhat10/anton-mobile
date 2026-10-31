package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

@Composable
fun SettingsScreen(
    baseUrl: String,
    cfId: String,
    cfSecret: String,
    notifyNeedsYou: Boolean,
    notifyFinished: Boolean,
    notifyWorking: Boolean,
    planFirst: Boolean,
    defaultModel: String?,
    onServer: (String, String, String) -> Unit,
    onToggleNeedsYou: (Boolean) -> Unit,
    onToggleFinished: (Boolean) -> Unit,
    onToggleWorking: (Boolean) -> Unit,
    onTogglePlanFirst: (Boolean) -> Unit,
    tabIndex: Int,
    onNav: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val host = baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        AppBar("Settings")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.contentWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 24.dp),
        ) {
            GroupLabel("Server")
            AntonCard(Modifier.fillMaxWidth()) {
                ListRow(
                    host.ifBlank { "No server" },
                    subtitle = if (baseUrl.isBlank()) "tap to add your Anton URL" else baseUrl.substringBefore("://", "https") + " · tap to edit",
                    icon = Lucide.Server,
                    monoSubtitle = true,
                    onClick = { editing = true },
                    trailing = { if (baseUrl.isBlank()) Badge("Not set", Tone.Warning) else Badge("Saved", Tone.Success) },
                )
                Hairline()
                ListRow(
                    "Cloudflare Access",
                    subtitle = if (cfId.isBlank()) "no service token" else "service token · ${cfId.take(10)}…",
                    icon = Lucide.Shield,
                    monoSubtitle = true,
                    onClick = { editing = true },
                    trailing = { if (cfId.isBlank() || cfSecret.isBlank()) Badge("Not set", Tone.Neutral) else Badge("Service auth", Tone.Accent) },
                )
            }
            GroupLabel("Notify me")
            AntonCard(Modifier.fillMaxWidth()) {
                ToggleRow("Needs you", "questions, plan approvals, failures", notifyNeedsYou, onToggleNeedsYou)
                Hairline()
                ToggleRow("Finished", "task done or PR opened", notifyFinished, onToggleFinished)
                Hairline()
                ToggleRow("Working updates", "silent while the app is open", notifyWorking, onToggleWorking)
            }
            GroupLabel("Defaults")
            AntonCard(Modifier.fillMaxWidth()) {
                ListRow("Model", trailing = {
                    Text(defaultModel?.substringAfterLast('/') ?: "server default", style = Type.Small, color = Anton.TextTertiary)
                })
                Hairline()
                ToggleRow("Plan first", "new tasks open in plan mode", planFirst, onTogglePlanFirst)
            }
            Text(
                "Anton for Android",
                style = Type.Num, color = Anton.TextDisabled,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
        }
        }
        BottomBar(tabIndex, onNav)
    }
    if (editing) {
        ServerSheet(baseUrl, cfId, cfSecret, onSave = { u, i, s -> onServer(u, i, s); editing = false }, onDismiss = { editing = false })
    }
}

@Composable
private fun GroupLabel(text: String) = SectionLabel(text, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(label, subtitle = hint, onClick = { onChange(!checked) }, trailing = { AntonSwitch(checked, onChange) })
}

/** Server + Cloudflare service token, edited together and saved on Save — a shadcn form in a sheet. */
@Composable
private fun ServerSheet(
    baseUrl: String, cfId: String, cfSecret: String,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(baseUrl) }
    var id by remember { mutableStateOf(cfId) }
    var secret by remember { mutableStateOf(cfSecret) }
    AntonSheet(onDismiss = onDismiss, title = "Server", subtitle = "Every request carries the service token, including the live stream.") {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Field("Server URL") {
                AntonInput(
                    url, { url = it.trim() }, placeholder = "https://code.mrgb.in", mono = true, leadingIcon = Lucide.Server,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Field("CF-Access-Client-Id") {
                AntonInput(
                    id, { id = it.trim() }, placeholder = "xxxxxxxx.access", mono = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Field("CF-Access-Client-Secret", hint = "Stored on this device only. Rotate it from the Zero Trust dashboard if the phone is lost.") {
                AntonInput(
                    secret, { secret = it.trim() }, placeholder = "service token secret", mono = true, secret = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AntonButton("Cancel", onClick = onDismiss, variant = ButtonVariant.Outline, size = ButtonSize.Lg, modifier = Modifier.weight(1f))
                AntonButton("Save", onClick = { onSave(url, id, secret) }, size = ButtonSize.Lg, modifier = Modifier.weight(1f))
            }
        }
    }
}
