package com.jarvis.assistant.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.ElectricOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LinkMetadata(
    val url: String,
    val domain: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?
)

object LinkPreviewCache {
    private val cache = ConcurrentHashMap<String, LinkMetadata>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val TITLE_PATTERN = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val OG_TITLE_PATTERN = Pattern.compile("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
    private val OG_DESC_PATTERN = Pattern.compile("<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
    private val OG_IMAGE_PATTERN = Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)

    /**
     * Validação estrita contra SSRF: Aceita apenas HTTP/HTTPS e rejeita IPs locais/privados.
     */
    fun isSafeUrl(rawUrl: String): Boolean {
        return try {
            val url = URL(rawUrl)
            val protocol = url.protocol.lowercase()
            if (protocol != "http" && protocol != "https") return false

            val host = url.host.lowercase()
            if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false

            val address = InetAddress.getByName(host)
            if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress || address.isAnyLocalAddress) {
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchMetadata(rawUrl: String): LinkMetadata? = withContext(Dispatchers.IO) {
        val cached = cache[rawUrl]
        if (cached != null) return@withContext cached

        if (!isSafeUrl(rawUrl)) return@withContext null

        try {
            val uri = Uri.parse(rawUrl)
            val domain = uri.host ?: rawUrl

            val request = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/114.0 Firefox/114.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val contentType = response.header("Content-Type") ?: ""
                if (!contentType.contains("text/html", ignoreCase = true)) return@withContext null

                val bodyBytes = response.body?.source()?.readByteArray(120_000) ?: return@withContext null
                val html = String(bodyBytes, Charsets.UTF_8)

                var title = extractPattern(OG_TITLE_PATTERN, html)
                if (title.isNullOrBlank()) {
                    title = extractPattern(TITLE_PATTERN, html)
                }
                val description = extractPattern(OG_DESC_PATTERN, html)
                val imageUrl = extractPattern(OG_IMAGE_PATTERN, html)

                if (title.isNullOrBlank() && description.isNullOrBlank()) return@withContext null

                val meta = LinkMetadata(
                    url = rawUrl,
                    domain = domain,
                    title = title?.trim()?.replace("&amp;", "&"),
                    description = description?.trim()?.replace("&amp;", "&"),
                    imageUrl = imageUrl
                )
                cache[rawUrl] = meta
                meta
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPattern(pattern: Pattern, html: String): String? {
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }
}

@Composable
fun LinkPreviewCard(
    url: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var metadata by remember(url) { mutableStateOf<LinkMetadata?>(null) }
    var hasLoaded by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (!hasLoaded) {
            metadata = LinkPreviewCache.fetchMetadata(url)
            hasLoaded = true
        }
    }

    val currentMeta = metadata ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable {
                try {
                    uriHandler.openUri(currentMeta.url)
                } catch (_: Exception) {}
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElectricOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = ElectricOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentMeta.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricOrange,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                currentMeta.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                currentMeta.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Abrir Link",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
