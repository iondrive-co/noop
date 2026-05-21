package io.iondrive.nop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.iondrive.nop.git.FileChange
import io.iondrive.nop.git.GitStatus
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommitPanel(
    status: GitStatus,
    selectedPaths: Set<String>,
    onToggle: (String) -> Unit,
    onChangeClick: (FileChange) -> Unit,
    onCommit: (message: String, included: List<FileChange>) -> Unit,
    commitInFlight: Boolean,
) {
    val messageState = remember { TextFieldState() }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val header = when {
            status.isClean && status.branch == null -> "Not a git repository"
            status.isClean -> "Commit — on ${status.branch} · no changes"
            else -> "Commit — on ${status.branch} · ${selectedPaths.size}/${status.changes.size} selected"
        }
        Text(header, modifier = Modifier.padding(bottom = 6.dp))

        if (status.branch != null && !status.isClean) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextArea(
                    state = messageState,
                    placeholder = { Text("Commit message") },
                    modifier = Modifier.weight(1f).height(64.dp),
                )
                DefaultButton(
                    onClick = {
                        val msg = messageState.text.toString().trim()
                        if (msg.isNotEmpty()) {
                            val included = status.changes.filter { it.path in selectedPaths }
                            onCommit(msg, included)
                            messageState.clearText()
                        }
                    },
                    enabled = !commitInFlight && messageState.text.toString().isNotBlank() && selectedPaths.isNotEmpty(),
                ) {
                    Text(if (commitInFlight) "Committing…" else "Commit")
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(status.changes) { change ->
                ChangeRow(
                    change = change,
                    checked = change.path in selectedPaths,
                    onToggle = { onToggle(change.path) },
                    onPathClick = { onChangeClick(change) },
                )
            }
        }
    }
}

private fun TextFieldState.clearText() {
    edit { replace(0, length, "") }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChangeRow(
    change: FileChange,
    checked: Boolean,
    onToggle: () -> Unit,
    onPathClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CheckboxRow(checked = checked, onCheckedChange = { onToggle() }) {}
        Text(ChangeColors.prefixFor(change.kind), color = ChangeColors.forKind(change.kind))
        Text(
            change.path,
            modifier = Modifier.weight(1f).onClick(onClick = onPathClick),
        )
    }
}
