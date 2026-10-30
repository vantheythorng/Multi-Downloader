package com.kwan.multidownloader.data.cookies

import android.content.Context
import android.net.Uri
import com.kwan.multidownloader.domain.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Per-platform cookies for login-only content (FR-14). Files live in app-private storage
 * (excluded from backups, see res/xml) and are never logged or shared.
 */
class CookieStore(private val context: Context) {
    private val dir = File(context.filesDir, DIR)

    /** Platforms with their own cookie file; YouTube Music uses YouTube's. */
    val platforms = listOf(Platform.YOUTUBE, Platform.FACEBOOK, Platform.TIKTOK, Platform.X)

    private val _counts = MutableStateFlow<Map<Platform, Int>>(emptyMap())
    /** Cookies stored per platform; platforms without a file are absent. */
    val counts: StateFlow<Map<Platform, Int>> = _counts.asStateFlow()

    sealed interface ImportResult {
        data class Imported(val cookieCount: Int) : ImportResult
        data object NotNetscape : ImportResult
        data object NoCookiesForPlatform : ImportResult
        data object TooLarge : ImportResult
        data object Unreadable : ImportResult
    }

    /** The file yt-dlp should get via `--cookies` for a link on [platform], if one was imported. */
    fun fileFor(platform: Platform): File? = file(owner(platform)).takeIf { ENABLED && it.isFile }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _counts.value = platforms.mapNotNull { platform ->
            fileFor(platform)?.let { platform to CookieFile.count(it.readText()) }
        }.toMap()
    }

    suspend fun import(platform: Platform, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX_BYTES + 1) }
        } catch (e: Exception) {
            null
        } ?: return@withContext ImportResult.Unreadable
        if (bytes.size > MAX_BYTES) return@withContext ImportResult.TooLarge
        when (val parsed = CookieFile.parse(bytes.decodeToString(), platform)) {
            CookieFile.Parsed.NotNetscape -> ImportResult.NotNetscape
            CookieFile.Parsed.NoCookiesForPlatform -> ImportResult.NoCookiesForPlatform
            is CookieFile.Parsed.Valid -> {
                dir.mkdirs()
                // Write then rename, so a running yt-dlp never reads a half-written file.
                val target = file(owner(platform))
                val temp = File(dir, "${target.name}.tmp")
                temp.writeText(parsed.text)
                if (!temp.renameTo(target)) {
                    temp.delete()
                    return@withContext ImportResult.Unreadable
                }
                refresh()
                ImportResult.Imported(parsed.cookieCount)
            }
        }
    }

    suspend fun remove(platform: Platform) = withContext(Dispatchers.IO) {
        file(owner(platform)).delete()
        refresh()
    }

    private fun owner(platform: Platform): Platform = if (platform == Platform.YT_MUSIC) Platform.YOUTUBE else platform

    private fun file(platform: Platform): File = File(dir, "${platform.name.lowercase()}.txt")

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        /** Hidden: import keeps every site's cookies from a browser export, not just the platform's. */
        const val ENABLED = false
        const val DIR = "cookies"
        private const val MAX_BYTES = 1024 * 1024
    }
}
