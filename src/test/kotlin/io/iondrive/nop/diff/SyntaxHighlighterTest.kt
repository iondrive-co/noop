package io.iondrive.nop.diff

import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyntaxHighlighterTest {
    @Test
    fun `syntax style is picked from extension`() {
        assertEquals(SyntaxConstants.SYNTAX_STYLE_KOTLIN, SyntaxHighlighter.syntaxFor("a/b/foo.kt"))
        assertEquals(SyntaxConstants.SYNTAX_STYLE_KOTLIN, SyntaxHighlighter.syntaxFor("build.gradle.kts"))
        assertEquals(SyntaxConstants.SYNTAX_STYLE_JAVA, SyntaxHighlighter.syntaxFor("X.java"))
        assertEquals(SyntaxConstants.SYNTAX_STYLE_PYTHON, SyntaxHighlighter.syntaxFor("script.py"))
        assertEquals(SyntaxConstants.SYNTAX_STYLE_NONE, SyntaxHighlighter.syntaxFor("README"))
    }

    @Test
    fun `unknown extension produces empty spans for every line`() {
        val tokens = SyntaxHighlighter.tokenizeByLine("hello\nworld\n", SyntaxConstants.SYNTAX_STYLE_NONE)
        assertEquals(2, tokens.size)
        assertTrue(tokens.all { it.isEmpty() })
    }

    @Test
    fun `kotlin keywords are tagged as RESERVED_WORD`() {
        val src = "fun main() { val x = 1 }\n"
        val tokens = SyntaxHighlighter.tokenizeByLine(src, SyntaxConstants.SYNTAX_STYLE_KOTLIN)
        assertEquals(1, tokens.size)
        val line = tokens[0]
        // The two keywords on this line are `fun` and `val`. Collect tagged-text by type.
        val keywordTexts = line.filter { it.tokenType == org.fife.ui.rsyntaxtextarea.TokenTypes.RESERVED_WORD }
            .map { src.substring(it.startChar, it.endCharExclusive) }
        assertTrue(keywordTexts.contains("fun"), "expected 'fun' tagged as keyword; got $keywordTexts")
        assertTrue(keywordTexts.contains("val"), "expected 'val' tagged as keyword; got $keywordTexts")
    }

    @Test
    fun `multi-line block comment carries state across lines`() {
        val src = "/* line one\n   still in comment\n   final */ code\n"
        val tokens = SyntaxHighlighter.tokenizeByLine(src, SyntaxConstants.SYNTAX_STYLE_JAVA)
        assertEquals(3, tokens.size)
        // The middle line is wholly inside the multi-line comment — there should be at least one
        // comment-typed span covering its content.
        val midLineComment = tokens[1].any {
            it.tokenType == org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_MULTILINE
        }
        assertTrue(midLineComment, "middle line should be inside a multi-line comment span: ${tokens[1]}")
    }
}
