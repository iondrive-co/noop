package iondrive.nop.diff

import iondrive.nop.diff.ConflictParser.Choice
import iondrive.nop.diff.ConflictParser.MergeSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConflictParserTest {

    private val simple = buildString {
        append("context above\n")
        append("<<<<<<< HEAD\n")
        append("our line one\n")
        append("our line two\n")
        append("=======\n")
        append("their line\n")
        append(">>>>>>> feature\n")
        append("context below\n")
    }

    @Test
    fun `hasConflicts detects a start marker and ignores plain text`() {
        assertTrue(ConflictParser.hasConflicts(simple))
        assertFalse(ConflictParser.hasConflicts("just\nsome\nlines\n"))
        // An 8-'=' comment divider must not read as a separator, nor as a conflict.
        assertFalse(ConflictParser.hasConflicts("a\n========\nb\n"))
    }

    @Test
    fun `parse splits a simple conflict into stable, conflict, stable`() {
        val segs = ConflictParser.parse(simple)
        assertEquals(3, segs.size)
        assertEquals(MergeSegment.Stable("context above\n"), segs[0])
        val c = segs[1] as MergeSegment.Conflict
        assertEquals("our line one\nour line two\n", c.ours)
        assertEquals("their line\n", c.theirs)
        assertNull(c.base)
        assertEquals(MergeSegment.Stable("context below\n"), segs[2])
    }

    @Test
    fun `parse round-trips exactly`() {
        val segs = ConflictParser.parse(simple)
        val rebuilt = segs.joinToString("") {
            when (it) {
                is MergeSegment.Stable -> it.text
                is MergeSegment.Conflict -> it.raw
            }
        }
        assertEquals(simple, rebuilt)
    }

    @Test
    fun `parse captures the diff3 base section`() {
        val text = buildString {
            append("<<<<<<< HEAD\n")
            append("ours\n")
            append("||||||| merged common ancestors\n")
            append("base\n")
            append("=======\n")
            append("theirs\n")
            append(">>>>>>> other\n")
        }
        val c = ConflictParser.parse(text).single() as MergeSegment.Conflict
        assertEquals("ours\n", c.ours)
        assertEquals("base\n", c.base)
        assertEquals("theirs\n", c.theirs)
    }

    @Test
    fun `parse handles multiple conflicts`() {
        val text = buildString {
            append("<<<<<<< HEAD\nA\n=======\nB\n>>>>>>> x\n")
            append("middle\n")
            append("<<<<<<< HEAD\nC\n=======\nD\n>>>>>>> x\n")
        }
        val segs = ConflictParser.parse(text)
        assertEquals(3, segs.size)
        assertEquals("A\n", (segs[0] as MergeSegment.Conflict).ours)
        assertEquals(MergeSegment.Stable("middle\n"), segs[1])
        assertEquals("D\n", (segs[2] as MergeSegment.Conflict).theirs)
    }

    @Test
    fun `parse leaves a buffer without markers as a single stable segment`() {
        val text = "alpha\nbeta\n"
        assertEquals(listOf(MergeSegment.Stable(text)), ConflictParser.parse(text))
    }

    @Test
    fun `parse treats an unterminated conflict as stable text`() {
        // No ======= / >>>>>>> — the start marker must not eat the rest as a conflict.
        val text = "<<<<<<< HEAD\nours\nmore\n"
        val segs = ConflictParser.parse(text)
        assertEquals(listOf(MergeSegment.Stable(text)), segs)
    }

    @Test
    fun `resolve OURS keeps our side and drops markers`() {
        val segs = ConflictParser.parse(simple)
        val expected = "context above\nour line one\nour line two\ncontext below\n"
        assertEquals(expected, ConflictParser.resolve(segs, 0, Choice.OURS))
    }

    @Test
    fun `resolve THEIRS keeps their side and drops markers`() {
        val segs = ConflictParser.parse(simple)
        val expected = "context above\ntheir line\ncontext below\n"
        assertEquals(expected, ConflictParser.resolve(segs, 0, Choice.THEIRS))
    }

    @Test
    fun `resolve BOTH keeps ours then theirs`() {
        val segs = ConflictParser.parse(simple)
        val expected = "context above\nour line one\nour line two\ntheir line\ncontext below\n"
        assertEquals(expected, ConflictParser.resolve(segs, 0, Choice.BOTH))
    }

    @Test
    fun `resolve touches only the targeted region and leaves others marked`() {
        val text = buildString {
            append("<<<<<<< HEAD\nA\n=======\nB\n>>>>>>> x\n")
            append("middle\n")
            append("<<<<<<< HEAD\nC\n=======\nD\n>>>>>>> x\n")
        }
        val segs = ConflictParser.parse(text)
        // Resolve the second conflict only; the first keeps its markers verbatim.
        val out = ConflictParser.resolve(segs, 1, Choice.OURS)
        assertEquals("<<<<<<< HEAD\nA\n=======\nB\n>>>>>>> x\nmiddle\nC\n", out)
    }

    @Test
    fun `resolve preserves CRLF line endings`() {
        val text = "ctx\r\n<<<<<<< HEAD\r\nours\r\n=======\r\ntheirs\r\n>>>>>>> x\r\n"
        val segs = ConflictParser.parse(text)
        assertEquals("ctx\r\nours\r\n", ConflictParser.resolve(segs, 0, Choice.OURS))
    }
}
