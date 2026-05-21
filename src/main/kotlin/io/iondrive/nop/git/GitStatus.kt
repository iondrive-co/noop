package io.iondrive.nop.git

enum class ChangeKind {
    MODIFIED,
    ADDED,
    REMOVED,
    UNTRACKED,
    CONFLICT,
    MISSING,
}

data class FileChange(
    val path: String,
    val kind: ChangeKind,
)

/** One entry on the shelf — a stashed working-tree snapshot. */
data class StashEntry(
    val index: Int,
    val sha: String,
    val message: String,
)

data class GitStatus(
    val branch: String?,
    val changes: List<FileChange>,
) {
    val byPath: Map<String, ChangeKind> = changes.associate { it.path to it.kind }

    val isClean: Boolean get() = changes.isEmpty()

    companion object {
        val EMPTY = GitStatus(branch = null, changes = emptyList())
    }
}
