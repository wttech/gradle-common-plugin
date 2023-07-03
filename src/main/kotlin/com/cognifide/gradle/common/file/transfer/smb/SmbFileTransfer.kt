package com.cognifide.gradle.common.file.transfer.smb

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.FileEntry
import com.cognifide.gradle.common.file.transfer.ProtocolFileTransfer
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.util.Properties

@Suppress("TooGenericExceptionCaught")
class SmbFileTransfer(common: CommonExtension) : ProtocolFileTransfer(common) {

    private val logger = common.logger

    val domain = common.obj.string {
        common.prop.string("fileTransfer.smb.domain")?.let { set(it) }
    }

    val user = common.obj.string {
        common.prop.string("fileTransfer.smb.user")?.let { set(it) }
    }

    val password = common.obj.string {
        common.prop.string("fileTransfer.smb.password")?.let { set(it) }
    }

    override val name: String get() = NAME

    override val protocols: List<String> get() = listOf("smb://*")

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        val fileUrl = "$dirUrl/$fileName"
        try {
            file(dirUrl, fileName).apply {
                logger.info("Downloading file from URL '$fileUrl'")
                downloader { size = length() }.download(inputStream, target)
            }
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            throw SmbFileException("Cannot upload file '$source' to URL '$fileUrl'. Cause: '${e.message}", e)
        }
    }

    override fun deleteFrom(dirUrl: String, fileName: String) {
        val fileUrl = "$dirUrl/$fileName"

        file(dirUrl, fileName).apply {
            try {
                logger.info("Deleting file at URL '$fileUrl'")
                delete()
            } catch (e: Exception) {
                throw SmbFileException("Cannot delete file at URL '$fileUrl'. Cause: '${e.message}", e)
            }
        }
    }

    override fun list(dirUrl: String): List<FileEntry> = dir(dirUrl).run {
        try {
            logger.info("Listing files at URL '$dirUrl'")
            dirFiles().map { FileEntry(it.name, it.length(), it.lastModified()) }
        } catch (e: Exception) {
            throw SmbFileException("Cannot list files in directory at URL '$dirUrl'. Cause: '${e.message}", e)
        }
    }

    override fun truncate(dirUrl: String) {
        dir(dirUrl).apply {
            try {
                logger.info("Truncating files at URL '$dirUrl'")
                dirFiles().forEach { it.delete() }
            } catch (e: Exception) {
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
        } catch (e: Exception) {
            throw SmbFileException("Cannot check file status at URL '$fileUrl'. Cause: '${e.message}", e)
        }
    }

    fun file(dirUrl: String, fileName: String): SmbFile {
        val dirUrlNormalized = StringUtils.appendIfMissing(dirUrl, "/")
        val properties = Properties()
        val configuration = PropertyConfiguration(properties)
        val context =
            if (!user.orNull.isNullOrBlank() && !password.orNull.isNullOrBlank())
                BaseContext(configuration).withCredentials(NtlmPasswordAuthenticator(domain.orNull, user.get(), password.get()))
            else
                BaseContext(configuration).withAnonymousCredentials()

        return SmbFile(context.get(dirUrlNormalized), fileName).apply { useCaches = false }
    }

    fun dir(dirUrl: String): SmbFile = file(dirUrl, "").apply {
        try {
            if (!isDirectory) {
                throw SmbFileException("Path at URL '$dirUrl' is not a directory.")
            }
        } catch (e: Exception) {
            throw SmbFileException("Directory at URL '$dirUrl' does not exist or not accessible. Cause: '${e.message}'", e)
        }
    }

    private fun SmbFile.dirFiles(): List<SmbFile> = (listFiles() ?: arrayOf()).filter { it.isFile }

    companion object {
        const val NAME = "smb"
    }
}
