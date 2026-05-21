package io.iondrive.nop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.iondrive.nop.git.GitRepo
import io.iondrive.nop.git.GitStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.VerticalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState
import java.nio.file.Path

@Composable
fun App(projectPath: Path) {
    val repo: GitRepo? = remember(projectPath) { GitRepo.discover(projectPath) }
    DisposableEffect(repo) { onDispose { repo?.close() } }

    var status by remember { mutableStateOf(GitStatus.EMPTY) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var commitInFlight by remember { mutableStateOf(false) }
    val tabsState = remember { TabsState() }
    val scope = rememberCoroutineScope()

    suspend fun reloadStatus() {
        if (repo != null) {
            val fresh = withContext(Dispatchers.IO) { repo.loadStatus() }
            status = fresh
            // Default-select every change after a reload
            selectedPaths = fresh.changes.map { it.path }.toSet()
        }
    }

    LaunchedEffect(repo) { reloadStatus() }

    val rootPath = repo?.rootDir ?: projectPath

    Box(
        modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground),
    ) {
        HorizontalSplitLayout(
            first = {
                ProjectTreePanel(
                    projectPath = rootPath,
                    status = status,
                    onFileClick = { tabsState.open(Tab.FileView(it)) },
                )
            },
            second = {
                VerticalSplitLayout(
                    first = { TabbedViewerPanel(tabsState, repo) },
                    second = {
                        CommitPanel(
                            status = status,
                            selectedPaths = selectedPaths,
                            onToggle = { path ->
                                selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                            },
                            onChangeClick = { change ->
                                if (repo != null) {
                                    tabsState.open(Tab.Diff(change, repo.rootDir.toFile()))
                                }
                            },
                            onCommit = { message, included ->
                                if (repo != null && !commitInFlight) {
                                    scope.launch {
                                        commitInFlight = true
                                        try {
                                            withContext(Dispatchers.IO) {
                                                repo.stageAndCommit(message, included)
                                            }
                                            reloadStatus()
                                        } finally {
                                            commitInFlight = false
                                        }
                                    }
                                }
                            },
                            commitInFlight = commitInFlight,
                        )
                    },
                    state = rememberSplitLayoutState(0.55f),
                )
            },
            state = rememberSplitLayoutState(0.22f),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
