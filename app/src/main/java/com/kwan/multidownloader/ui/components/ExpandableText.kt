package com.kwan.multidownloader.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kwan.multidownloader.R

/**
 * Long text cut to [collapsedMaxLines] with "Show more" / "Show less". The button only appears when the
 * text actually overflows; tapping the text toggles it too.
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 5,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by rememberSaveable(text) { mutableStateOf(false) }
    val canToggle = overflows || expanded

    Column(modifier = modifier.animateContentSize()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = if (canToggle) Modifier.clickable { expanded = !expanded } else Modifier,
        )
        if (canToggle) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
            }
        }
    }
}
