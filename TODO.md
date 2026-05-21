# TODO

## Markdown preview pane

Add a side-by-side rendered preview for `.md` files: editor on the left, rendered
output on the right. The cleanest path is to pull in `org.commonmark:commonmark`
(plus the GFM tables and strikethrough extensions) to parse the buffer into an
AST, then write a small Compose walker that maps nodes to Jewel/Compose
composables — headings at varying `fontSize`, monospace `Text` for code blocks,
`Column` with bullets for lists, inline spans for bold/italic/links. Wire it
into `TabbedViewerPanel.kt` so that when `tab.file.extension == "md"` the
`FileEditView` is wrapped in a horizontal split with the preview on the right;
the preview re-renders off the same `edit.state.text` flow that drives
autosave, so it stays live as you type. The AST-walker approach keeps the
distributable small and the look native, at the cost of not handling images,
embedded HTML, or math without extra work — fine for a v1, and we can revisit
embedding JCEF later if full fidelity becomes important.

## Syntax highlighting

Colorize the open file in `FileEditView` (`TabbedViewerPanel.kt`) using Compose
BTF2's `OutputTransformation`: a tokenizer turns the buffer into an
`AnnotatedString` with `SpanStyle` colors per token, and the BasicTextField
renders that without changing the underlying text. Start with a small
regex-based lexer per language keyed off file extension (Kotlin, JSON,
Markdown, JS/TS, shell) — enough patterns to color keywords, strings,
comments, and numbers. Pick the lexer at tab-open time from
`tab.file.extension` and fall back to plain text for unknown extensions. The
tradeoff is correctness on edge cases (string escapes, nested comments,
embedded languages) — acceptable for v1 since the worst failure is "this
chunk isn't colored", not a crash. If the regex approach feels too rough,
escalate to reusing `rsyntaxtextarea`'s token-maker classes (Swing-only, but
the lexers are independent) or Tree-sitter via JNI for real grammars.
