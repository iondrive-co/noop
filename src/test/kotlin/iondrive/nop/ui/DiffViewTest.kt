package iondrive.nop.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DiffViewTest {

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
}
