package iondrive.nop.index

import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** A single match within a file. [matchStart]/[matchEnd] are char offsets within [lineText]. */
data class SearchHit(
    val path: String,
    val line: Int,
    val lineText: String,
    val matchStart: Int,
    val matchEnd: Int,
)

/**
 * Case-insensitive literal scan over the project file index. Reads each file off the EDT,
 * skipping anything too big to plausibly be source so a stray binary blob doesn't tank a search.
 *
 * The scan is cancellation-aware — call sites that re-search on every keystroke can wrap this in
 * [kotlinx.coroutines.flow.collectLatest] and trust that the prior coroutine drops out promptly.
 */
object SearchEngine {
    /** Same cutoff [iondrive.nop.index.Indexer] uses for source readability. */
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024
    /** Cap per-file matches so a query that hits "the" in a giant log doesn't OOM the panel. */
    private const val MAX_HITS_PER_FILE = 200
    /** Cap total matches so the result list stays scrollable and the UI stays responsive. */
    const val MAX_TOTAL_HITS = 2000

    suspend fun search(
        projectRoot: Path,
        files: List<String>,
        query: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): List<SearchHit> {
        if (query.isEmpty()) return emptyList()
        val root = projectRoot.toAbsolutePath().normalize().toFile()
        val needle = query.lowercase()
        return withContext(dispatcher) {
            val out = ArrayList<SearchHit>()
            for (rel in files) {
                coroutineContext.ensureActive()
                if (out.size >= MAX_TOTAL_HITS) break
                val file = File(root, rel)
                if (!file.isFile || file.length() > MAX_FILE_BYTES) continue
                val text = runCatching { file.readText() }.getOrNull() ?: continue
                scanFile(rel, text, needle, out)
            }
            out
        }
    }

    private fun scanFile(rel: String, text: String, needle: String, out: MutableList<SearchHit>) {
        var perFile = 0
        var lineNumber = 1
        var lineStart = 0
        val len = text.length
        var i = 0
        while (i <= len) {
            val isEnd = i == len
            val ch = if (isEnd) '\n' else text[i]
            if (ch == '\n') {
                // Strip a trailing \r so Windows line endings don't bleed into the highlight.
                val lineEnd = if (i > lineStart && text[i - 1] == '\r') i - 1 else i
                val line = text.substring(lineStart, lineEnd)
                val added = findInLine(rel, lineNumber, line, needle, MAX_HITS_PER_FILE - perFile, out)
                perFile += added
                if (perFile >= MAX_HITS_PER_FILE || out.size >= MAX_TOTAL_HITS) return
                lineNumber++
                lineStart = i + 1
            }
            i++
        }
    }

    private fun findInLine(
        rel: String,
        lineNumber: Int,
        line: String,
        needle: String,
        remaining: Int,
        out: MutableList<SearchHit>,
    ): Int {
        if (remaining <= 0) return 0
        val haystack = line.lowercase()
        var from = 0
        var added = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) break
            out += SearchHit(
                path = rel,
                line = lineNumber,
                lineText = line,
                matchStart = at,
                matchEnd = at + needle.length,
            )
            added++
            if (added >= remaining) break
            from = at + maxOf(needle.length, 1)
        }
        return added
    }
}
