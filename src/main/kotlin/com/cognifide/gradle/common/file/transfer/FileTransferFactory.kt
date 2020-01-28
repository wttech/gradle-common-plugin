package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.http.HttpFileTransfer
import com.cognifide.gradle.common.file.transfer.sftp.SftpFileTransfer
import com.cognifide.gradle.common.file.transfer.smb.SmbFileTransfer

/**
 * Allows to create separate file transfer of same type but with different settings.
 *
 * Useful in task scripting to communicate with multiple remote servers.
 */
class FileTransferFactory(private val common: CommonExtension) {

    fun <T> http(options: HttpFileTransfer.() -> T) = HttpFileTransfer(common).run(options)

    fun <T> sftp(options: SftpFileTransfer.() -> T) = SftpFileTransfer(common).run(options)

    fun <T> smb(options: SmbFileTransfer.() -> T) = SmbFileTransfer(common).run(options)
}
