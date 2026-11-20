package com.waizusora.nanoce

import android.text.Spannable
import android.text.style.ForegroundColorSpan

internal enum class EditorLanguage(val label: String, val keywords: Set<String>, val supportsSlashComments: Boolean) {
    CPP("C/C++", setOf("alignas", "auto", "bool", "break", "case", "catch", "char", "class", "const", "constexpr", "continue", "default", "delete", "do", "double", "else", "enum", "explicit", "extern", "false", "float", "for", "if", "inline", "int", "long", "namespace", "new", "nullptr", "private", "protected", "public", "return", "short", "signed", "sizeof", "static", "struct", "switch", "template", "this", "throw", "true", "try", "typedef", "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "while"), true),
    RUST("Rust", setOf("as", "async", "await", "break", "const", "continue", "crate", "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while"), true),
    KOTLIN("Kotlin", setOf("as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null", "object", "package", "private", "protected", "public", "return", "super", "this", "throw", "true", "try", "typealias", "val", "var", "when", "while"), true),
    PYTHON("Python", setOf("and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield"), false),
    JSON("JSON", setOf("true", "false", "null"), false),
    PLAIN("Plain text", emptySet(), false);

    companion object {
        fun fromFileName(name: String): EditorLanguage {
            return when (name.substringAfterLast('.', "").lowercase()) {
                "c", "h", "cc", "hh", "cpp", "hpp", "cxx", "hxx" -> CPP
                "rs" -> RUST
                "kt", "kts" -> KOTLIN
                "py", "pyw" -> PYTHON
                "json", "jsonc" -> JSON
                else -> PLAIN
            }
        }
    }
}

internal object SyntaxHighlighter {
    private const val KEYWORD = 0xFF7CB7FF.toInt()
    private const val STRING = 0xFFB5CEA8.toInt()
    private const val COMMENT = 0xFF7F8C79.toInt()
    private const val NUMBER = 0xFFD7A0E8.toInt()
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val number = Regex("\\b(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\b")
    private val string = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
    private val slashComment = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
    private val hashComment = Regex("#[^\\n]*")

    fun applyTreeSitter(
        text: Spannable,
        language: EditorLanguage,
        nativeBridge: NativeBridge
    ): Boolean {
        val parserLanguage = when (language) {
            EditorLanguage.JSON -> "json"
            EditorLanguage.CPP -> "cpp"
            EditorLanguage.RUST -> "rust"
            else -> return false
        }
        text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach(text::removeSpan)
        if (text.isEmpty()) return true

        val encoded = runCatching {
            nativeBridge.nativeSyntaxHighlights(text.toString(), parserLanguage)
        }.getOrNull() ?: return false
        if (encoded == "PARSER_ERROR" || encoded == "QUERY_ERROR" ||
            encoded == "UNSUPPORTED_LANGUAGE" || encoded == "INPUT_TOO_LARGE") return false

        val colors = mapOf(
            "keyword" to KEYWORD,
            "type" to KEYWORD,
            "string" to STRING,
            "comment" to COMMENT,
            "number" to NUMBER,
            "constant" to NUMBER,
            "property" to 0xFF9CDCFE.toInt(),
            "variable" to 0xFFE6EAF0.toInt()
        )
        encoded.split(';').forEach { token ->
            if (token.isBlank()) return@forEach
            val fields = token.split(':', limit = 3)
            if (fields.size != 3) return@forEach
            val start = fields[0].toIntOrNull() ?: return@forEach
            val end = fields[1].toIntOrNull() ?: return@forEach
            val color = colors[fields[2]] ?: return@forEach
            if (start >= 0 && end > start && end <= text.length) {
                text.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return true
    }

    fun apply(text: Spannable, language: EditorLanguage) {
        text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach(text::removeSpan)
        if (language == EditorLanguage.PLAIN || text.isEmpty()) return

        val occupied = BooleanArray(text.length)
        fun paint(regex: Regex, color: Int) {
            regex.findAll(text).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                if (start < 0 || end > text.length || (start until end).any { occupied[it] }) return@forEach
                text.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                for (index in start until end) occupied[index] = true
            }
        }

        paint(string, STRING)
        when (language) {
            EditorLanguage.PYTHON -> paint(hashComment, COMMENT)
            EditorLanguage.JSON -> Unit
            else -> paint(slashComment, COMMENT)
        }
        identifier.findAll(text).forEach { match ->
            if (match.value in language.keywords) {
                val start = match.range.first
                val end = match.range.last + 1
                if ((start until end).none { occupied[it] }) {
                    text.setSpan(ForegroundColorSpan(KEYWORD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    for (index in start until end) occupied[index] = true
                }
            }
        }
        paint(number, NUMBER)
    }
}
