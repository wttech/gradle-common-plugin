package com.cognifide.gradle.common.file.transfer.resolve

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.build.DependencyFile
import com.cognifide.gradle.common.file.transfer.ProtocolFileTransfer
import java.io.File

@Suppress("TooGenericExceptionCaught")
class ResolveFileTransfer(common: CommonExtension) : ProtocolFileTransfer(common) {

    override val name = NAME

    override val protocols = listOf("$NAME://*")

    override val parallelable = common.obj.provider { false }

    override fun handles(fileUrl: String): Boolean = super.handles(fileUrl) || DependencyFile.isNotation(fileUrl)

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        resolve(dirUrl).apply {
            inputStream().use { downloader().download(length(), it, target) }
        }
    }

    fun resolve(value: Any) = try {
        DependencyFile(common.project, when (value) {
            is String -> value.substringAfter("://")
            else -> value
        }).file
    } catch (e: Exception) {
        throw ResolveFileException("Cannot resolve value '$value'. Cause: ${e.message}", e)
    }

    companion object {
        const val NAME = "resolve"
    }
}
