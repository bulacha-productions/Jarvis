package com.jarvis.assistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.ElectricOrange

@Composable
fun JarvisMascot(
    size: Dp = 100.dp,
    modifier: Modifier = Modifier,
    glowEffect: Boolean = true
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Painéis Laterais (Laranja Elétrico - Voxel Earbuds)
        val earWidth = w * 0.11f
        val earHeight = h * 0.36f
        val earTop = h * 0.40f

        // Esquerdo
        drawRoundRect(
            color = ElectricOrange,
            topLeft = Offset(w * 0.04f, earTop),
            size = Size(earWidth, earHeight),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        // Direito
        drawRoundRect(
            color = ElectricOrange,
            topLeft = Offset(w * 0.85f, earTop),
            size = Size(earWidth, earHeight),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        // 2. Chassi Principal (Caixa Robótica Branca/Cinza 8-bit)
        val bodyPath = Path().apply {
            moveTo(w * 0.14f, h * 0.34f)
            lineTo(w * 0.86f, h * 0.34f)
            lineTo(w * 0.82f, h * 0.84f)
            lineTo(w * 0.18f, h * 0.84f)
            close()
        }
        drawPath(path = bodyPath, color = Color(0xFFD6D6DC))

        // Relevo frontal branco puro
        val frontFacePath = Path().apply {
            moveTo(w * 0.18f, h * 0.40f)
            lineTo(w * 0.82f, h * 0.40f)
            lineTo(w * 0.79f, h * 0.82f)
            lineTo(w * 0.21f, h * 0.82f)
            close()
        }
        drawPath(path = frontFacePath, color = Color(0xFFEEEEF2))

        // 3. Cúpula Superior / Câmara Neural Translúcida
        val domePath = Path().apply {
            moveTo(w * 0.25f, h * 0.34f)
            lineTo(w * 0.35f, h * 0.16f)
            lineTo(w * 0.65f, h * 0.16f)
            lineTo(w * 0.75f, h * 0.34f)
            close()
        }
        drawPath(path = domePath, color = Color(0xFF1E1E24))

        // Circuitos Neurais e Núcleo Iluminado
        drawRect(
            color = ElectricOrange,
            topLeft = Offset(w * 0.46f, h * 0.22f),
            size = Size(w * 0.08f, h * 0.08f)
        )
        // Trilhas do circuito
        drawLine(
            color = ElectricOrange,
            start = Offset(w * 0.38f, h * 0.24f),
            end = Offset(w * 0.46f, h * 0.24f),
            strokeWidth = w * 0.02f
        )
        drawLine(
            color = ElectricOrange,
            start = Offset(w * 0.54f, h * 0.26f),
            end = Offset(w * 0.62f, h * 0.26f),
            strokeWidth = w * 0.02f
        )

        // 4. Visor Frontal Preto Retangular Arredondado
        val visorWidth = w * 0.56f
        val visorHeight = h * 0.34f
        val visorLeft = w * 0.22f
        val visorTop = h * 0.44f

        drawRoundRect(
            color = Color(0xFF0F0F12),
            topLeft = Offset(visorLeft, visorTop),
            size = Size(visorWidth, visorHeight),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        // 5. Olhos Digitais Retangulares Luminescentes (8-bit Voxel)
        val eyeWidth = w * 0.09f
        val eyeHeight = h * 0.17f
        val eyeTop = visorTop + (visorHeight - eyeHeight) / 2

        // Olho Esquerdo
        drawRoundRect(
            color = ElectricOrange,
            topLeft = Offset(w * 0.34f, eyeTop),
            size = Size(eyeWidth, eyeHeight),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )

        // Olho Direito
        drawRoundRect(
            color = ElectricOrange,
            topLeft = Offset(w * 0.57f, eyeTop),
            size = Size(eyeWidth, eyeHeight),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )
    }
}
