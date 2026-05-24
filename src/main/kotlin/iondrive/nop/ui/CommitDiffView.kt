package iondrive.nop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iondrive.nop.diff.DiffComputer
import iondrive.nop.diff.DiffResult
import iondrive.nop.diff.DiffRow
import iondrive.nop.diff.InlineSpan
import iondrive.nop.diff.RowKind
import iondrive.nop.git.CommitFileChange
import iondrive.nop.git.GitRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text

private val INSERT_BG = Color(0x33629755)
private val DELETE_BG = Color(0x33B35E5E)
private val CHANGE_BG = Color(0x33547B9D)
private val EMPTY_BG = Color(0x14FFFFFF)
private val INLINE_WORD_BG = Color(0x66629755)
private val INLINE_WORD_BG_OLD = Color(0x66B35E5E)
private val GUTTER_FG = Color(0xFF808080)
private val INSERT_MARK = Color(0xFF7DBE6E)
private val DELETE_MARK = Color(0xFFD96B6B)
private val CHANGE_MARK = Color(0xFF6FA8DC)
private val TEXT_FG = Color(0xFFA9B7C6)

@Composable
fun CommitDiffView(repo: GitRepo, tab: Tab.CommitDiff) {
    var loading by remember(tab.id) { mutableStateOf(true) }
    var error by remember(tab.id) { mutableStateOf<String?>(null) }
    var result by remember(tab.id) { mutableStateOf<DiffResult?>(null) }

    LaunchedEffect(tab.id) {
        try {
            val (oldText, newText) = withContext(Dispatchers.IO) {
                val parentRev = "${tab.sha}^"
                val old = when (tab.file.changeType) {
                    CommitFileChange.ADDED -> ""
                    else -> repo.readContentAt(parentRev, tab.file.path) ?: ""
                }
                val new = when (tab.file.changeType) {
                    CommitFileChange.DELETED -> ""
                    else -> repo.readContentAt(tab.sha, tab.file.path) ?: ""
                }
                old to new
            }
            result = withContext(Dispatchers.Default) { DiffComputer.compute(oldText, newText) }
            loading = false
        } catch (t: Throwable) {
            error = t.message ?: t::class.simpleName
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) { Text("Loading diff…") }
        error != null -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) { Text("Could not load diff: $error") }
        result != null -> ReadOnlyDiffList(result!!)
    }
}

@Composable
private fun ReadOnlyDiffList(result: DiffResult) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 14.dp),
        ) {
            items(result.rows, key = { row ->
                when {
                    row.newLineNumber != null -> "n${row.newLineNumber}"
                    row.oldLineNumber != null -> "o${row.oldLineNumber}"
                    else -> "x${result.rows.indexOf(row)}"
                }
            }) { row ->
                ReadOnlyDiffRowView(row)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.width(4.dp).fillMaxHeight()) {
                drawChangeMarkers(result.rows)
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                style = NopScrollbarStyle,
                modifier = Modifier.width(10.dp).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ReadOnlyDiffRowView(row: DiffRow) {
    val (oldBg, newBg) = backgroundsFor(row)
    Row(
        modifier = Modifier.fillMaxWidth().height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ReadOnlyHalf(
            text = row.oldLine,
            spans = row.oldSpans,
            lineNumber = row.oldLineNumber,
            background = oldBg,
            inlineHighlight = INLINE_WORD_BG_OLD,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).fillMaxSize().background(Color(0x33FFFFFF)))
        ReadOnlyHalf(
            text = row.newLine,
            spans = row.newSpans,
            lineNumber = row.newLineNumber,
            background = newBg,
            inlineHighlight = INLINE_WORD_BG,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReadOnlyHalf(
    text: String?,
    spans: List<InlineSpan>,
    lineNumber: Int?,
    background: Color,
    inlineHighlight: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize().background(background),
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(
            text = lineNumber?.toString()?.padStart(5) ?: "     ",
            style = TextStyle(color = GUTTER_FG, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        BasicText(
            text = annotateLine(text ?: "", spans, inlineHighlight),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TEXT_FG),
            softWrap = false,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

private fun annotateLine(text: String, spans: List<InlineSpan>, highlightColor: Color): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        for (s in spans) {
            val start = s.startChar.coerceIn(0, text.length)
            val end = s.endCharExclusive.coerceIn(start, text.length)
            if (end == start) continue
            val piece = text.substring(start, end)
            if (s.changed) {
                withStyle(SpanStyle(background = highlightColor)) { append(piece) }
            } else {
                append(piece)
            }
        }
    }
}

private fun backgroundsFor(row: DiffRow): Pair<Color, Color> = when (row.kind) {
    RowKind.EQUAL -> Color.Transparent to Color.Transparent
    RowKind.CHANGE -> CHANGE_BG to CHANGE_BG
    RowKind.INSERT -> EMPTY_BG to INSERT_BG
    RowKind.DELETE -> DELETE_BG to EMPTY_BG
}

private fun DrawScope.drawChangeMarkers(rows: List<DiffRow>) {
    val n = rows.size
    if (n == 0) return
    val markerH = (size.height / n).coerceAtLeast(3f)
    val w = size.width
    rows.forEachIndexed { idx, row ->
        val color = when (row.kind) {
            RowKind.EQUAL -> return@forEachIndexed
            RowKind.INSERT -> INSERT_MARK
            RowKind.DELETE -> DELETE_MARK
            RowKind.CHANGE -> CHANGE_MARK
        }
        val y = (idx.toFloat() / n) * size.height
        drawRect(color = color, topLeft = Offset(0f, y), size = Size(w, markerH))
    }
}
