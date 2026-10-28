package com.kwan.multidownloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kwan.multidownloader.domain.Platform

private data class Badge(val label: String, val color: Color)

private fun Platform.badge(): Badge = when (this) {
    Platform.YOUTUBE -> Badge("YT", Color(0xFFE62117))
    Platform.YT_MUSIC -> Badge("♪", Color(0xFFC2185B))
    Platform.FACEBOOK -> Badge("f", Color(0xFF1877F2))
    Platform.TIKTOK -> Badge("Tk", Color(0xFF1A9AA6))
    Platform.X -> Badge("X", Color(0xFF14171A))
    Platform.OTHER -> Badge("?", Color(0xFF757575))
}

/** Letter badge per platform; deliberately not the platforms' own logos. */
@Composable
fun PlatformIcon(
    platform: Platform,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    highlighted: Boolean = true,
) {
    val badge = platform.badge()
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (highlighted) 1f else 0.35f)
            .background(badge.color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .semantics { contentDescription = platform.displayName },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badge.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}
