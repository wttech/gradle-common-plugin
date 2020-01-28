package com.cognifide.gradle.common.file.transfer.smb

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.FileEntry
import com.cognifide.gradle.common.file.transfer.ProtocolFileTransfer
import java.io.File
import java.io.IOException
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import org.apache.commons.lang3.StringUtils

class SmbFileTransfer(common: CommonExtension) : ProtocolFileTransfer(common) {

    private val logger = common.logger

    var domain: String? = common.prop.string("fileTransfer.smb.domain")

    var user: String? = common.prop.string("fileTransfer.smb.user")

    var password: String? = common.prop.string("fileTransfer.smb.password")

    override val name: String
        get() = NAME

    override val protocols: List<String>
        get() = listOf("smb://*")

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        val fileUrl = "$dirUrl/$fileName"
        try {
            file(dirUrl, fileName).apply {
                logger.info("Downloading file from URL '$fileUrl'")
                downloader().download(length(), inputStream, target)
            }
        } catch (e: IOException) {
            throw SmbFileException("Cannot download file from URL '$fileUrl' . Cause: '${e.message}")
        }
    }

    override fun uploadTo(dirUrl: String, fileName: String, source: File) {
        val fileUrl = "$dirUrl/$fileName"

        try {
            file(dirUrl, fileName).apply {
                logger.info("Uploading file to URL '$fileUrl'")
                uploader().upload(source, outputStream)
            }
        } catch (e: IOException) {
            throw SmbFileException("Cannot upload file '$source' to URL '$fileUrl'. Cause: '${e.message}", e)
        }
    }

    override fun deleteFrom(dirUrl: String, fileName: String) {
        val fileUrl = "$dirUrl/$fileName"

        file(dirUrl, fileName).apply {
            try {
                logger.info("Deleting file at URL '$fileUrl'")
                delete()
            } catch (e: IOException) {
                throw SmbFileException("Cannot delete file at URL '$fileUrl'. Cause: '${e.message}", e)
            }
        }
    }

    override fun list(dirUrl: String): List<FileEntry> = dir(dirUrl).run {
        try {
            logger.info("Listing files at URL '$dirUrl'")
            dirFiles().map { FileEntry(it.name, it.length(), it.lastModified()) }
        } catch (e: IOException) {
            throw SmbFileException("Cannot list files in directory at URL '$dirUrl'. Cause: '${e.message}", e)
        }
    }

    override fun truncate(dirUrl: String) {
        dir(dirUrl).apply {
            try {
                logger.info("Truncating files at URL '$dirUrl'")
                dirFiles().forEach { it.delete() }
            } catch (e: IOException) {
                throw SmbFileException("Cannot truncate directory at URL '$dirUrl'. Cause: '${e.message}", e)
            }
        }
    }

    override fun stat(dirUrl: String, fileName: String): FileEntry? {
        val fileUrl = "$dirUrl/$fileName"
        logger.info("Checking file status at URL '$fileUrl'")
        try {
            return file(dirUrl, fileName)
                    .takeIf { it.isFile }
                    ?.run { FileEntry(fileName, length(), lastModified()) }
        } catch (e: IOException) {
            throw SmbFileException("Cannot check file status at URL '$fileUrl'. Cause: '${e.message}", e)
        }
    }

    fun file(dirUrl: String, fileName: String): SmbFile {
        val dirUrlNormalized = StringUtils.appendIfMissing(dirUrl, "/")

        return if (!user.isNullOrBlank() && !password.isNullOrBlank()) {
            SmbFile(dirUrlNormalized, fileName, NtlmPasswordAuthentication(domain, user, password))
        } else {
            SmbFile(dirUrlNormalized, fileName)
        }.apply {
            useCaches = false
        }
    }

    fun dir(dirUrl: String): SmbFile {
        return file(dirUrl, "").apply {
            try {
                if (!isDirectory) {
                    throw SmbFileException("Path at URL '$dirUrl' is not a directory.")
                }
            } catch (e: IOException) {
                throw SmbFileException("Directory at URL '$dirUrl' does not exist or not accessible: '${e.message}'!", e)
            }
        }
    }

    private fun SmbFile.dirFiles(): List<SmbFile> = (listFiles() ?: arrayOf()).filter { it.isFile }

    companion object {
        const val NAME = "smb"
    }
}
