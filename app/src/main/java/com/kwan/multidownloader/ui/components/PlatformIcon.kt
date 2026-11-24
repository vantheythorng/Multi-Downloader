package com.kwan.multidownloader.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kwan.multidownloader.R
import com.kwan.multidownloader.domain.Platform

/** [logo] is null for [Platform.OTHER], which shows a "?" instead. */
private data class Badge(@DrawableRes val logo: Int?, val color: Color)

/** Logos and brand colours from Simple Icons (CC0-1.0), see res/drawable/ic_platform_*.xml. */
private fun Platform.badge(): Badge = when (this) {
    Platform.YOUTUBE -> Badge(R.drawable.ic_platform_youtube, Color(0xFFFF0000))
    Platform.YT_MUSIC -> Badge(R.drawable.ic_platform_yt_music, Color(0xFFFF0000))
    Platform.FACEBOOK -> Badge(R.drawable.ic_platform_facebook, Color(0xFF0866FF))
    Platform.TIKTOK -> Badge(R.drawable.ic_platform_tiktok, Color(0xFF000000))
    Platform.X -> Badge(R.drawable.ic_platform_x, Color(0xFF000000))
    Platform.INSTAGRAM -> Badge(R.drawable.ic_platform_instagram, Color(0xFFFF0069))
    Platform.PINTEREST -> Badge(R.drawable.ic_platform_pinterest, Color(0xFFBD081C))
    Platform.OTHER -> Badge(null, Color(0xFF757575))
}

/** The platform's logo in white on a circle of its brand colour; a "?" for unknown sites. */
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
        if (badge.logo != null) {
            Icon(
                painter = painterResource(badge.logo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f),
            )
        } else {
            Text(
                text = "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}
