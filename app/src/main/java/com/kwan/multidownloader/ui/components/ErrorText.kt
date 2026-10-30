package com.kwan.multidownloader.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import com.kwan.multidownloader.R
import com.kwan.multidownloader.engine.ErrorKind

/** Translatable wording for each [ErrorKind]; null for [ErrorKind.OTHER], which shows yt-dlp's own message. */
@StringRes
fun ErrorKind.messageRes(): Int? = when (this) {
    ErrorKind.PRIVATE -> R.string.error_private
    ErrorKind.UNSUPPORTED -> R.string.error_unsupported
    ErrorKind.UNAVAILABLE -> R.string.error_unavailable
    ErrorKind.LINK_NOT_FOUND -> R.string.error_link_not_found
    ErrorKind.RATE_LIMITED -> R.string.error_rate_limited
    ErrorKind.LOGIN_REQUIRED -> R.string.error_login_required
    ErrorKind.NO_VIDEO -> R.string.error_no_video
    ErrorKind.NETWORK -> R.string.error_network
    ErrorKind.EXTRACTOR_OUTDATED -> R.string.error_extractor_outdated
    ErrorKind.SITE_CHANGED -> R.string.error_site_changed
    ErrorKind.SAVE_FAILED -> R.string.error_save_failed
    ErrorKind.OTHER -> null
}

/** Text for a failed download or fetch, usable outside Compose (notifications). */
fun errorText(resources: Resources, kind: ErrorKind?, fallback: String?): String =
    kind?.messageRes()?.let(resources::getString)
        ?: fallback?.takeIf { it.isNotBlank() }
        ?: resources.getString(R.string.error_unknown)
