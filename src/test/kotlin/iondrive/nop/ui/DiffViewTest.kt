package iondrive.nop.ui

import iondrive.nop.diff.DiffRow
import iondrive.nop.diff.RowKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiffViewTest {

    // --- helpers for building diff rows the way DiffComputer would ----------------------------
    private fun equal(line: String, n: Int) =
        DiffRow(RowKind.EQUAL, line, line, emptyList(), emptyList(), n, n)

    private fun change(old: String, new: String, oldN: Int, newN: Int) =
        DiffRow(RowKind.CHANGE, old, new, emptyList(), emptyList(), oldN, newN)

    private fun insert(new: String, newN: Int) =
        DiffRow(RowKind.INSERT, null, new, emptyList(), emptyList(), null, newN)

    private fun delete(old: String, oldN: Int) =
        DiffRow(RowKind.DELETE, old, null, emptyList(), emptyList(), oldN, null)

    @Test
    fun `replaceLine swaps a single line in the middle`() {
        val full = "alpha\nbeta\ngamma\n"
        assertEquals("alpha\nBETA\ngamma\n", replaceLine(full, 2, "BETA"))
    }

    @Test
    fun `replaceLine on the first line`() {
        val full = "alpha\nbeta\n"
        assertEquals("ALPHA\nbeta\n", replaceLine(full, 1, "ALPHA"))
    }

    @Test
    fun `replaceLine preserves the trailing blank produced by a final newline`() {
        val full = "alpha\nbeta\n"
        // split('\n') on "alpha\nbeta\n" yields ["alpha","beta",""], so the file's final newline
        // round-trips. Verify we don't lose it when patching line 1.
        val patched = replaceLine(full, 1, "alpha")
        assertEquals(full, patched)
    }

    @Test
    fun `replaceLine returns same instance when the line already matches`() {
        val full = "alpha\nbeta\n"
        val patched = replaceLine(full, 2, "beta")
        assertSame(full, patched, "no-op edits must not allocate")
    }

    @Test
    fun `replaceLine ignores out-of-range line numbers`() {
        val full = "alpha\nbeta\n"
        assertSame(full, replaceLine(full, 0, "x"))
        assertSame(full, replaceLine(full, 99, "x"))
        assertSame(full, replaceLine(full, -1, "x"))
    }

    @Test
    fun `replaceLine on a single-line file without a trailing newline`() {
        val full = "only"
        assertEquals("changed", replaceLine(full, 1, "changed"))
    }

    @Test
    fun `hunkRanges is empty when everything matches`() {
        val rows = listOf(equal("a", 1), equal("b", 2))
        assertTrue(hunkRanges(rows).isEmpty())
    }

    @Test
    fun `hunkRanges groups a single change`() {
        val rows = listOf(equal("a", 1), change("b", "B", 2, 2), equal("c", 3))
        assertEquals(listOf(1..1), hunkRanges(rows))
    }

    @Test
    fun `hunkRanges separates two runs and handles edges`() {
        // change at the very start, then equal, then a delete+insert run at the end
        val rows = listOf(
            change("a", "A", 1, 1),
            equal("b", 2),
            delete("c", 3),
            insert("d", 3),
        )
        assertEquals(listOf(0..0, 2..3), hunkRanges(rows))
    }

    @Test
    fun `revertHunk restores HEAD for a changed hunk and keeps the rest`() {
        val rows = listOf(equal("a", 1), change("b", "B", 2, 2), equal("c", 3))
        assertEquals("a\nb\nc\n", revertHunk(rows, 1..1, trailingNewline = true))
        assertEquals("a\nb\nc", revertHunk(rows, 1..1, trailingNewline = false))
    }

    @Test
    fun `revertHunk drops an inserted hunk`() {
        // HEAD: a,c  working: a,b,c — reverting the insert removes b.
        val rows = listOf(equal("a", 1), insert("b", 2), equal("c", 3))
        assertEquals("a\nc\n", revertHunk(rows, 1..1, trailingNewline = true))
    }

    @Test
    fun `revertHunk re-adds a deleted hunk`() {
        // HEAD: a,b,c  working: a,c — reverting the delete brings b back.
        val rows = listOf(equal("a", 1), delete("b", 2), equal("c", 3))
        assertEquals("a\nb\nc\n", revertHunk(rows, 1..1, trailingNewline = true))
    }

    @Test
    fun `revertHunk touches only the targeted hunk`() {
        // Two changed hunks; reverting the first leaves the second's working content intact.
        val rows = listOf(
            equal("a", 1),
            change("b", "B", 2, 2),
            equal("c", 3),
            change("d", "D", 4, 4),
            equal("e", 5),
        )
        assertEquals("a\nb\nc\nD\ne\n", revertHunk(rows, 1..1, trailingNewline = true))
        assertEquals("a\nB\nc\nd\ne\n", revertHunk(rows, 3..3, trailingNewline = true))
    }
}
