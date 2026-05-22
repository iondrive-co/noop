package iondrive.nop.index

import java.io.File

data class JumpTarget(val file: File, val line: Int)

object JumpResolver {
    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-'

    /**
     * Returns the word straddling [offset] in [text], or null when the cursor isn't on a word.
     * Word chars are letters, digits, underscore, and hyphen — hyphens appear in Ansible role
     * names and template filenames (`adn-deploy-tool`) and we want those to round-trip.
     */
    fun wordAt(text: String, offset: Int): String? {
        val range = wordRangeAt(text, offset) ?: return null
        return text.substring(range.first, range.last + 1)
    }

    /** Half-open-style range covering the word under [offset], or null when on no word. */
    fun wordRangeAt(text: String, offset: Int): IntRange? {
        if (offset < 0 || offset > text.length) return null
        var start = offset
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = offset
        while (end < text.length && isWordChar(text[end])) end++
        if (start == end) return null
        return start..(end - 1)
    }

    /**
     * Looks up the word under [offset] and returns where to jump, or null when no match exists.
     * If multiple entries share the same name, an entry that isn't [currentFile] wins so
     * Ctrl-clicking a self-reference doesn't no-op.
     */
    fun resolve(
        index: SymbolIndex,
        projectRoot: File,
        currentFile: File?,
        text: String,
        offset: Int,
    ): JumpTarget? {
        val word = wordAt(text, offset) ?: return null
        val candidates = index.lookup(word)
        if (candidates.isEmpty()) return null
        val curAbs = currentFile?.toPath()?.toAbsolutePath()?.normalize()
        val pick = candidates.firstOrNull {
            val abs = projectRoot.toPath().resolve(it.file).toAbsolutePath().normalize()
            curAbs == null || abs != curAbs
        } ?: candidates.first()
        return JumpTarget(File(projectRoot, pick.file), pick.line)
    }
}
