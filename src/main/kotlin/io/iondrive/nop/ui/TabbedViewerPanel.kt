package io.iondrive.nop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.iondrive.nop.git.GitRepo
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.editorTabStyle

@OptIn(ExperimentalJewelApi::class)
@Composable
fun TabbedViewerPanel(tabsState: TabsState, repo: GitRepo?) {
    val selected = tabsState.selectedTab

    if (tabsState.tabs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Click a file in the tree, or a change in the commit panel, to view it here")
        }
        return
    }

    val tabData = tabsState.tabs.map { tab ->
        TabData.Editor(
            selected = tab.id == tabsState.selectedId,
            content = { state -> SimpleTabContent(label = tab.title, state = state) },
            onClick = { tabsState.select(tab.id) },
            onClose = { tabsState.close(tab.id) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabStrip(tabs = tabData, style = JewelTheme.editorTabStyle)
        Box(modifier = Modifier.fillMaxSize()) {
            when (val current = selected) {
                is Tab.FileView -> FileContentView(current)
                is Tab.Diff -> if (repo != null) DiffView(repo, current)
                null -> {}
            }
        }
    }
}

@Composable
private fun FileContentView(tab: Tab.FileView) {
    val scroll = rememberScrollState()
    val text = remember(tab.id) {
        runCatching { tab.file.readText() }.getOrElse { "<<unable to read: ${it.message}>>" }
    }
    Box(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp)) {
        Text(text)
    }
}
