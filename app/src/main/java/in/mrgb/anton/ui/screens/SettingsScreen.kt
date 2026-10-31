package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import `in`.mrgb.anton.ui.components.contentWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.ui.components.AntonCard
import `in`.mrgb.anton.ui.components.AntonSwitch
import `in`.mrgb.anton.ui.components.SectionLabel
import `in`.mrgb.anton.ui.theme.Anton
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
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = Type.Title, color = Anton.TextPrimary, modifier = Modifier.weight(1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.contentWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Server")
            AntonCard(Modifier.fillMaxWidth()) {
                FieldRow("Server URL", baseUrl, placeholder = "https://code.mrgb.in") { url -> onServer(url, cfId, cfSecret) }
                Divider()
                FieldRow("CF-Access-Client-Id", cfId, placeholder = "service-token client id") { v -> onServer(baseUrl, v, cfSecret) }
                Divider()
                FieldRow("CF-Access-Client-Secret", cfSecret, secret = true) { v -> onServer(baseUrl, cfId, v) }
            }
            SectionLabel("Notify me")
            AntonCard(Modifier.fillMaxWidth()) {
                ToggleRow("Needs you", "questions, plan approvals, failures", notifyNeedsYou, onToggleNeedsYou)
                Divider()
                ToggleRow("Finished", "task done or PR opened", notifyFinished, onToggleFinished)
                Divider()
                ToggleRow("Working updates", "silent while the app is open", notifyWorking, onToggleWorking)
            }
            SectionLabel("Defaults")
            AntonCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Model", style = Type.Body, color = Anton.TextPrimary, modifier = Modifier.weight(1f))
                    Text(defaultModel ?: "server default", style = Type.Small, color = Anton.TextTertiary)
                }
                Divider()
                ToggleRow("Plan first", "new tasks open in plan mode", planFirst, onTogglePlanFirst)
            }
        }
        }
        BottomBar(tabIndex, onNav)
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Anton.BorderSubtle))

@Composable
private fun FieldRow(label: String, value: String, placeholder: String = "", secret: Boolean = false, onValue: (String) -> Unit) {
    var editing = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Body, color = Anton.TextPrimary)
            OutlinedTextField(
                value = editing.value,
                onValueChange = { editing.value = it; onValue(it) },
                singleLine = true,
                placeholder = { Text(placeholder, style = Type.Num, color = Anton.TextDisabled) },
                visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
                    else androidx.compose.ui.text.input.VisualTransformation.None,
                textStyle = Type.Num.copy(color = Anton.TextSecondary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Anton.Accent, unfocusedBorderColor = Anton.BorderSubtle,
                    focusedContainerColor = Anton.WellBg, unfocusedContainerColor = Anton.WellBg,
                    cursorColor = Anton.Accent,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Body, color = Anton.TextPrimary)
            Text(hint, style = Type.Meta, color = Anton.TextTertiary)
        }
        AntonSwitch(checked, onChange)
    }
}
