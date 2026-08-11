package ca.rofiant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.rofiant.app.ui.theme.CodeTextStyle
import ca.rofiant.app.ui.theme.LocalRofiantExtraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Kotlin port of rofiant-desktop's src/lib/markdown-lite.tsx block grammar.
// Tool-call tags (@@tool:...@@) and the sources tag are agent-loop features
// with no mobile equivalent (see ChatApi) so they're not parsed here — any
// literal occurrence just renders as plain text. Markdown tables are the one
// other thing dropped: the desktop system prompt already tells models to
// avoid them, and it's a fallback path there too.

private sealed interface MdBlock {
    data class CodeBlock(val lang: String, val code: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data object Hr : MdBlock
    data object Spacer : MdBlock
}

private val FENCE_OPEN = Regex("^\\s*```(\\w*)")
private val FENCE_CLOSE = Regex("^\\s*```")
private val HR_RE = Regex("^\\s*(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})$")
private val BULLET_RE = Regex("^\\s*[-*]\\s+(.*)")
private val HEADING_RE = Regex("^(#{1,3})\\s+(.*)")
private val ENTITIES = mapOf(
    "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "#39" to "'",
)
private val ENTITY_RE = Regex("&(nbsp|amp|lt|gt|quot|apos|#39);")

private fun decodeEntities(text: String): String =
    text.replace(ENTITY_RE) { ENTITIES[it.groupValues[1]] ?: it.value }

private fun parseMarkdown(text: String): List<MdBlock> {
    val lines = decodeEntities(text).split("\n")
    val blocks = mutableListOf<MdBlock>()
    var listBuffer = mutableListOf<String>()

    fun flushList() {
        if (listBuffer.isNotEmpty()) {
            blocks.add(MdBlock.Bullets(listBuffer.toList()))
            listBuffer = mutableListOf()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        val fenceOpen = FENCE_OPEN.find(line)
        if (fenceOpen != null) {
            flushList()
            val lang = fenceOpen.groupValues[1]
            i++
            val codeLines = mutableListOf<String>()
            while (i < lines.size && !FENCE_CLOSE.containsMatchIn(lines[i])) {
                codeLines.add(lines[i])
                i++
            }
            i++ // skip closing fence (or EOF if unterminated)
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        if (HR_RE.matches(line)) {
            flushList()
            blocks.add(MdBlock.Hr)
            i++
            continue
        }

        val bullet = BULLET_RE.find(line)
        if (bullet != null) {
            listBuffer.add(bullet.groupValues[1])
            i++
            continue
        }
        flushList()

        val heading = HEADING_RE.find(line)
        if (heading != null) {
            blocks.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2]))
            i++
            continue
        }

        if (line.isBlank()) {
            blocks.add(MdBlock.Spacer)
            i++
            continue
        }

        blocks.add(MdBlock.Paragraph(line))
        i++
    }
    flushList()
    return blocks
}

private val INLINE_RE = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`|\\*[^*]+\\*)")

@Composable
private fun renderInline(text: String): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        var last = 0
        for (match in INLINE_RE.findAll(text)) {
            if (match.range.first > last) append(text.substring(last, match.range.first))
            val part = match.value
            when {
                part.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(part.substring(2, part.length - 2))
                }
                part.startsWith("`") -> withStyle(
                    SpanStyle(fontFamily = CodeTextStyle.fontFamily, background = codeBg, fontSize = 13.sp)
                ) {
                    append(part.substring(1, part.length - 1))
                }
                part.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(part.substring(1, part.length - 1))
                }
            }
            last = match.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is MdBlock.CodeBlock -> CodeBlockView(block.lang, block.code)
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    }
                    Text(
                        text = renderInline(block.text),
                        style = style.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                is MdBlock.Bullets -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    for (item in block.items) {
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•  ", style = MaterialTheme.typography.bodyMedium)
                            Text(renderInline(item), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is MdBlock.Paragraph -> Text(
                    text = renderInline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
                MdBlock.Hr -> HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                MdBlock.Spacer -> Box(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CodeBlockView(lang: String, code: String) {
    val extra = LocalRofiantExtraColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lang.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                modifier = Modifier.height(24.dp),
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                    scope.launch {
                        delay(1500)
                        copied = false
                    }
                },
            ) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = if (copied) "Copied" else "Copy code",
                    modifier = Modifier.height(16.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Box(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Text(text = highlightedCode(code, lang, extra), style = CodeTextStyle)
        }
    }
}

@Composable
private fun highlightedCode(code: String, lang: String, extra: ca.rofiant.app.ui.theme.RofiantExtraColors): AnnotatedString {
    val tokens = remember(code, lang) { tokenize(code, lang) }
    return buildAnnotatedString {
        for (token in tokens) {
            val color: Color? = when (token.type) {
                TokenType.Keyword -> extra.syntaxKeyword
                TokenType.StringLit -> extra.syntaxString
                TokenType.Comment -> extra.syntaxComment
                TokenType.Number -> extra.syntaxNumber
                TokenType.Function -> extra.syntaxFunction
                TokenType.Text -> null
            }
            if (color != null) {
                val italic = token.type == TokenType.Comment
                withStyle(SpanStyle(color = color, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)) {
                    append(token.text)
                }
            } else {
                append(token.text)
            }
        }
    }
}
