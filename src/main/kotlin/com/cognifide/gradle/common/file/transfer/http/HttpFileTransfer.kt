package com.cognifide.gradle.common.file.transfer.http

import com.cognifide.gradle.common.CommonException
import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.ProtocolFileTransfer
import com.cognifide.gradle.common.http.HttpClient
import java.io.File
import java.io.IOException

class HttpFileTransfer(common: CommonExtension, var client: HttpClient = client(common)) : ProtocolFileTransfer(common) {

    fun client(configurer: HttpClient.() -> Unit) {
        client.apply(configurer)
    }

    override val name: String
        get() = NAME

    override val protocols: List<String>
        get() = listOf("http://*", "https://*")

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        val sourceUrl = "$dirUrl/$fileName"

        try {
            client.get(sourceUrl) { response ->
                common.logger.info("Downloading: $sourceUrl -> $target")
                downloader().download(response.entity.contentLength, asStream(response), target)
            }
        } catch (e: CommonException) {
            throw HttpFileException("Cannot download URL '$sourceUrl' to file '$target' using HTTP(s). Cause: ${e.message}", e)
        } catch (e: IOException) {
            throw HttpFileException("Cannot download URL '$sourceUrl' to file '$target' using HTTP(s). Cause: ${e.message}", e)
        }
    }

    companion object {
        const val NAME = "httpd"

        fun client(common: CommonExtension) = HttpClient(common).apply {
            basicUser = common.prop.string("fileTransfer.http.user")
            basicPassword = common.prop.string("fileTransfer.http.password")
            connectionIgnoreSsl = common.prop.boolean("fileTransfer.http.connectionIgnoreSsl") ?: true
        }
    }
}
