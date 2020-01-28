package com.cognifide.gradle.common.file.transfer.sftp

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.FileEntry
import com.cognifide.gradle.common.file.transfer.ProtocolFileTransfer
import java.io.File
import java.io.IOException
import org.apache.http.client.utils.URIBuilder
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.subsystem.sftp.SftpClient
import org.apache.sshd.client.subsystem.sftp.SftpClientFactory
import org.apache.sshd.common.subsystem.sftp.SftpConstants
import org.apache.sshd.common.subsystem.sftp.SftpException

class SftpFileTransfer(common: CommonExtension) : ProtocolFileTransfer(common) {

    var user: String? = common.prop.string("fileTransfer.sftp.user")

    var password: String? = common.prop.string("fileTransfer.sftp.password")

    var timeout: Long = common.prop.long("fileTransfer.sftp.timeout") ?: 60000L

    var clientOptions: SshClient.() -> Unit = {}

    fun client(options: SshClient.() -> Unit) {
        this.clientOptions = options
    }

    var sessionOptions: ClientSession.() -> Unit = {}

    fun session(options: ClientSession.() -> Unit) {
        this.sessionOptions = options
    }

    override val name: String
        get() = NAME

    override val protocols: List<String>
        get() = listOf("sftp://*")

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        val fileUrl = "$dirUrl/$fileName"

        connectDir(dirUrl) { dirPath ->
            try {
                common.logger.info("Downloading file from URL '$fileUrl'")
                val filePath = "$dirPath/$fileName"
                downloader().download(stat(filePath).size, read(filePath), target)
            } catch (e: IOException) {
                throw SftpFileException("Cannot download file from URL '$fileUrl'. Cause: '${e.message}")
            }
        }
    }

    override fun uploadTo(dirUrl: String, fileName: String, source: File) {
        val fileUrl = "$dirUrl/$fileName"

        connectDir(dirUrl) { dirPath ->
            try {
                common.logger.info("Uploading file to URL '$fileUrl'")
                val filePath = "$dirPath/$fileName"
                uploader().upload(source, write(filePath))
            } catch (e: IOException) {
                throw SftpFileException("Cannot upload file '$source' to URL '$fileUrl'. Cause: '${e.message}", e)
            }
        }
    }

    override fun deleteFrom(dirUrl: String, fileName: String) {
        connectDir(dirUrl) { dirPath ->
            try {
                val filePath = "$dirPath/$fileName"
                remove(filePath)
            } catch (e: IOException) {
                throw SftpFileException("Cannot delete file at URL '$dirUrl/$fileName'. Cause: '${e.message}", e)
            }
        }
    }

    override fun list(dirUrl: String): List<FileEntry> {
        return connectDir(dirUrl) { dirPath ->
            try {
                common.logger.info("Listing files at URL '$dirUrl'")
                dirFiles(dirPath).map { FileEntry(it.filename, it.attributes.size, it.attributes.modifyTime.toMillis()) }
            } catch (e: IOException) {
                throw SftpFileException("Cannot list files in directory at URL '$dirUrl'. Cause: '${e.message}", e)
            }
        }
    }

    override fun truncate(dirUrl: String) {
        connectDir(dirUrl) { dirPath ->
            try {
                common.logger.info("Truncating files at URL '$dirUrl'")
                dirFiles(dirPath).forEach { remove("$dirPath/${it.filename}") }
            } catch (e: IOException) {
                throw SftpFileException("Cannot truncate directory at URL '$dirUrl'. Cause: '${e.message}", e)
            }
        }
    }

    override fun stat(dirUrl: String, fileName: String): FileEntry? {
        val fileUrl = "$dirUrl/$fileName"

        return connectDir(dirUrl) { dirPath ->
            try {
                common.logger.info("Checking file status at URL '$fileUrl'")
                stat("$dirPath/$fileName").takeIf { it.isRegularFile }?.run {
                    FileEntry(fileName, size, modifyTime.toMillis())
                }
            } catch (e: IOException) {
                when {
                    e is SftpException && STATUS_NOT_EXISTS.contains(e.status) -> null
                    else -> throw SftpFileException("Cannot check file status at URL '$fileUrl'. Cause: '${e.message}", e)
                }
            }
        }
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    fun <T> connect(url: String, callback: SftpClient.(String) -> T): T {
        val urlConfig = URIBuilder(url)
        val userInfo = urlConfig.userInfo?.split(":") ?: listOf()
        val user = userInfo.takeIf { it.isNotEmpty() }?.get(0) ?: user
        val password = userInfo.takeIf { it.size == 2 }?.get(1) ?: password
        val port = if (urlConfig.port >= 0) urlConfig.port else PORT_DEFAULT
        val host = urlConfig.host

        try {
            SshClient.setUpDefaultClient().use { client ->
                client.apply(clientOptions)
                client.start()
                client.connect(user, host, port).apply { await(timeout) }.session.use { session ->
                    session.apply(sessionOptions)
                    if (!password.isNullOrBlank()) {
                        session.addPasswordIdentity(password)
                    }
                    session.auth().await(timeout)

                    SftpClientFactory.instance().createSftpClient(session).use { sftp ->
                        return callback(sftp, urlConfig.path).also { client.stop() }
                    }
                }
            }
        } catch (e: IOException) {
            throw SftpFileException("SFTP file transfer error (check credentials, network / VPN etc)", e)
        }
    }

    fun <T> connectDir(dirUrl: String, callback: SftpClient.(String) -> T): T {
        return connect(dirUrl) { dirPath ->
            try {
                if (!lstat(dirPath).isDirectory) {
                    throw SftpFileException("Path at URL '$dirUrl' is not a directory.")
                }
            } catch (e: IOException) {
                throw SftpFileException("Directory at URL '$dirUrl' does not exist or not accessible: '${e.message}'!", e)
            }

            callback(dirPath)
        }
    }

    private fun SftpClient.dirFiles(dirPath: String) = listDir(openDir(dirPath)).filter { it.attributes.isRegularFile }

    companion object {
        const val NAME = "sftp"

        const val PORT_DEFAULT = 22

        val STATUS_NOT_EXISTS = arrayOf(
                SftpConstants.SSH_FX_NO_SUCH_FILE,
                SftpConstants.SSH_FX_NO_SUCH_PATH
        )
    }
}