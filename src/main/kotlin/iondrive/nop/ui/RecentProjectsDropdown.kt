package iondrive.nop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import java.nio.file.Files
import java.nio.file.Path

/**
 * Folder-icon button that opens a popup listing recent projects + a "New…" item.
 * Stale (non-existent) recents and the currently-open [currentProject] are filtered out.
 */
@OptIn(ExperimentalJewelApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecentProjectsDropdown(
    recentProjects: List<Path>,
    currentProject: Path,
    onPickRecent: (Path) -> Unit,
    onPickNew: () -> Unit,
    tint: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    val visible = remember(recentProjects, currentProject) {
        recentProjects
            .map { it.toAbsolutePath().normalize() }
            .distinct()
            .filter { it != currentProject.toAbsolutePath().normalize() }
            .filter { Files.isDirectory(it) }
    }

    Box {
        Tooltip(tooltip = { Text("Open a project") }) {
            IconButton(onClick = { expanded = !expanded }) {
                Canvas(Modifier.size(16.dp)) { drawOpenFolderIcon(tint) }
            }
        }

        if (expanded) {
            Popup(
                onDismissRequest = { expanded = false },
                offset = IntOffset(0, 28),
                properties = PopupProperties(focusable = true),
            ) {
                val border = if (JewelTheme.isDark) Color(0xFF393B40) else Color(0xFFD3D5DB)
                val bg = JewelTheme.globalColors.panelBackground
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(6.dp))
                        .padding(vertical = 4.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (visible.isEmpty()) {
                        PassiveText("No recent projects")
                    } else {
                        for (p in visible) {
                            RecentRow(
                                title = p.fileName?.toString() ?: p.toString(),
                                subtitle = p.toString(),
                                onClick = {
                                    expanded = false
                                    onPickRecent(p)
                                },
                            )
                        }
                        Separator()
                    }
                    RecentRow(
                        title = "Open…",
                        subtitle = null,
                        onClick = {
                            expanded = false
                            onPickNew()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(title)
        if (subtitle != null) {
            val muted = if (JewelTheme.isDark) Color(0xFF8B8F99) else Color(0xFF7A7E87)
            Text(subtitle, color = muted)
        }
    }
}

@Composable
private fun PassiveText(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
        val muted = if (JewelTheme.isDark) Color(0xFF8B8F99) else Color(0xFF7A7E87)
        Text(text, color = muted)
    }
}

@Composable
private fun Separator() {
    val color = if (JewelTheme.isDark) Color(0xFF393B40) else Color(0xFFE3E5EA)
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(color))
}
