package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.FileException
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.Serializable

abstract class AbstractFileTransfer(protected val common: CommonExtension) : FileTransferHandler, Serializable {

    override val enabled = common.obj.boolean { convention(true) }

    override val parallelable = common.obj.provider { true }

    override fun download(fileUrl: String) = download(fileUrl, common.temporaryFile(FilenameUtils.getName(fileUrl)))

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        throw FileException("File transfer '$name' does not support 'download' operation.")
    }

    override fun uploadTo(dirUrl: String, fileName: String, source: File) {
        throw FileException("File transfer '$name' does not support 'upload' operation.")
    }

    override fun deleteFrom(dirUrl: String, fileName: String) {
        throw FileException("File transfer '$name' does not support 'delete' operation.")
    }

    override fun truncate(dirUrl: String) {
        throw FileException("File transfer '$name' does not support 'truncate' operation.")
    }

    override fun list(dirUrl: String): List<FileEntry> {
        throw FileException("File transfer '$name' does not support 'list' operation.")
    }

    override fun stat(dirUrl: String, fileName: String): FileEntry? {
        throw FileException("File transfer '$name' does not support 'stat' operation.")
    }

    fun downloader() = FileDownloader(common)

    fun uploader() = FileUploader(common)
}
