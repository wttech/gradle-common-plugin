package com.cognifide.gradle.common.file.transfer.generic

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.FileException
import com.cognifide.gradle.common.file.transfer.ProtocolFileTransfer
import java.io.File
import java.net.URL

@Suppress("TooGenericExceptionCaught")
class UrlFileTransfer(common: CommonExtension) : ProtocolFileTransfer(common) {

    override val name: String get() = NAME

    override val protocols: List<String> get() = listOf(PROTOCOL)

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        val fileUrl = "$dirUrl/$fileName"

        try {
            common.logger.info("Downloading: $fileUrl -> ${target.absolutePath}")

            URL(fileUrl).openConnection().apply {
                useCaches = false
                inputStream.use { downloader().download(contentLengthLong, it, target) }
            }
        } catch (e: Exception) {
            throw FileException("Cannot download URL '$fileUrl' to file '$target'. Cause: '${e.message}'", e)
        }
    }

    companion object {
        const val NAME = "url"

        const val PROTOCOL = "*://*"
    }
}
