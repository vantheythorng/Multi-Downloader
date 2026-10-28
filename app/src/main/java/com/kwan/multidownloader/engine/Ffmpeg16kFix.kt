package com.kwan.multidownloader.engine

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Makes FFmpeg start on 16 KB-page devices. youtubedl-android 0.18.1 unpacks libwebp 1.6.0 aligned to
 * 4 KB pages, which the linker refuses there, so every MP3 conversion and video merge fails
 * (https://github.com/yausername/youtubedl-android/issues/365). We ship the same libwebp version rebuilt
 * with 16 KB alignment (tools/webp16k/build.sh) as `libmd16k_*.so` and copy them over the unpacked ones.
 *
 * Only runs on 16 KB devices, so 4 KB phones keep the library's own files untouched, and only replaces
 * libraries that are still 4 KB-aligned: once upstream ships aligned ones, this stops patching by itself.
 */
object Ffmpeg16kFix {
    private const val TAG = "Ffmpeg16kFix"
    private val LIBS = listOf("sharpyuv", "webp", "webpdecoder", "webpdemux", "webpmux")

    /** Call after FFmpeg.init(): that unpacks (or re-unpacks) the bundle we patch. */
    fun apply(context: Context) {
        if (Os.sysconf(OsConstants._SC_PAGESIZE) <= 4096) return
        val ffmpegLibDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
        val ourLibDir = File(context.applicationInfo.nativeLibraryDir)
        if (!ffmpegLibDir.isDirectory) return
        for (name in LIBS) {
            // 32-bit APKs don't ship them (32-bit Android always uses 4 KB pages).
            val source = File(ourLibDir, "libmd16k_$name.so").takeIf { it.isFile } ?: continue
            val target = File(ffmpegLibDir, "lib$name.so")
            // Already ours, or upstream's own build is aligned now: leave it.
            if (target.isFile && (loadAlignment(target) ?: 0) >= MIN_ALIGNMENT) continue
            // Write next to the target, then rename, so FFmpeg never sees a half-copied library.
            val temp = File(ffmpegLibDir, "lib$name.so.md16k")
            source.copyTo(temp, overwrite = true)
            if (!temp.renameTo(target)) {
                temp.delete()
                Log.w(TAG, "Couldn't replace ${target.name}")
            }
        }
        Log.i(TAG, "16 KB-aligned libwebp in place for FFmpeg")
    }

    private const val MIN_ALIGNMENT = 16 * 1024L

    /** Smallest p_align of the PT_LOAD segments of a 64-bit little-endian ELF file, or null if it isn't one. */
    internal fun loadAlignment(file: File): Long? = RandomAccessFile(file, "r").use { raf ->
        val header = ByteArray(64)
        if (raf.read(header) != header.size) return null
        val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        // Magic 0x7F E L F, then ELFCLASS64 and little-endian
        if (h.getInt(0) != 0x464C457F || header[4] != 2.toByte() || header[5] != 1.toByte()) return null
        val phoff = h.getLong(0x20)
        val phentsize = h.getShort(0x36).toInt() and 0xFFFF
        val phnum = h.getShort(0x38).toInt() and 0xFFFF
        val table = ByteArray(phentsize * phnum)
        raf.seek(phoff)
        raf.readFully(table)
        val t = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        (0 until phnum)
            .filter { t.getInt(it * phentsize) == PT_LOAD }
            .minOfOrNull { t.getLong(it * phentsize + 0x30) }
    }

    private const val PT_LOAD = 1
}
