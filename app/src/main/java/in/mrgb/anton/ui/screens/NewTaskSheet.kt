package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.data.api.ModelDto
import `in`.mrgb.anton.ui.components.AntonSwitch
import `in`.mrgb.anton.ui.components.SheetChip
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

/**
 * The new-task drawer — the web composer's anatomy: a prompt card with a
 * footer row of dropdown chips (repo · branch · model · plan) and Start.
 */
@Composable
fun NewTaskSheet(
    repos: List<Pair<String, String>>, // id → name
    branches: List<String>,
    defaultBranch: String?,
    models: List<ModelDto>,
    planFirst: Boolean,
    creating: Boolean,
    onRepo: (String?) -> Unit,
    onPlanFirst: (Boolean) -> Unit,
    onStart: (projectId: String?, branch: String?, prompt: String, model: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var repo by remember { mutableStateOf(repos.firstOrNull()?.first) }
    var branch by remember { mutableStateOf(branches.firstOrNull()) }
    var prompt by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(models.firstOrNull()?.id) }
    var repoMenu by remember { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().background(Anton.Raised)
            .padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 10.dp),
    ) {
        // The prompt card — the same well as the thread composer.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Anton.CardBorder, RoundedCornerShape(12.dp)).background(Anton.CardBg)) {
            BasicTextField(
                value = prompt, onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 56.dp),
                textStyle = Type.Body.copy(color = Anton.TextPrimary),
                cursorBrush = SolidColor(Anton.Accent),
                decorationBox = { inner ->
                    if (prompt.isBlank()) Text("What should the agent do?", style = Type.Body, color = Anton.TextTertiary)
                    inner()
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Repo chip → dropdown
                Box {
                    SheetChip(Icons.Outlined.Folder, repos.firstOrNull { it.first == repo }?.second?.substringAfterLast('/') ?: "Chat", true) { repoMenu = true }
                    DropdownMenu(
                        expanded = repoMenu, onDismissRequest = { repoMenu = false },
                        modifier = Modifier.background(Anton.Overlay).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(9.dp)).heightIn(max = 280.dp),
                        containerColor = Anton.Overlay,
                        shadowElevation = 8.dp,
                    ) {
                        DropdownMenuItem(
                            modifier = Modifier.height(30.dp),
                            text = { Text("Chat (no repo)", style = Type.Small, color = if (repo == null) Anton.Accent else Anton.TextPrimary) },
                            onClick = { repo = null; branch = null; onRepo(null); repoMenu = false },
                        )
                        repos.forEach { (id, name) ->
                            DropdownMenuItem(
                                modifier = Modifier.height(30.dp),
                                text = { Text(name, style = Type.Small, color = if (id == repo) Anton.Accent else Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { repo = id; branch = null; onRepo(id); repoMenu = false },
                            )
                        }
                    }
                }
                // Branch chip → dropdown (repo tasks only)
                if (repo != null) {
                    Box {
                        SheetChip(Icons.Outlined.AccountTree, branch ?: defaultBranch ?: "main", true) { branchMenu = true }
                        DropdownMenu(
                            expanded = branchMenu, onDismissRequest = { branchMenu = false },
                            modifier = Modifier.background(Anton.Overlay).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(9.dp)).heightIn(max = 280.dp),
                            containerColor = Anton.Overlay,
                            shadowElevation = 8.dp,
                        ) {
                            branches.forEach { b ->
                                DropdownMenuItem(
                                    modifier = Modifier.height(30.dp),
                                    text = { Text(b, style = Type.Small, color = if (b == branch) Anton.Accent else Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = { branch = b; branchMenu = false },
                                )
                            }
                        }
                    }
                }
                // Model chip → the shared searchable picker (search + vendor groups)
                Box {
                    SheetChip(
                        null,
                        models.firstOrNull { it.id == model }?.name ?: model?.substringAfterLast('/') ?: "Model",
                        true,
                    ) { modelMenu = true }
                    if (modelMenu) {
                        ModelPickerSheet(
                            models = models,
                            selected = model ?: "",
                            onPick = { model = it; modelMenu = false },
                            onDismiss = { modelMenu = false },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                AntonSwitch(planFirst, onPlanFirst)
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (prompt.isBlank() || creating) Anton.N700 else Anton.Accent)
                        .clickable(enabled = prompt.isNotBlank() && !creating) { onStart(repo, branch, prompt, model) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowUpward, if (creating) "Starting…" else "Start task", tint = if (prompt.isBlank() || creating) Anton.TextDisabled else Anton.AccentFg, modifier = Modifier.size(15.dp)) }
            }
        }
    }
}
