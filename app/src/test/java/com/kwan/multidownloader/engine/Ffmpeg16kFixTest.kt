package com.kwan.multidownloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Ffmpeg16kFixTest {

    @Test fun shipped_libraries_are_16k_aligned() {
        // Unit tests run from the module directory.
        for (abi in listOf("arm64-v8a")) {
            for (name in listOf("sharpyuv", "webp", "webpdecoder", "webpdemux", "webpmux")) {
                val lib = File("src/main/jniLibs/$abi/libmd16k_$name.so")
                assertEquals("$abi $name", 16384L, Ffmpeg16kFix.loadAlignment(lib))
            }
        }
    }

    @Test fun reads_4k_alignment_like_the_bundled_libwebp() {
        assertEquals(4096L, Ffmpeg16kFix.loadAlignment(fakeElf(listOf(4096L, 4096L))))
        assertEquals(4096L, Ffmpeg16kFix.loadAlignment(fakeElf(listOf(16384L, 4096L))))
    }

    @Test fun non_elf_files_are_ignored() {
        val f = File.createTempFile("not-elf", ".so").apply { writeText("hello") }
        assertNull(Ffmpeg16kFix.loadAlignment(f))
    }

    /** Minimal 64-bit little-endian ELF: header plus one PT_LOAD program header per alignment. */
    private fun fakeElf(alignments: List<Long>): File {
        val phentsize = 56
        val buf = ByteBuffer.allocate(64 + phentsize * alignments.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0, 0x464C457F)
        buf.put(4, 2) // ELFCLASS64
        buf.put(5, 1) // little-endian
        buf.putLong(0x20, 64) // e_phoff
        buf.putShort(0x36, phentsize.toShort())
        buf.putShort(0x38, alignments.size.toShort())
        alignments.forEachIndexed { i, align ->
            buf.putInt(64 + i * phentsize, 1) // PT_LOAD
            buf.putLong(64 + i * phentsize + 0x30, align)
        }
        return File.createTempFile("fake", ".so").apply { writeBytes(buf.array()) }
    }
}
