package iondrive.nop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import iondrive.nop.ui.App
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JFileChooser
import kotlin.system.exitProcess

@OptIn(FlowPreview::class)
fun main(args: Array<String>) {
    val initial = resolveInitialProjects(args)
    if (initial.isEmpty()) exitProcess(0)

    application {
        // One entry per open window. Adding a path opens a new window; removing one closes it.
        // When the list empties we exit. Using a state list so Compose recomposes on changes.
        val openProjects = remember { mutableStateListOf<Path>().apply { addAll(initial) } }
        var darkMode by remember { mutableStateOf(Settings.loadDarkMode()) }

        LaunchedEffect(Unit) {
            // Never persist an empty list: when the user closes the last window we want the
            // *previous* state to survive on disk so the next launch reopens it instead of
            // dropping back to a fresh picker. The list naturally going to zero means the app
            // is about to exit — leaving the previous content in place is the desired behaviour.
            snapshotFlow { openProjects.toList() }
                .distinctUntilChanged()
                .collectLatest { if (it.isNotEmpty()) Settings.saveOpenProjects(it) }
        }
        LaunchedEffect(darkMode) { Settings.saveDarkMode(darkMode) }

        val snapshot = openProjects.toList()
        snapshot.forEachIndexed { index, projectPath ->
            ProjectWindow(
                projectPath = projectPath,
                isFirstWindow = index == 0,
                darkMode = darkMode,
                onPickAnotherProject = {
                    pickProjectDir(initial = projectPath.toFile())?.let { picked ->
                        // Avoid opening a duplicate window for an already-open project.
                        if (picked !in openProjects) openProjects.add(picked)
                    }
                },
                onToggleTheme = { darkMode = !darkMode },
                onCloseWindow = {
                    openProjects.remove(projectPath)
                    if (openProjects.isEmpty()) exitApplication()
                },
            )
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ApplicationScope.ProjectWindow(
    projectPath: Path,
    isFirstWindow: Boolean,
    darkMode: Boolean,
    onPickAnotherProject: () -> Unit,
    onToggleTheme: () -> Unit,
    onCloseWindow: () -> Unit,
) {
    // The first window restores the persisted geometry; additional windows let the OS pick
    // a fresh position so they don't all stack on top of each other.
    val saved = Settings.loadWindowGeometry()
    val windowState = rememberWindowState(
        size = DpSize(
            width = saved?.width?.dp ?: 1000.dp,
            height = saved?.height?.dp ?: 700.dp,
        ),
        position = if (isFirstWindow && saved?.x != null && saved.y != null) {
            WindowPosition(saved.x.dp, saved.y.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )

    // Only the first window writes back geometry — otherwise every newly-opened additional
    // window would race to overwrite the saved position with its own.
    if (isFirstWindow) {
        LaunchedEffect(windowState) {
            snapshotFlow {
                WindowGeometry(
                    width = windowState.size.width.value.toInt().coerceAtLeast(200),
                    height = windowState.size.height.value.toInt().coerceAtLeast(200),
                    x = windowState.position.x.takeIf { it.isSpecified }?.value?.toInt(),
                    y = windowState.position.y.takeIf { it.isSpecified }?.value?.toInt(),
                )
            }
                .debounce(500)
                .distinctUntilChanged()
                .collectLatest { Settings.saveWindowGeometry(it) }
        }
    }

    Window(
        state = windowState,
        onCloseRequest = onCloseWindow,
        title = "nop — ${projectPath.fileName}",
    ) {
        IntUiTheme(
            theme = if (darkMode) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(),
            styling = ComponentStyling.default(),
        ) {
            App(
                projectPath = projectPath,
                onChangeProject = onPickAnotherProject,
                onToggleTheme = onToggleTheme,
            )
        }
    }
}

private fun resolveInitialProjects(args: Array<String>): List<Path> {
    if (args.isNotEmpty()) {
        return listOf(Paths.get(args[0]).toAbsolutePath().normalize())
    }
    val saved = Settings.loadOpenProjects().filter { Files.isDirectory(it) }
    if (saved.isNotEmpty()) return saved.map { it.toAbsolutePath().normalize() }.distinct()
    val picked = pickProjectDir(initial = null) ?: return emptyList()
    return listOf(picked)
}

/** Shows a directory chooser. Returns null if the user cancelled. */
private fun pickProjectDir(initial: File?): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "nop — choose a project directory"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        currentDirectory = initial ?: File(System.getProperty("user.home"))
    }
    val res = chooser.showOpenDialog(null)
    if (res != JFileChooser.APPROVE_OPTION) return null
    val selected = chooser.selectedFile ?: return null
    return selected.toPath().toAbsolutePath().normalize()
}
