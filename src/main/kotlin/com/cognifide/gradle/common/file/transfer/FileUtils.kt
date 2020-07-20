package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.build.DependencyFile
import org.apache.commons.io.FilenameUtils

object FileUtils {

    /**
     * Handle correctly automatic retrieval of file names from urls and dependency notation.
     */
    fun nameFromUrl(fileUrl: String) = when {
        DependencyFile.isNotation(fileUrl) -> "${fileUrl.replace(":", "_")}.${DependencyFile.getExtension(fileUrl)}"
        else -> FilenameUtils.getName(fileUrl)
    }

    fun splitUrl(fileUrl: String): Pair<String, String> = when {
        fileUrl.contains("://") -> {
            val (protocol, path) = fileUrl.split("://")
            val dirUrl = path.takeIf { it.contains("/") }?.substringBeforeLast("/").orEmpty()
            val fileName = path.substringAfterLast("/")

            ("$protocol://$dirUrl") to fileName
        }
        else -> {
            val dirUrl = fileUrl.substringBeforeLast("/")
            val fileName = fileUrl.substringAfterLast("/")

            dirUrl to fileName
        }
    }
}
