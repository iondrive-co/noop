package io.iondrive.nop.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.nio.file.Path

class GitRepo(val rootDir: Path, private val repository: Repository) : AutoCloseable {
    private val git: Git = Git.wrap(repository)

    /**
     * Stages the given changes and creates a commit. ADDED/MODIFIED/UNTRACKED paths are added
     * via `git add`; REMOVED/MISSING paths are staged for removal via `git rm --cached`.
     * Returns the new commit SHA, or throws if no changes were ultimately staged.
     */
    fun stageAndCommit(message: String, changes: Collection<FileChange>): String {
        for (change in changes) {
            when (change.kind) {
                ChangeKind.REMOVED, ChangeKind.MISSING ->
                    git.rm().setCached(true).addFilepattern(change.path).call()
                else ->
                    git.add().addFilepattern(change.path).call()
            }
        }
        val commit = git.commit().setMessage(message).call()
        return commit.name
    }

    fun loadStatus(): GitStatus {
        val status = git.status().call()
        val changes = buildList {
            status.modified.forEach { add(FileChange(it, ChangeKind.MODIFIED)) }
            status.changed.forEach { add(FileChange(it, ChangeKind.MODIFIED)) }
            status.added.forEach { add(FileChange(it, ChangeKind.ADDED)) }
            status.untracked.forEach { add(FileChange(it, ChangeKind.UNTRACKED)) }
            status.removed.forEach { add(FileChange(it, ChangeKind.REMOVED)) }
            status.missing.forEach { add(FileChange(it, ChangeKind.MISSING)) }
            status.conflicting.forEach { add(FileChange(it, ChangeKind.CONFLICT)) }
        }.distinctBy { it.path }
        val branch = repository.branch
        return GitStatus(branch = branch, changes = changes)
    }

    /** File content at HEAD for the given repo-relative path, or null if the path is absent from HEAD. */
    fun readHeadContent(relPath: String): String? {
        val head = repository.resolve("HEAD") ?: return null
        RevWalk(repository).use { walk ->
            val commit = walk.parseCommit(head)
            TreeWalk.forPath(repository, relPath, commit.tree)?.use { tw ->
                val blobId = tw.getObjectId(0)
                val loader = repository.open(blobId)
                return String(loader.bytes, Charsets.UTF_8)
            }
        }
        return null
    }

    /** File content from the working tree, or null if the file is absent. */
    fun readWorkingTreeContent(relPath: String): String? {
        val file = File(rootDir.toFile(), relPath)
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    override fun close() {
        repository.close()
    }

    companion object {
        fun discover(path: Path): GitRepo? {
            val gitDir = findGitDir(path.toFile()) ?: return null
            val repository = FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()
            return GitRepo(rootDir = gitDir.parentFile.toPath(), repository = repository)
        }

        private fun findGitDir(start: File): File? {
            var cur: File? = start.absoluteFile
            while (cur != null) {
                val candidate = File(cur, ".git")
                if (candidate.exists()) return candidate
                cur = cur.parentFile
            }
            return null
        }
    }
}
