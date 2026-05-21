# TODO

## 1. Allow paragraph formatting and editing text in the main window

## 2. App launcher added to system so can be run from toolbar etc

## 3. Coloured diff (syntax highlighting in the diff viewer)

Today the diff viewer paints background tints per row (added/removed/changed) and per word (the inline-change span), but the source text itself is plain. Add per-token foreground colouring so keywords, strings, comments, numbers, etc. render in the usual code-editor palette.

**Recommended path: RSyntaxTextArea token-makers.** `com.fifesoft:rsyntaxtextarea` ships pure-Java lexers for roughly 60 languages and we can use the tokenizer half without touching its Swing UI. Steps:

1. Add `com.fifesoft:rsyntaxtextarea:3.5.x` to `build.gradle.kts`.
2. In `DiffComputer` (or a new sibling `SyntaxHighlighter`), look up a `TokenMaker` by file extension via `TokenMakerFactory.getDefaultInstance()`. Tokenize the full old text and the full new text once each — not line by line — since a multi-line string or block comment needs cross-line state.
3. From the tokens, derive a `List<TokenSpan>` per line: `(startChar, endChar, TokenType)`. Cache these per `DiffResult`.
4. In `DiffView.annotateLine`, merge the token spans with the existing `InlineSpan` list. Foreground colour from the token; background colour from the diff (unchanged ↔ no bg, inline-changed ↔ keep the existing `INLINE_WORD_BG*`). Build a single `AnnotatedString` per line that carries both.
5. Define a small dark palette: `keyword=#CC7832`, `string=#6A8759`, `comment=#808080`, `number=#6897BB`, `function=#FFC66D`, `type=#A9B7C6`, default `#A9B7C6`. (These match the muted darks already used for change indicators.)

**Other options considered:**
- *Tree-sitter via JNI* (`org.treesitter:*` + per-language grammars) — best fidelity, incremental, but pulls native binaries per platform and complicates packaging. Worth revisiting if a) RSyntaxTextArea coverage is insufficient or b) we want incremental rehighlighting on edit.
- *TextMate / Sublime grammars via a Java port* (`tm4j`) — broad ecosystem of `.tmLanguage.json` grammars, heavier runtime.
- *Hand-rolled regex lexers per file extension* — smallest dep footprint but unmaintainable past a handful of languages.

**Gotchas:**
- Tokenization is CPU-bound; do it on `Dispatchers.Default`, same as the diff computation, not on the UI thread.
- File extension → language mapping needs a small explicit table; trust the file path rather than sniffing content.
- For files larger than ~1 MB or rows in the thousands, consider skipping highlighting (already-large diffs hurt enough without per-token paint).
- Diff inline-change spans and syntax token spans can overlap arbitrarily — the `AnnotatedString` builder needs to emit fine-grained sub-spans where they intersect.

## Other follow-ups (lower priority)

- Horizontal scroll on diff rows so long lines don't truncate.
- Hunk-level staging (per-hunk include/exclude rather than per-file checkboxes), like `git add -p`.
- 3-way merge conflict resolution view.
- Browse commit history (a "log" tab listing past commits with their diffs).
- Push / pull / fetch — currently only local commits.
- Proper UI test harness so the commit-message text field can be driven from automated tests (the new BasicTextField/TextFieldState pipeline doesn't accept raw X11 KeyPress events).
