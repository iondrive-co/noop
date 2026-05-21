package io.iondrive.nop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.iondrive.nop.git.GitStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.tree.ChildrenGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Text
import java.io.File
import java.nio.file.Path

private val IGNORED_DIR_NAMES = setOf(
    ".git", ".idea", ".gradle", ".vscode",
    "node_modules", "build", "out", "target", "dist", ".next", "__pycache__",
)

private fun Path.asFilteredTree(): Tree<File> = buildTree {
    val root = toFile()
    addNode(root, id = root.absolutePath) { addChildren(root) }
}

private fun ChildrenGeneratorScope<File>.addChildren(dir: File) {
    val files = dir.listFiles() ?: return
    files
        .filter { it.name !in IGNORED_DIR_NAMES && !it.isHidden }
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .forEach { file ->
            if (file.isFile) {
                addLeaf(file, id = file.absolutePath)
            } else {
                addNode(file, id = file.absolutePath) { addChildren(file) }
            }
        }
}

private fun File.relativePathTo(repoRoot: Path): String? = runCatching {
    repoRoot.toAbsolutePath().normalize().relativize(this.toPath().toAbsolutePath().normalize())
        .toString().replace(File.separatorChar, '/')
}.getOrNull()?.takeIf { !it.startsWith("..") }

@OptIn(ExperimentalJewelApi::class)
@Composable
fun ProjectTreePanel(
    projectPath: Path,
    status: GitStatus,
    onFileClick: (File) -> Unit,
) {
    val tree = remember(projectPath) { projectPath.asFilteredTree() }
    val treeState = rememberTreeState()
    val rootId = remember(projectPath) { projectPath.toFile().absolutePath }

    LaunchedEffect(rootId) {
        treeState.openNodes(listOf(rootId))
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(
            "Project",
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        )
        LazyTree(
            tree = tree,
            treeState = treeState,
            modifier = Modifier.fillMaxSize(),
            onElementClick = { element ->
                val file = element.data
                if (file.isFile) onFileClick(file)
            },
        ) { element ->
            val file: File = element.data
            val relPath = file.relativePathTo(projectPath)
            val kind = when {
                relPath == null -> null
                file.isFile -> status.byPath[relPath]
                else -> status.changes.firstOrNull { it.path.startsWith("$relPath/") }?.kind
            }
            val color = kind?.let(ChangeColors::forKind)
            if (color != null) Text(file.name, color = color) else Text(file.name)
        }
    }
}
