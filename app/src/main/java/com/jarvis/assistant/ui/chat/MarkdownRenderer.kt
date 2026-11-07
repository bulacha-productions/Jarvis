package com.jarvis.assistant.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.ElectricOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.regex.Pattern

private sealed class MarkdownElement {
    data class Heading(val level: Int, val text: String) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class BulletItem(val text: String) : MarkdownElement()
    data class NumberedItem(val number: String, val text: String) : MarkdownElement()
    data class Blockquote(val text: String) : MarkdownElement()
    object Divider : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
}

@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val elements = remember(content) { parseMarkdown(content) }
    val extractedUrls = remember(content) { extractUrls(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        elements.forEach { element ->
            when (element) {
                is MarkdownElement.Heading -> {
                    val fontSize = when (element.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = buildInlineAnnotatedString(element.text, textColor),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = if (element.level == 1) ElectricOrange else textColor
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownElement.CodeBlock -> {
                    CodeBlockView(language = element.language, code = element.code)
                }
                is MarkdownElement.BulletItem -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = ElectricOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = buildInlineAnnotatedString(element.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
                is MarkdownElement.NumberedItem -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${element.number}.",
                            color = ElectricOrange,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = buildInlineAnnotatedString(element.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
                is MarkdownElement.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(ElectricOrange, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = buildInlineAnnotatedString(element.text, textColor),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = textColor.copy(alpha = 0.85f)
                            )
                        )
                    }
                }
                is MarkdownElement.Divider -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                is MarkdownElement.Paragraph -> {
                    Text(
                        text = buildInlineAnnotatedString(element.text, textColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
            }
        }

        // Exibe Link Previews de links detectados no texto
        extractedUrls.take(2).forEach { url ->
            LinkPreviewCard(url = url)
        }
    }
}

@Composable
fun CodeBlockView(
    language: String,
    code: String
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF14161D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282C37)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header do Bloco de Código com Linguagem e Botão Copiar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1E27))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "CODE" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricOrange,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Código", code)
                            clipboard.setPrimaryClip(clip)
                            isCopied = true
                            Toast.makeText(context, "Código copiado!", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Done else Icons.Default.ContentCopy,
                        contentDescription = "Copiar Código",
                        tint = if (isCopied) Color.Green else Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isCopied) "Copiado!" else "Copiar",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCopied) Color.Green else Color.LightGray
                    )
                }
            }

            // Conteúdo do Código com Rolagem Horizontal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun parseMarkdown(raw: String): List<MarkdownElement> {
    val result = mutableListOf<MarkdownElement>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeBuilder = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeBuilder.append(lines[i]).append("\n")
                i++
            }
            result.add(MarkdownElement.CodeBlock(language, codeBuilder.toString().trimEnd()))
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            result.add(MarkdownElement.Divider)
            i++
            continue
        }

        if (trimmed.startsWith("# ")) {
            result.add(MarkdownElement.Heading(1, trimmed.removePrefix("# ").trim()))
            i++
            continue
        }
        if (trimmed.startsWith("## ")) {
            result.add(MarkdownElement.Heading(2, trimmed.removePrefix("## ").trim()))
            i++
            continue
        }
        if (trimmed.startsWith("### ")) {
            result.add(MarkdownElement.Heading(3, trimmed.removePrefix("### ").trim()))
            i++
            continue
        }

        if (trimmed.startsWith("> ")) {
            result.add(MarkdownElement.Blockquote(trimmed.removePrefix("> ").trim()))
            i++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            result.add(MarkdownElement.BulletItem(trimmed.substring(2).trim()))
            i++
            continue
        }

        val numberedMatcher = Pattern.compile("^([0-9]+)\\.\\s+(.*)").matcher(trimmed)
        if (numberedMatcher.matches()) {
            val num = numberedMatcher.group(1) ?: "1"
            val text = numberedMatcher.group(2) ?: ""
            result.add(MarkdownElement.NumberedItem(num, text.trim()))
            i++
            continue
        }

        if (trimmed.isNotBlank()) {
            result.add(MarkdownElement.Paragraph(trimmed))
        }
        i++
    }

    return result
}

private fun buildInlineAnnotatedString(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val pattern = Pattern.compile("(\\*\\*([^*]+)\\*\\*)|(\\*([^*]+)\\*)|(`([^`]+)`)|(\\[([^\\]]+)\\]\\((https?://[^)]+)\\))")
        val matcher = pattern.matcher(text)

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            when {
                matcher.group(1) != null -> { // **negrito**
                    val boldText = matcher.group(2) ?: ""
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(boldText)
                    }
                }
                matcher.group(3) != null -> { // *itálico*
                    val italicText = matcher.group(4) ?: ""
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(italicText)
                    }
                }
                matcher.group(5) != null -> { // `inline code`
                    val inlineCode = matcher.group(6) ?: ""
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x33FF8000),
                            color = ElectricOrange,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" $inlineCode ")
                    }
                }
                matcher.group(7) != null -> { // [label](url)
                    val label = matcher.group(8) ?: ""
                    val url = matcher.group(9) ?: ""
                    withStyle(
                        SpanStyle(
                            color = ElectricOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(label)
                    }
                }
            }
            cursor = end
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

private fun extractUrls(text: String): List<String> {
    val urls = mutableListOf<String>()
    val pattern = Pattern.compile("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]+")
    val matcher = pattern.matcher(text)
    while (matcher.find()) {
        val url = matcher.group(0) ?: continue
        if (!urls.contains(url)) {
            urls.add(url)
        }
    }
    return urls
}
