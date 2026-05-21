package io.iondrive.nop.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class GitRepoTest {
    @Test
    fun `discover returns null for non-git directory`(@TempDir tmp: Path) {
        val repo = GitRepo.discover(tmp)
        assertNull(repo)
    }

    @Test
    fun `loadStatus reports modified untracked added removed`(@TempDir tmp: Path) {
        runShell(tmp, "git init -q && git config user.email t@x && git config user.name T")

        // Initial commit so we have HEAD
        (tmp / "kept.txt").writeText("kept\n")
        (tmp / "to-modify.txt").writeText("v1\n")
        (tmp / "to-delete.txt").writeText("bye\n")
        runShell(tmp, "git add -A && git commit -q -m init")

        // Now create the four kinds of change
        (tmp / "to-modify.txt").writeText("v2\n")             // MODIFIED
        (tmp / "added.txt").writeText("new staged\n")
        runShell(tmp, "git add added.txt")                    // ADDED (staged)
        runShell(tmp, "git rm -q to-delete.txt")              // REMOVED
        (tmp / "untracked.txt").writeText("?\n")              // UNTRACKED

        val repo = GitRepo.discover(tmp)
        assertNotNull(repo)
        val status = repo!!.loadStatus()
        repo.close()

        val byPath = status.byPath
        assertEquals(ChangeKind.MODIFIED, byPath["to-modify.txt"], "to-modify.txt should be modified")
        assertEquals(ChangeKind.ADDED, byPath["added.txt"], "added.txt should be added")
        assertEquals(ChangeKind.REMOVED, byPath["to-delete.txt"], "to-delete.txt should be removed")
        assertEquals(ChangeKind.UNTRACKED, byPath["untracked.txt"], "untracked.txt should be untracked")
        assertTrue(status.changes.size >= 4, "expected >=4 changes, got ${status.changes}")
    }

    @Test
    fun `stageAndCommit stages selected changes and produces a clean status`(@TempDir tmp: Path) {
        runShell(tmp, "git init -q && git config user.email t@x && git config user.name T")
        (tmp / "a.txt").writeText("a\n")
        runShell(tmp, "git add -A && git commit -q -m init")

        // Make two changes; commit one of them
        (tmp / "a.txt").writeText("a-modified\n")
        (tmp / "b.txt").writeText("new file\n")

        val repo = GitRepo.discover(tmp)!!
        val before = repo.loadStatus()
        assertEquals(2, before.changes.size)

        // Commit only a.txt
        val onlyA = before.changes.filter { it.path == "a.txt" }
        val sha = repo.stageAndCommit("touch a", onlyA)
        assertTrue(sha.isNotEmpty(), "expected commit sha")

        val after = repo.loadStatus()
        // b.txt should remain untracked; a.txt committed
        assertEquals(1, after.changes.size)
        assertEquals(ChangeKind.UNTRACKED, after.byPath["b.txt"])

        // Verify the committed file content reads back via HEAD
        val headContent = repo.readHeadContent("a.txt")
        assertEquals("a-modified\n", headContent)
        repo.close()
    }

    @Test
    fun `readHeadContent returns null for path not in HEAD`(@TempDir tmp: Path) {
        runShell(tmp, "git init -q && git config user.email t@x && git config user.name T")
        (tmp / "a.txt").writeText("a\n")
        runShell(tmp, "git add -A && git commit -q -m init")

        val repo = GitRepo.discover(tmp)!!
        assertNull(repo.readHeadContent("never-existed.txt"))
        repo.close()
    }

    @Test
    fun `clean repo reports no changes`(@TempDir tmp: Path) {
        runShell(tmp, "git init -q && git config user.email t@x && git config user.name T")
        (tmp / "a.txt").writeText("a\n")
        runShell(tmp, "git add -A && git commit -q -m init")

        val repo = GitRepo.discover(tmp)!!
        val status = repo.loadStatus()
        repo.close()

        assertTrue(status.isClean, "expected clean, got ${status.changes}")
    }

    private operator fun Path.div(name: String): Path = resolve(name)

    private fun runShell(cwd: Path, cmd: String) {
        cwd.createDirectories()
        val proc = ProcessBuilder("sh", "-c", cmd)
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        check(exit == 0) { "Command failed (exit=$exit): $cmd\n$out" }
    }
}
