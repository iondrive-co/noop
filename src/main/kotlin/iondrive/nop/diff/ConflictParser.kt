package iondrive.nop.diff

/**
 * Parses a working-tree buffer carrying git merge-conflict markers into an ordered list of
 * segments, and rebuilds the buffer once a region is resolved by taking one side (or both).
 *
 * The parse is *lossless*: concatenating every segment's source text ([MergeSegment.Stable.text]
 * for stable runs, [MergeSegment.Conflict.raw] for conflict blocks) reproduces the original buffer
 * byte-for-byte, including line endings and a missing final newline. That is what lets [resolve]
 * rewrite a single conflict region while leaving everything else — other conflicts included —
 * exactly as the user (or git) wrote it.
 *
 * Marker shapes recognised (standard and diff3):
 *
 *     <<<<<<< ours-label
 *     ...ours...
 *     ||||||| base-label        (diff3 only)
 *     ...base...
 *     =======
 *     ...theirs...
 *     >>>>>>> theirs-label
 *
 * Anything that doesn't parse as a well-formed block is left as [MergeSegment.Stable] — a stray or
 * unbalanced marker never throws and never swallows surrounding content.
 */
object ConflictParser {

    enum class Choice { OURS, THEIRS, BOTH }

    sealed interface MergeSegment {
        /** A verbatim run of text outside any conflict. */
        data class Stable(val text: String) : MergeSegment

        /**
         * One conflict block. [raw] is the exact original text of the whole block (markers
         * included) so it can be re-emitted untouched; [ours]/[theirs]/[base] are the marker-free
         * bodies (each line keeps its terminator) used both for display and for resolution.
         */
        data class Conflict(
            val raw: String,
            val ours: String,
            val theirs: String,
            val base: String?,
        ) : MergeSegment
    }

    /** Cheap pre-check: does [text] contain at least one conflict start marker? */
    fun hasConflicts(text: String): Boolean = text.lineSequence().any { isStart(contentOf(it)) }

    /** Splits [text] into stable runs and conflict blocks. See the class doc for the loss-less guarantee. */
    fun parse(text: String): List<MergeSegment> {
        if (text.isEmpty()) return emptyList()
        val lines = splitKeepingEol(text)
        val segments = ArrayList<MergeSegment>()
        val stable = StringBuilder()

        fun flushStable() {
            if (stable.isNotEmpty()) {
                segments.add(MergeSegment.Stable(stable.toString()))
                stable.setLength(0)
            }
        }

        var i = 0
        while (i < lines.size) {
            if (isStart(contentOf(lines[i]))) {
                val block = tryParseBlock(lines, i)
                if (block != null) {
                    flushStable()
                    segments.add(block.segment)
                    i = block.endExclusive
                    continue
                }
            }
            stable.append(lines[i])
            i++
        }
        flushStable()
        return segments
    }

    /**
     * Rebuild the full buffer with the [regionIndex]-th conflict (0-based, counting only
     * [MergeSegment.Conflict] entries) replaced by the chosen side; [Choice.BOTH] keeps ours then
     * theirs, matching git's marker order. All other segments — stable runs and other, still
     * unresolved conflicts — are emitted verbatim.
     */
    fun resolve(segments: List<MergeSegment>, regionIndex: Int, choice: Choice): String {
        val sb = StringBuilder()
        var seen = 0
        for (seg in segments) {
            when (seg) {
                is MergeSegment.Stable -> sb.append(seg.text)
                is MergeSegment.Conflict -> {
                    if (seen == regionIndex) {
                        sb.append(
                            when (choice) {
                                Choice.OURS -> seg.ours
                                Choice.THEIRS -> seg.theirs
                                Choice.BOTH -> seg.ours + seg.theirs
                            },
                        )
                    } else {
                        sb.append(seg.raw)
                    }
                    seen++
                }
            }
        }
        return sb.toString()
    }

    private class ParsedBlock(val segment: MergeSegment.Conflict, val endExclusive: Int)

    /**
     * Attempt to read a complete conflict block starting at [start] (a `<<<<<<<` line). Returns null
     * — so the caller treats the start marker as ordinary text — if the block runs off the end or a
     * new start marker appears before it closes.
     */
    private fun tryParseBlock(lines: List<String>, start: Int): ParsedBlock? {
        val ours = StringBuilder()
        val base = StringBuilder()
        val theirs = StringBuilder()
        var sawBase = false

        // ours: from just after `<<<<<<<` up to ||||||| or =======
        var i = start + 1
        while (i < lines.size) {
            val c = contentOf(lines[i])
            if (isStart(c)) return null
            if (isBase(c) || isSep(c)) break
            ours.append(lines[i]); i++
        }
        if (i >= lines.size) return null

        // optional base (diff3): from ||||||| up to =======
        if (isBase(contentOf(lines[i]))) {
            sawBase = true
            i++
            while (i < lines.size) {
                val c = contentOf(lines[i])
                if (isStart(c)) return null
                if (isSep(c)) break
                base.append(lines[i]); i++
            }
            if (i >= lines.size) return null
        }

        // at this point lines[i] must be the ======= separator
        if (!isSep(contentOf(lines[i]))) return null
        i++

        // theirs: from after ======= up to >>>>>>>
        while (i < lines.size) {
            val c = contentOf(lines[i])
            if (isStart(c)) return null
            if (isEnd(c)) break
            theirs.append(lines[i]); i++
        }
        if (i >= lines.size) return null // never closed

        // lines[i] is the >>>>>>> end marker; include it in raw
        val endExclusive = i + 1
        val raw = buildString { for (j in start until endExclusive) append(lines[j]) }
        return ParsedBlock(
            MergeSegment.Conflict(
                raw = raw,
                ours = ours.toString(),
                theirs = theirs.toString(),
                base = if (sawBase) base.toString() else null,
            ),
            endExclusive,
        )
    }

    /**
     * Splits into physical lines, each retaining its `\n`/`\r\n` terminator, so the pieces rejoin
     * into the exact original. The final element has no terminator when the text doesn't end in one.
     */
    private fun splitKeepingEol(text: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                out.add(text.substring(start, i + 1))
                start = i + 1
            }
        }
        if (start < text.length) out.add(text.substring(start))
        return out
    }

    /** A physical line minus its trailing CR/LF, so markers can be matched on the logical content. */
    private fun contentOf(line: String): String = line.removeSuffix("\n").removeSuffix("\r")

    // Git markers are exactly seven characters; start/base/end carry a space + label, the separator
    // stands alone. The length-7-or-followed-by-space guard rejects look-alikes such as a `========`
    // comment divider (eight '=') while accepting the real `=======`.
    private fun isMarker(content: String, ch: Char): Boolean {
        if (!content.startsWith(ch.toString().repeat(7))) return false
        return content.length == 7 || content[7] == ' '
    }

    private fun isStart(content: String) = isMarker(content, '<')
    private fun isBase(content: String) = isMarker(content, '|')
    private fun isSep(content: String) = isMarker(content, '=')
    private fun isEnd(content: String) = isMarker(content, '>')
}
