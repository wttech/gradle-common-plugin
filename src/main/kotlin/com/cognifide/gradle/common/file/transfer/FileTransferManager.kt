package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.FileException
import com.cognifide.gradle.common.file.transfer.generic.CustomFileTransfer
import com.cognifide.gradle.common.file.transfer.generic.PathFileTransfer
import com.cognifide.gradle.common.file.transfer.generic.UrlFileTransfer
import com.cognifide.gradle.common.file.transfer.http.HttpFileTransfer
import com.cognifide.gradle.common.file.transfer.resolve.ResolveFileTransfer
import com.cognifide.gradle.common.file.transfer.sftp.SftpFileTransfer
import com.cognifide.gradle.common.file.transfer.smb.SmbFileTransfer
import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.common.utils.using
import java.io.File

/**
 * Facade for transferring files over multiple protocols HTTP/SFTP/SMB and custom.
 *
 * Handles locking files for avoiding uncompleted downloads.
 * Prevents unnecessary download if file on local server already exist.
 * Prevents unnecessary uploads if file on remote servers already exist.
 */
class FileTransferManager(private val common: CommonExtension) : FileTransfer {

    private val logger = common.project.logger

    val user = common.obj.string {
        common.prop.string("fileTransfer.user")?.let { set(it) }
    }

    val password = common.obj.string {
        common.prop.string("fileTransfer.password")?.let { set(it) }
    }

    var bearerToken = common.obj.string {}

    val domain = common.obj.string {
        common.prop.string("fileTransfer.domain")?.let { set(it) }
    }

    val downloadOverwrite = common.obj.boolean {
        convention(false)
        common.prop.boolean("fileTransfer.downloadOverwrite")?.let { set(it) }
    }

    val uploadOverwrite = common.obj.boolean {
        convention(false)
        common.prop.boolean("fileTransfer.uploadOverwrite")?.let { set(it) }
    }

    val credentials: Pair<String, String>
        get() = if (user.orNull.isNullOrBlank() && password.orNull.isNullOrBlank())
            user.get() to password.get()
        else
            throw FileTransferException("File transfer credentials are missing!")

    val credentialsString get() = credentials.run { "$first:$second" }

    /**
     * Shorthand method to enforce credentials for all protocols requiring it.
     *
     * Useful only in specific cases, when e.g. company storage offers accessing files via multiple protocols
     * using same AD credentials.
     */
    fun credentials(user: String?, password: String?, domain: String? = null) {
        this.user.set(user)
        this.password.set(password)
        this.domain.set(domain)

        http.client.basicUser.set(user)
        http.client.basicPassword.set(password)

        sftp.user.set(user)
        sftp.password.set(password)

        smb.user.set(user)
        smb.password.set(password)
        smb.domain.set(domain)
    }

    val factory = FileTransferFactory(common)

    val http = HttpFileTransfer(common).apply {
        client.basicUser.convention(user)
        client.basicPassword.convention(password)
        client.bearerToken.convention(bearerToken)
    }

    fun http(options: HttpFileTransfer.() -> Unit) = http.using(options)

    val sftp = SftpFileTransfer(common).apply {
        user.convention(this@FileTransferManager.user)
        password.convention(this@FileTransferManager.password)
    }

    fun sftp(options: SftpFileTransfer.() -> Unit) = sftp.using(options)

    val smb = SmbFileTransfer(common).apply {
        user.convention(this@FileTransferManager.user)
        password.convention(this@FileTransferManager.password)
        domain.convention(this@FileTransferManager.domain)
    }

    fun smb(options: SmbFileTransfer.() -> Unit) = smb.using(options)

    val resolve = ResolveFileTransfer(common)

    fun resolve(options: ResolveFileTransfer.() -> Unit) = resolve.using(options)

    val url = UrlFileTransfer(common)

    fun url(options: UrlFileTransfer.() -> Unit) = url.using(options)

    val path = PathFileTransfer(common)

    fun path(options: PathFileTransfer.() -> Unit) = path.using(options)

    private val custom = mutableListOf<CustomFileTransfer>()

    private val all get() = (custom + arrayOf(http, sftp, smb, resolve, url, path)).filter { it.enabled.get() }

    /**
     * Downloads file from specified URL to temporary directory with preserving file name.
     */
    override fun download(fileUrl: String): File = common.temporaryFile(FileUtils.nameFromUrl(fileUrl))
        .also { download(fileUrl, it) }

    /**
     * Downloads file of given name from directory at specified URL.
     */
    override fun downloadFrom(dirUrl: String, fileName: String, target: File) = downloadUsing(handling(dirUrl), dirUrl, fileName, target)

    /**
     * Downloads file from specified URL using dedicated transfer type.
     */
    fun downloadUsing(transfer: FileTransfer, fileUrl: String, target: File) {
        val (dirUrl, fileName) = FileUtils.splitUrl(fileUrl)
        return downloadUsing(transfer, dirUrl, fileName, target)
    }

    /**
     * Downloads file of given name from directory at specified URL using dedicated transfer type.
     */
    fun downloadUsing(transfer: FileTransfer, dirUrl: String, fileName: String, target: File) {
        if (target.exists()) {
            if (!downloadOverwrite.get()) {
                logger.info("Skipping downloading file from URL '$dirUrl/$fileName' to '$target' as of it already exists.")
                return
            }
            logger.info("Downloading file from URL '$dirUrl/$fileName' overwrites existing file '$target'.")
        }

        target.parentFile.mkdirs()

        val tmp = File(target.parentFile, "${target.name}$TMP_SUFFIX")
        if (tmp.exists()) {
            tmp.delete()
        }

        val started = System.currentTimeMillis()
        transfer.downloadFrom(dirUrl, fileName, tmp)
        tmp.renameTo(target)

        logger.info("Downloaded file from URL '$dirUrl/$fileName' to '$target' in ${Formats.durationWordsSince(started)}")
    }

    /**
     * Uploads file to directory at specified URL and set given name.
     */
    override fun uploadTo(dirUrl: String, fileName: String, source: File) = uploadUsing(handling(dirUrl), dirUrl, fileName, source)

    /**
     * Uploads file to file at specified URL using dedicated transfer type.
     */
    fun uploadUsing(transfer: FileTransfer, fileUrl: String, source: File) {
        val (dirUrl, fileName) = FileUtils.splitUrl(fileUrl)
        return uploadUsing(transfer, dirUrl, fileName, source)
    }

    /**
     * Uploads file to directory at specified URL and set given name using dedicated transfer type.
     */
    fun uploadUsing(transfer: FileTransfer, dirUrl: String, fileName: String, source: File) {
        val fileUrl = "$dirUrl/$fileName"

        try {
            if (stat(dirUrl, fileName) != null) { // 'stat' may be unsupported
                if (!uploadOverwrite.get()) {
                    logger.info("Skipping uploading file to URL '$fileUrl' as of it already exists on server.")
                    return
                }
                logger.info("Uploading file from '$source' overwrites existing file at URL '$fileUrl'.")
            }
        } catch (e: FileException) {
            logger.debug("Cannot check status of uploaded file at URL '$fileUrl'", e)
        }

        val started = System.currentTimeMillis()
        transfer.uploadTo(dirUrl, fileName, source)

        logger.info("Uploaded file from '$source' to URL '$dirUrl/$fileName' in ${Formats.durationWordsSince(started)}")
    }

    /**
     * Lists files in directory available at specified URL.
     */
    override fun list(dirUrl: String): List<FileEntry> = handling(dirUrl).list(dirUrl)

    /**
     * Deletes file of given name in directory at specified URL.
     */
    override fun deleteFrom(dirUrl: String, fileName: String) = handling(dirUrl).deleteFrom(dirUrl, fileName)

    /**
     * Deletes all files in directory available at specified URL.
     */
    override fun truncate(dirUrl: String) = handling(dirUrl).truncate(dirUrl)

    /**
     * Gets file status of given name in directory at specified URL.
     */
    override fun stat(dirUrl: String, fileName: String): FileEntry? = handling(dirUrl).stat(dirUrl, fileName)

    /**
     * Check if there is any file transfer supporting specified URL.
     */
    override fun handles(fileUrl: String): Boolean = all.any { it.handles(fileUrl) }

    /**
     * Get file transfer supporting specified URL.
     */
    fun handling(fileUrl: String): FileTransferHandler = all.find { it.handles(fileUrl) }
        ?: throw FileException("File transfer supporting URL '$fileUrl' not found!")

    /**
     * Register custom file transfer for e.g downloading / uploading files from cloud storages like:
     * Amazon S3, Google Cloud Storage etc.
     */
    fun custom(name: String, definition: CustomFileTransfer.() -> Unit) {
        custom.add(
            CustomFileTransfer(common).apply {
                this.name = name
                this.protocols = listOf("$name://*")

                apply(definition)
            }
        )
    }

    /**
     * Get custom (or built-in) file transfer by name.
     */
    fun named(name: String): FileTransfer = all.find { it.name == name }
        ?: throw FileException("File transfer named '$name' not found!")

    companion object {
        const val NAME = "manager"

        const val TMP_SUFFIX = ".tmp"
    }
}
