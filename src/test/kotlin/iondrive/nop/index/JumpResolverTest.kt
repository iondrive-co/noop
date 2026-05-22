package iondrive.nop.index

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JumpResolverTest {

    @Test
    fun `wordAt returns the word straddling the offset`() {
        val text = "hello world"
        assertEquals("hello", JumpResolver.wordAt(text, 0))
        assertEquals("hello", JumpResolver.wordAt(text, 3))
        // offset 5 lands on the trailing boundary of "hello" — still returns the preceding word.
        assertEquals("hello", JumpResolver.wordAt(text, 5))
        assertEquals("world", JumpResolver.wordAt(text, 7))
    }

    @Test
    fun `wordRangeAt returns inclusive bounds matching wordAt`() {
        val text = "foo-bar baz"
        val range = JumpResolver.wordRangeAt(text, 2)!!
        assertEquals(0, range.first)
        assertEquals(6, range.last)
        assertEquals("foo-bar", text.substring(range.first, range.last + 1))
    }

    @Test
    fun `wordRangeAt is null when both sides are non-word characters`() {
        // Two-space gap; offset 4 is whitespace with whitespace on either side.
        assertNull(JumpResolver.wordRangeAt("abc  def", 4))
    }

    @Test
    fun `wordRangeAt tolerates out-of-bounds offsets`() {
        assertNull(JumpResolver.wordRangeAt("abc", -1))
        assertNull(JumpResolver.wordRangeAt("abc", 99))
    }

    @Test
    fun `wordRangeAt includes underscores and hyphens`() {
        val text = "my_role-name = 1"
        val range = JumpResolver.wordRangeAt(text, 4)!!
        assertEquals("my_role-name", text.substring(range.first, range.last + 1))
    }
}
