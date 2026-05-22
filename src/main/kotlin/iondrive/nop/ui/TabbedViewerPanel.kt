package iondrive.nop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iondrive.nop.git.GitRepo
import iondrive.nop.index.JumpTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.io.File
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState
import org.jetbrains.jewel.ui.component.styling.TabIcons
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.theme.editorTabStyle

// Jewel's TabStrip pulls its close glyph from the IntelliJ Platform icons jar
// (which we don't depend on), so editor-tab close buttons render as magenta
// missing-icon placeholders. Bundle a local SVG and override the style.
private object TabIconsClass
private val TabCloseIconKey = PathIconKey("icons/close-small.svg", TabIconsClass::class.java)

/** How long to wait for the typing to settle before writing the buffer to disk. */
private const val AUTOSAVE_DEBOUNCE_MS = 400L

@OptIn(ExperimentalJewelApi::class)
@Composable
fun TabbedViewerPanel(
    tabsState: TabsState,
    repo: GitRepo?,
    editStore: FileEditStore,
    onFileSaved: () -> Unit = {},
    onResolveAt: (currentFile: File, text: String, offset: Int) -> JumpTarget? = { _, _, _ -> null },
    onJump: (File, Int) -> Unit = { _, _ -> },
) {
    val selected = tabsState.selectedTab

    if (tabsState.tabs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text("Click a file in the tree, or a change in the commit panel, to view it here")
        }
        return
    }

    val tabData = tabsState.tabs.map { tab ->
        val label = labelFor(tab, editStore)
        TabData.Editor(
            selected = tab.id == tabsState.selectedId,
            content = { state -> SimpleTabContent(label = label, state = state) },
            onClick = { tabsState.select(tab.id) },
            onClose = {
                editStore.close(tab.id)
                if (tab is Tab.LauncherOutput) tab.run.stop()
                tabsState.close(tab.id)
            },
        )
    }

    val baseTabStyle = JewelTheme.editorTabStyle
    val tabStyle = remember(baseTabStyle) {
        TabStyle(
            colors = baseTabStyle.colors,
            metrics = baseTabStyle.metrics,
            icons = TabIcons(close = TabCloseIconKey),
            contentAlpha = baseTabStyle.contentAlpha,
            scrollbarStyle = baseTabStyle.scrollbarStyle,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabStrip(tabs = tabData, style = tabStyle)
        Box(modifier = Modifier.fillMaxSize()) {
            when (val current = selected) {
                is Tab.FileView -> {
                    val pendingLine = tabsState.pendingJumpLine(current.id)
                    if (current.file.extension.equals("md", ignoreCase = true)) {
                        MarkdownEditWithPreview(current, editStore, onFileSaved)
                    } else {
                        FileEditView(
                            tab = current,
                            store = editStore,
                            onSaved = onFileSaved,
                            onResolveAt = { text, offset -> onResolveAt(current.file, text, offset) },
                            onJump = onJump,
                            pendingLine = pendingLine,
                            onPendingLineConsumed = { tabsState.clearJumpLine(current.id) },
                        )
                    }
                }
                is Tab.Diff -> if (repo != null) DiffView(repo, current)
                is Tab.History -> if (repo != null) HistoryView(repo, current)
                is Tab.LauncherOutput -> LauncherOutputView(current)
                null -> {}
            }
        }
    }
}

@Composable
private fun labelFor(tab: Tab, editStore: FileEditStore): String = when (tab) {
    is Tab.FileView -> {
        val edit = editStore.peek(tab.id)
        val base = tab.file.name
        if (edit != null && edit.isModified) "*$base" else base
    }
    is Tab.Diff -> tab.title
    is Tab.History -> tab.title
    is Tab.LauncherOutput -> tab.title
}

@OptIn(FlowPreview::class)
@Composable
private fun FileEditView(
    tab: Tab.FileView,
    store: FileEditStore,
    onSaved: () -> Unit,
    onResolveAt: (text: String, offset: Int) -> JumpTarget? = { _, _ -> null },
    onJump: (File, Int) -> Unit = { _, _ -> },
    pendingLine: Int? = null,
    onPendingLineConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val edit = remember(tab.id) { store.edit(tab) }
    val focusRequester = remember(tab.id) { FocusRequester() }
    val scrollState = rememberScrollState()
    val savedCallback by rememberUpdatedState(onSaved)
    val resolveCallback by rememberUpdatedState(onResolveAt)
    val jumpCallback by rememberUpdatedState(onJump)
    val pendingConsumedCallback by rememberUpdatedState(onPendingLineConsumed)

    // We intentionally do NOT requestFocus() on tab activation. Keeping focus on the tree means
    // tree-bound shortcuts (Delete to remove the file, H to view history) keep working after the
    // user clicks a file. Click into the editor body to start typing.

    // Autosave: debounce edits, write to disk on a background thread, then notify the rest of
    // the app (commit panel) that on-disk state may have changed.
    LaunchedEffect(edit) {
        snapshotFlow { edit.state.text.toString() }
            .drop(1) // skip the initial value already in sync with disk
            .debounce(AUTOSAVE_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { text ->
                if (text != edit.savedText) {
                    withContext(Dispatchers.IO) { edit.save() }
                    savedCallback()
                }
            }
    }

    val isDark = JewelTheme.isDark
    val fg = if (isDark) androidx.compose.ui.graphics.Color(0xFFA9B7C6) else androidx.compose.ui.graphics.Color(0xFF1F2329)
    val palette = if (isDark) HighlightPalette.Dark else HighlightPalette.Light
    val tokenize = remember(tab.id) { tokenizerForExtension(tab.file.extension) }
    val transformation = remember(tokenize, palette) { tokenize?.let { highlightTransformation(it, palette) } }

    // We keep the text layout around so Ctrl-click can map mouse coordinates to text offsets,
    // and so an inbound jump request can scroll a target line to the top of the viewport.
    var layout by remember(tab.id) { mutableStateOf<TextLayoutResult?>(null) }

    // Inbound jump: once the layout for this tab exists, scroll the requested line to ~3 lines
    // below the top so the user can see context around the landing site. Also drop the cursor
    // at the line start so the visual anchor is obvious.
    LaunchedEffect(tab.id, layout, pendingLine) {
        val tl = layout ?: return@LaunchedEffect
        val line = pendingLine ?: return@LaunchedEffect
        val safe = (line - 1).coerceIn(0, maxOf(0, tl.lineCount - 1))
        val lineHeight = (tl.getLineBottom(0) - tl.getLineTop(0)).coerceAtLeast(1f)
        val targetTop = (tl.getLineTop(safe) - lineHeight * 3).toInt().coerceAtLeast(0)
        scrollState.scrollTo(targetTop)
        val lineStart = tl.getLineStart(safe)
        edit.state.edit { selection = TextRange(lineStart) }
        pendingConsumedCallback()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(12.dp),
    ) {
        BasicTextField(
            state = edit.state,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp)
                .focusRequester(focusRequester)
                // Ctrl-click → jump-to-source. We run on the Initial pass and consume the change
                // when we have a target, so the field doesn't also move the cursor to the click
                // site. Bare clicks fall through unchanged and place the cursor as usual.
                .pointerInput(tab.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Press) continue
                            if (!event.keyboardModifiers.isCtrlPressed) continue
                            val change = event.changes.firstOrNull() ?: continue
                            val tl = layout ?: continue
                            val offset = tl.getOffsetForPosition(change.position)
                            val target = resolveCallback(edit.state.text.toString(), offset)
                            if (target != null) {
                                change.consume()
                                jumpCallback(target.file, target.line)
                            }
                        }
                    }
                },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = fg,
            ),
            cursorBrush = SolidColor(fg),
            lineLimits = TextFieldLineLimits.MultiLine(),
            scrollState = scrollState,
            outputTransformation = transformation,
            onTextLayout = { getResult ->
                val r = getResult()
                if (r != null) layout = r
            },
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            style = NopScrollbarStyle,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.CenterEnd)
                .width(10.dp)
                .fillMaxHeight(),
        )
    }
}

/** Markdown tab layout: editor on the left, live-rendered preview on the right. */
@Composable
private fun MarkdownEditWithPreview(
    tab: Tab.FileView,
    store: FileEditStore,
    onSaved: () -> Unit,
) {
    val edit = remember(tab.id) { store.edit(tab) }
    val previewText by remember(edit) {
        derivedStateOf { edit.state.text.toString() }
    }
    HorizontalSplitLayout(
        first = { FileEditView(tab, store, onSaved) },
        second = {
            MarkdownPreview(
                text = previewText,
                modifier = Modifier.fillMaxSize(),
            )
        },
        state = rememberSplitLayoutState(0.5f),
        modifier = Modifier.fillMaxSize(),
    )
}
