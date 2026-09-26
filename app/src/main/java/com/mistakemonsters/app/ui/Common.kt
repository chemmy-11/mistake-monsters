package com.mistakemonsters.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== 公共组件 =====

@Composable
fun MsCard(modifier: Modifier = Modifier, bg: Color = Color.White, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun MsChip(text: String, color: Color = C.PrimarySoft, textColor: Color = C.PrimaryDeep) {
    Text(
        text,
        Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun Stars(n: Int) {
    val k = n.coerceIn(1, 5)
    Text(
        "★".repeat(k) + "☆".repeat(5 - k),
        color = C.Sun,
        fontSize = 12.sp,
    )
}

@Composable
fun MsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bg: Color = C.Primary,
    fg: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = C.PrimaryDeep,
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, C.PrimarySoft),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink)
}

@Composable
fun EmptyState(emoji: String, title: String, desc: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = C.Ink)
        if (desc != null) {
            Spacer(Modifier.height(4.dp))
            Text(desc, color = C.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}

@Composable
fun ErrorBox(message: String, onDismiss: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.RoseSoft, CardShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠️ $message", color = C.Rose, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            Text("✕", color = C.Rose, modifier = Modifier
                .padding(start = 8.dp)
                .clickable { onDismiss() })
        }
    }
}

@Composable
fun Labeled(text: String, content: @Composable () -> Unit) {
    Column {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.InkSoft)
        Spacer(Modifier.height(4.dp))
        content()
    }
}
