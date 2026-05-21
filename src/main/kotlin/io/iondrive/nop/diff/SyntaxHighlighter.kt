package io.iondrive.nop.diff

import androidx.compose.ui.graphics.Color
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenTypes
import javax.swing.text.Segment

data class TokenSpan(
    val startChar: Int,
    val endCharExclusive: Int,
    val tokenType: Int,
)

object SyntaxHighlighter {
    private val factory: TokenMakerFactory = TokenMakerFactory.getDefaultInstance()

    // Skip highlighting for very large files — the per-line tokenizer is fine, but a multi-MB
    // file inflates the cached span list and slows the diff render for marginal value.
    private const val MAX_HIGHLIGHT_CHARS = 1_000_000

    fun syntaxFor(path: String): String {
        val name = path.substringAfterLast('/').lowercase()
        if (name == "dockerfile") return SyntaxConstants.SYNTAX_STYLE_DOCKERFILE
        if (name == "makefile") return SyntaxConstants.SYNTAX_STYLE_MAKEFILE
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        return when (ext) {
            "kt", "kts" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN
            "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA
            "groovy", "gradle" -> SyntaxConstants.SYNTAX_STYLE_GROOVY
            "scala" -> SyntaxConstants.SYNTAX_STYLE_SCALA
            "py", "pyw" -> SyntaxConstants.SYNTAX_STYLE_PYTHON
            "rb" -> SyntaxConstants.SYNTAX_STYLE_RUBY
            "js", "mjs", "cjs", "jsx" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
            "ts", "tsx" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT
            "html", "htm" -> SyntaxConstants.SYNTAX_STYLE_HTML
            "xml", "xhtml", "svg" -> SyntaxConstants.SYNTAX_STYLE_XML
            "json" -> SyntaxConstants.SYNTAX_STYLE_JSON
            "yaml", "yml" -> SyntaxConstants.SYNTAX_STYLE_YAML
            "md", "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN
            "sh", "bash", "zsh" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL
            "css" -> SyntaxConstants.SYNTAX_STYLE_CSS
            "scss", "sass" -> SyntaxConstants.SYNTAX_STYLE_CSS
            "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL
            "go" -> SyntaxConstants.SYNTAX_STYLE_GO
            "rs" -> SyntaxConstants.SYNTAX_STYLE_RUST
            "c", "h" -> SyntaxConstants.SYNTAX_STYLE_C
            "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS
            "cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP
            "lua" -> SyntaxConstants.SYNTAX_STYLE_LUA
            "pl", "pm" -> SyntaxConstants.SYNTAX_STYLE_PERL
            "php" -> SyntaxConstants.SYNTAX_STYLE_PHP
            "properties" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
            "toml" -> SyntaxConstants.SYNTAX_STYLE_INI
            "ini" -> SyntaxConstants.SYNTAX_STYLE_INI
            "dart" -> SyntaxConstants.SYNTAX_STYLE_DART
            else -> SyntaxConstants.SYNTAX_STYLE_NONE
        }
    }

    /**
     * Tokenizes [text] into a per-line list of spans (offsets relative to that line). Multi-line
     * strings and block comments are tracked by threading the end-of-line token type back as the
     * next line's initial token type — exactly how `RSyntaxDocument` does it internally.
     *
     * Returns one entry per source line, indexed 0-based, matching [DiffComputer.splitLines].
     */
    fun tokenizeByLine(text: String, syntax: String): List<List<TokenSpan>> {
        val lines = splitLines(text)
        if (lines.isEmpty()) return emptyList()
        if (syntax == SyntaxConstants.SYNTAX_STYLE_NONE) return lines.map { emptyList() }
        if (text.length > MAX_HIGHLIGHT_CHARS) return lines.map { emptyList() }
        val tm = runCatching { factory.getTokenMaker(syntax) }.getOrNull() ?: return lines.map { emptyList() }

        val out = ArrayList<List<TokenSpan>>(lines.size)
        var initialType = TokenTypes.NULL
        for (line in lines) {
            val arr = line.toCharArray()
            val seg = Segment(arr, 0, arr.size)
            val head: Token? = tm.getTokenList(seg, initialType, 0)
            out.add(collectSpans(head, line.length))
            initialType = endTokenType(head)
        }
        return out
    }

    private fun collectSpans(head: Token?, lineLength: Int): List<TokenSpan> {
        if (head == null) return emptyList()
        val spans = ArrayList<TokenSpan>()
        var t: Token? = head
        while (t != null && t.isPaintable) {
            val start = t.offset
            val end = (t.offset + t.length()).coerceAtMost(lineLength)
            if (end > start && start >= 0) {
                spans.add(TokenSpan(start, end, t.type))
            }
            t = t.nextToken
        }
        return spans
    }

    private fun endTokenType(head: Token?): Int {
        var last: Token? = head ?: return TokenTypes.NULL
        var next = last?.nextToken
        while (next != null) {
            last = next
            next = next.nextToken
        }
        val type = last?.type ?: TokenTypes.NULL
        return if (type < 0) TokenTypes.NULL else type
    }

    private fun splitLines(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        val parts = s.split("\r\n", "\n")
        return if (s.endsWith("\n") && parts.lastOrNull() == "") parts.dropLast(1) else parts
    }

    // Dark-theme palette — keep these close to the muted darks already used for change indicators
    private val KEYWORD = Color(0xFFCC7832)
    private val STRING = Color(0xFF6A8759)
    private val COMMENT = Color(0xFF808080)
    private val NUMBER = Color(0xFF6897BB)
    private val FUNCTION = Color(0xFFFFC66D)
    private val TYPE = Color(0xFFA9B7C6)
    private val ANNOTATION = Color(0xFFBBB529)
    private val PREPROCESSOR = Color(0xFF9876AA)
    private val MARKUP_ATTR = Color(0xFFBABABA)
    private val ERROR = Color(0xFFFF6B68)
    val DEFAULT_FG = Color(0xFFA9B7C6)

    fun colorFor(tokenType: Int): Color = when (tokenType) {
        TokenTypes.COMMENT_EOL,
        TokenTypes.COMMENT_MULTILINE,
        TokenTypes.COMMENT_DOCUMENTATION,
        TokenTypes.COMMENT_KEYWORD,
        TokenTypes.COMMENT_MARKUP,
        TokenTypes.MARKUP_COMMENT,
        -> COMMENT

        TokenTypes.RESERVED_WORD,
        TokenTypes.RESERVED_WORD_2,
        -> KEYWORD

        TokenTypes.FUNCTION -> FUNCTION

        TokenTypes.LITERAL_BOOLEAN,
        TokenTypes.LITERAL_NUMBER_DECIMAL_INT,
        TokenTypes.LITERAL_NUMBER_FLOAT,
        TokenTypes.LITERAL_NUMBER_HEXADECIMAL,
        -> NUMBER

        TokenTypes.LITERAL_STRING_DOUBLE_QUOTE,
        TokenTypes.LITERAL_CHAR,
        TokenTypes.LITERAL_BACKQUOTE,
        -> STRING

        TokenTypes.DATA_TYPE -> TYPE
        TokenTypes.ANNOTATION -> ANNOTATION
        TokenTypes.PREPROCESSOR -> PREPROCESSOR
        TokenTypes.REGEX -> STRING

        TokenTypes.MARKUP_TAG_DELIMITER,
        TokenTypes.MARKUP_TAG_NAME,
        -> KEYWORD

        TokenTypes.MARKUP_TAG_ATTRIBUTE -> MARKUP_ATTR
        TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE -> STRING
        TokenTypes.MARKUP_PROCESSING_INSTRUCTION -> PREPROCESSOR
        TokenTypes.MARKUP_ENTITY_REFERENCE -> NUMBER
        TokenTypes.MARKUP_CDATA,
        TokenTypes.MARKUP_CDATA_DELIMITER,
        TokenTypes.MARKUP_DTD,
        -> TYPE

        TokenTypes.ERROR_IDENTIFIER,
        TokenTypes.ERROR_NUMBER_FORMAT,
        TokenTypes.ERROR_STRING_DOUBLE,
        TokenTypes.ERROR_CHAR,
        -> ERROR

        else -> DEFAULT_FG
    }
}
