package com.cognifide.gradle.common.file.transfer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileUtilTest {

    @Test
    fun shouldHandleWindowsPathCorrectly() {
        val url = """C:\s\xxxx-yy-zzz\dir\my.jar"""

        val (dirUrl, fileName) = FileUtils.splitUrl(url)

        assertEquals("C:/s/xxxx-yy-zzz/dir", dirUrl)
        assertEquals("my.jar", fileName)
    }
}
