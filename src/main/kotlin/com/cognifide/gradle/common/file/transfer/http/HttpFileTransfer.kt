package com.cognifide.gradle.common.file.transfer.http

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.ProtocolFileTransfer
import com.cognifide.gradle.common.http.HttpClient
import com.cognifide.gradle.common.utils.using
import java.io.File

@Suppress("TooGenericExceptionCaught")
class HttpFileTransfer(common: CommonExtension) : ProtocolFileTransfer(common) {

    internal var client = HttpClient(common)

    fun client(options: HttpClient.() -> Unit) = client.using(options)

    override val name: String get() = NAME

    override val protocols: List<String> get() = listOf("http://*", "https://*")

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        val sourceUrl = "$dirUrl/$fileName"

        try {
            client.get(sourceUrl) { response ->
                common.logger.info("Downloading: $sourceUrl -> $target")
                downloader().download(response.entity.contentLength, asStream(response), target)
            }
        } catch (e: Exception) {
            throw HttpFileException("Cannot download URL '$sourceUrl' to file '$target' using HTTP(s). Cause: ${e.message}", e)
        }
    }

    companion object {
        const val NAME = "http"
    }
}
