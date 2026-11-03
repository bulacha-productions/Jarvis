package com.jarvis.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.ElectricOrange

data class DiscoveredAgentBadge(
    val title: String,
    val color: Color
)

object AgentBadgeHelper {
    fun extractBadges(text: String): List<DiscoveredAgentBadge> {
        val badges = mutableListOf<DiscoveredAgentBadge>()
        if (text.contains("Jarvis Central") || text.contains("Coordenador") || text.contains("⚡")) {
            badges.add(DiscoveredAgentBadge("⚡ Jarvis Central", ElectricOrange))
        }
        if (text.contains("Programador") || text.contains("Programação") || text.contains("💻")) {
            badges.add(DiscoveredAgentBadge("💻 Programador", Color(0xFF4FC3F7)))
        }
        if (text.contains("Pesquisador") || text.contains("Pesquisa") || text.contains("🔍")) {
            badges.add(DiscoveredAgentBadge("🔍 Pesquisador", Color(0xFF81C784)))
        }
        if (text.contains("Criativo") || text.contains("✨")) {
            badges.add(DiscoveredAgentBadge("✨ Criativo", Color(0xFFBA68C8)))
        }
        if (text.contains("Analítico") || text.contains("📊")) {
            badges.add(DiscoveredAgentBadge("📊 Analítico", Color(0xFFFFB74D)))
        }
        return badges.distinctBy { it.title }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentBadgesRow(badges: List<DiscoveredAgentBadge>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    FlowRow(
        modifier = modifier.padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        badges.forEach { badge ->
            Surface(
                color = badge.color.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, badge.color.copy(alpha = 0.4f))
            ) {
                Text(
                    text = badge.title,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = badge.color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
