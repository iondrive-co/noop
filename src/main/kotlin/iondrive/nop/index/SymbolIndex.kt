package iondrive.nop.index

import java.nio.file.Files
import java.nio.file.Path

enum class SymbolKind {
    ANSIBLE_ROLE,
    ANSIBLE_TASKS,
    ANSIBLE_TEMPLATE,
    ANSIBLE_VAR,
    TS_SYMBOL,
    KOTLIN_SYMBOL,
}

data class IndexEntry(
    val name: String,
    /** Path relative to the project root, using forward slashes. */
    val file: String,
    /** 1-based line number of the definition. */
    val line: Int,
    val kind: SymbolKind,
)

/**
 * In-memory lookup over a list of [IndexEntry]. Stored on disk as TSV: name<TAB>file<TAB>line<TAB>kind.
 * One entry per line; lines that don't parse are silently skipped so a partially-corrupt cache file
 * doesn't take the lookup offline.
 */
class SymbolIndex(private val entries: List<IndexEntry> = emptyList()) {
    private val byName: Map<String, List<IndexEntry>> = entries.groupBy { it.name }

    val size: Int get() = entries.size

    fun lookup(name: String): List<IndexEntry> = byName[name].orEmpty()

    fun all(): List<IndexEntry> = entries

    companion object {
        fun load(path: Path): SymbolIndex {
            if (!Files.isRegularFile(path)) return SymbolIndex()
            val text = runCatching { Files.readString(path) }.getOrNull() ?: return SymbolIndex()
            val parsed = text.lineSequence().mapNotNull { parseLine(it) }.toList()
            return SymbolIndex(parsed)
        }

        fun save(path: Path, index: SymbolIndex) {
            runCatching {
                Files.createDirectories(path.parent)
                val body = index.all().joinToString("\n") {
                    "${it.name}\t${it.file}\t${it.line}\t${it.kind.name}"
                }
                Files.writeString(path, body)
            }
        }

        private fun parseLine(line: String): IndexEntry? {
            if (line.isBlank()) return null
            val parts = line.split('\t')
            if (parts.size != 4) return null
            val ln = parts[2].toIntOrNull() ?: return null
            val kind = runCatching { SymbolKind.valueOf(parts[3]) }.getOrNull() ?: return null
            return IndexEntry(parts[0], parts[1], ln, kind)
        }
    }
}
