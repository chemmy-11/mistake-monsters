package com.mistakemonsters.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== 主题（与 Web 版同一套儿童向配色）=====

object C {
    val Primary = Color(0xFF7C5CFC)
    val PrimaryDeep = Color(0xFF5B3FE4)
    val PrimarySoft = Color(0xFFEFEBFF)
    val Accent = Color(0xFFFF8A3D)
    val AccentSoft = Color(0xFFFFF1E6)
    val Cream = Color(0xFFFFF9F2)
    val Ink = Color(0xFF33303B)
    val InkSoft = Color(0xFF8B8794)
    val Mint = Color(0xFF2FBE8F)
    val MintSoft = Color(0xFFE4F8F0)
    val Rose = Color(0xFFFF6B8B)
    val RoseSoft = Color(0xFFFFE9EE)
    val Sky = Color(0xFF3DA9FC)
    val SkySoft = Color(0xFFE6F3FF)
    val Sun = Color(0xFFF7B32B)
    val SunSoft = Color(0xFFFFF6DE)
}

val CardShape = RoundedCornerShape(20.dp)
val PillShape = RoundedCornerShape(999.dp)

fun causeColor(category: String): Color = when (category) {
    "知识性" -> C.Sky
    "习惯性" -> C.Accent
    "审题性" -> C.Rose
    "心理性" -> C.Sun
    else -> C.InkSoft
}

@Composable
fun MistakeMonstersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Primary,
            onPrimary = Color.White,
            secondary = C.Accent,
            background = C.Cream,
            surface = Color.White,
            onSurface = C.Ink,
            onSurfaceVariant = C.InkSoft,
            outline = Color(0xFFEDE8F8),
        ),
        typography = Typography().copy(
            titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
        ),
        shapes = MaterialTheme.shapes.copy(
            medium = CardShape,
            large = CardShape,
        ),
        content = content,
    )
}
