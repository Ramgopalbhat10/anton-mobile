package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.ui.components.AntonSwitch
import `in`.mrgb.anton.ui.components.AntonWell
import `in`.mrgb.anton.ui.components.Segmented
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

/** Bottom-sheet content shown from the Tasks screen's FAB / +. */
@Composable
fun NewTaskSheet(
    repos: List<Pair<String, String>>, // id → name
    models: List<String>,
    planFirst: Boolean,
    creating: Boolean,
    onPlanFirst: (Boolean) -> Unit,
    onStart: (projectId: String?, prompt: String, model: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var repo by remember { mutableStateOf(repos.firstOrNull()?.first) }
    var prompt by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(models.firstOrNull()) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp)).background(Anton.Raised).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Anton.N600))
        Text("New task", style = Type.Title.copy(fontSize = 15.sp), color = Anton.TextPrimary)

        SheetLabel("Repository")
        AntonWell(Modifier.fillMaxWidth().height(38.dp)) {
            Text(
                repos.firstOrNull { it.first == repo }?.second ?: repos.firstOrNull()?.second ?: "Chat (no repo)",
                style = Type.Body, color = Anton.TextPrimary, modifier = Modifier.weight(1f),
            )
        }
        if (repos.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repos.take(4).forEach { (id, name) ->
                    Text(
                        name.substringAfterLast('/'),
                        style = Type.Meta,
                        color = if (id == repo) Anton.Accent else Anton.TextTertiary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { repo = id }.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        SheetLabel("Prompt")
        AntonWell(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            BasicTextField(
                value = prompt, onValueChange = { prompt = it },
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                textStyle = Type.Body.copy(color = Anton.TextPrimary),
                cursorBrush = SolidColor(Anton.Accent),
                decorationBox = { inner ->
                    if (prompt.isBlank()) Text("Describe what Anton should do…", style = Type.Body, color = Anton.TextTertiary)
                    inner()
                },
            )
        }

        if (models.isNotEmpty()) {
            SheetLabel("Model")
            Segmented(options = models.take(3).map { it to it.substringAfterLast('/') }, selected = model ?: "", onSelect = { model = it }, modifier = Modifier.fillMaxWidth())
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Plan first", style = Type.Body, color = Anton.TextPrimary, modifier = Modifier.weight(1f))
            AntonSwitch(planFirst, onPlanFirst)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Anton.Raised).border(1.dp, Anton.BorderDefault, RoundedCornerShape(6.dp)).clickable(onClick = onDismiss).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Attach context", style = Type.BodyMedium, color = Anton.TextPrimary) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (prompt.isBlank() || creating) Anton.N700 else Anton.Accent).clickable(enabled = prompt.isNotBlank() && !creating) { onStart(repo, prompt, model) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (creating) "Starting…" else "Start task", style = Type.BodyMedium, color = if (prompt.isBlank() || creating) Anton.TextDisabled else Anton.AccentFg) }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text.uppercase(), style = Type.MonoLabel, color = Anton.TextDisabled)
}
