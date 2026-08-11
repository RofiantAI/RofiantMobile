package ca.rofiant.app.ui.components

// Kotlin port of rofiant-desktop's src/lib/syntax-highlight.ts — a
// lightweight regex tokenizer, not a full grammar. Good enough coverage for
// common languages inside a chat code block without pulling in a highlighting
// library.

enum class TokenType { Keyword, StringLit, Comment, Number, Function, Text }

data class Token(val type: TokenType, val text: String)

private object Keywords {
    val cLike = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "class", "extends", "implements", "new",
        "this", "super", "import", "export", "default", "from", "as", "async", "await",
        "try", "catch", "finally", "throw", "typeof", "instanceof", "in", "of", "null",
        "undefined", "true", "false", "void", "static", "public", "private", "protected",
        "interface", "type", "enum", "readonly", "namespace", "declare", "yield", "delete",
    )
    val python = setOf(
        "def", "return", "if", "elif", "else", "for", "while", "break", "continue", "class",
        "import", "from", "as", "try", "except", "finally", "raise", "with", "lambda",
        "None", "True", "False", "and", "or", "not", "in", "is", "pass", "yield", "async",
        "await", "global", "nonlocal", "assert", "del", "self",
    )
    val rust = setOf(
        "fn", "let", "mut", "const", "return", "if", "else", "match", "for", "while", "loop",
        "break", "continue", "struct", "enum", "impl", "trait", "pub", "use", "mod", "crate",
        "self", "Self", "super", "as", "where", "dyn", "async", "await", "move", "unsafe",
        "true", "false", "None", "Some", "Ok", "Err", "static", "ref",
    )
    val go = setOf(
        "func", "return", "if", "else", "for", "range", "break", "continue", "switch", "case",
        "default", "struct", "interface", "package", "import", "var", "const", "type", "go",
        "chan", "select", "defer", "map", "true", "false", "nil", "iota",
    )
    val java = setOf(
        "public", "private", "protected", "class", "interface", "extends", "implements",
        "static", "final", "void", "new", "return", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "try", "catch", "finally", "throw", "throws",
        "import", "package", "this", "super", "true", "false", "null", "enum", "abstract",
    )
    val ruby = setOf(
        "def", "end", "return", "if", "elsif", "else", "unless", "for", "while", "break",
        "next", "class", "module", "require", "require_relative", "attr_accessor", "do",
        "true", "false", "nil", "self", "yield", "begin", "rescue", "ensure", "raise",
    )
    val bash = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "case",
        "esac", "function", "return", "echo", "export", "local", "set", "break", "continue",
    )
    val sql = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "create", "table", "alter", "drop", "join", "left", "right", "inner", "outer", "on",
        "group", "by", "order", "having", "limit", "and", "or", "not", "null", "as", "distinct",
    )
}

private val LANG_ALIASES = mapOf(
    "js" to "cLike", "jsx" to "cLike", "ts" to "cLike", "tsx" to "cLike",
    "javascript" to "cLike", "typescript" to "cLike", "mjs" to "cLike", "cjs" to "cLike",
    "c" to "java", "cpp" to "java", "c++" to "java", "cs" to "java", "csharp" to "java",
    "swift" to "cLike", "kotlin" to "cLike",
    "py" to "python", "python" to "python", "python3" to "python",
    "rs" to "rust", "rust" to "rust",
    "go" to "go", "golang" to "go",
    "java" to "java",
    "rb" to "ruby", "ruby" to "ruby",
    "sh" to "bash", "bash" to "bash", "shell" to "bash", "zsh" to "bash",
    "sql" to "sql",
    "json" to "json", "jsonc" to "json",
    "css" to "css", "scss" to "css", "less" to "css",
    "html" to "html", "xml" to "html", "svg" to "html",
)

private fun keywordsFor(family: String): Set<String>? = when (family) {
    "cLike" -> Keywords.cLike
    "python" -> Keywords.python
    "rust" -> Keywords.rust
    "go" -> Keywords.go
    "java" -> Keywords.java
    "ruby" -> Keywords.ruby
    "bash" -> Keywords.bash
    "sql" -> Keywords.sql
    else -> null
}

private data class CommentStyle(val line: Regex? = null, val block: Regex? = null)

private fun commentsFor(family: String): CommentStyle? = when (family) {
    "cLike", "java", "go", "rust" -> CommentStyle(line = Regex("//[^\\n]*"), block = Regex("/\\*[\\s\\S]*?(\\*/|$)"))
    "css" -> CommentStyle(block = Regex("/\\*[\\s\\S]*?(\\*/|$)"))
    "python", "bash", "ruby" -> CommentStyle(line = Regex("#[^\\n]*"))
    "sql" -> CommentStyle(line = Regex("--[^\\n]*"))
    else -> null
}

private val STRING_RE = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`")
private val NUMBER_RE = Regex("\\b0[xXbBoO][0-9a-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b")
private val IDENT_RE = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

/** Matches [regex] only if it succeeds starting exactly at [at] (poor man's sticky flag). */
private fun matchAt(regex: Regex, code: String, at: Int): MatchResult? {
    val m = regex.find(code, at) ?: return null
    return if (m.range.first == at) m else null
}

fun tokenize(code: String, lang: String): List<Token> {
    val family = LANG_ALIASES[lang.lowercase()] ?: return listOf(Token(TokenType.Text, code))
    if (family == "html") return listOf(Token(TokenType.Text, code))

    val keywords = if (family == "json" || family == "css") null else keywordsFor(family)
    val comments = commentsFor(family)
    val tokens = mutableListOf<Token>()
    var i = 0

    fun push(type: TokenType, text: String) {
        val last = tokens.lastOrNull()
        if (last != null && last.type == type) {
            tokens[tokens.lastIndex] = last.copy(text = last.text + text)
        } else {
            tokens.add(Token(type, text))
        }
    }

    while (i < code.length) {
        var matched = false

        comments?.block?.let { re ->
            matchAt(re, code, i)?.let { m ->
                push(TokenType.Comment, m.value)
                i += m.value.length
                matched = true
            }
        }
        if (!matched) comments?.line?.let { re ->
            matchAt(re, code, i)?.let { m ->
                push(TokenType.Comment, m.value)
                i += m.value.length
                matched = true
            }
        }
        if (!matched) matchAt(STRING_RE, code, i)?.let { m ->
            push(TokenType.StringLit, m.value)
            i += m.value.length
            matched = true
        }
        if (!matched) matchAt(NUMBER_RE, code, i)?.let { m ->
            push(TokenType.Number, m.value)
            i += m.value.length
            matched = true
        }
        if (!matched) matchAt(IDENT_RE, code, i)?.let { m ->
            val word = m.value
            val isCall = (i + word.length < code.length) && code[i + word.length] == '('
            when {
                keywords?.contains(word) == true -> push(TokenType.Keyword, word)
                isCall -> push(TokenType.Function, word)
                else -> push(TokenType.Text, word)
            }
            i += word.length
            matched = true
        }
        if (!matched) {
            push(TokenType.Text, code[i].toString())
            i += 1
        }
    }

    return tokens
}
