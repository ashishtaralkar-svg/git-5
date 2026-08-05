package com.hdfc.docupload.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsTest {

    @Test
    fun `readableSize formats bytes`() {
        assertEquals("0 B", FileUtils.readableSize(0))
        assertEquals("512 B", FileUtils.readableSize(512))
    }

    @Test
    fun `readableSize formats kilobytes and megabytes`() {
        assertEquals("1 KB", FileUtils.readableSize(1024))
        assertEquals("1 MB", FileUtils.readableSize(1024L * 1024))
        assertEquals("1.5 MB", FileUtils.readableSize((1.5 * 1024 * 1024).toLong()))
    }
}
