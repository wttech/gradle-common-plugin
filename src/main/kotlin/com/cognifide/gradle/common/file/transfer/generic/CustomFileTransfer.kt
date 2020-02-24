package com.cognifide.gradle.common.file.transfer.generic

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.FileEntry
import com.cognifide.gradle.common.file.transfer.ProtocolFileTransfer
import java.io.File

/**
 * Represents dynamically created file transfer via build script.
 *
 * Allows to implement file transfer supporting cloud storage like Amazon S3, Google Cloud Storage etc
 * and use them for uploading, downloading and listing files.
 */
class CustomFileTransfer(common: CommonExtension) : ProtocolFileTransfer(common) {

    override lateinit var name: String

    override lateinit var protocols: List<String>

    override val parallelable = common.obj.provider { true }

    /**
     * Register callback responsible for downloading file.
     */
    fun download(callback: (dirUrl: String, fileName: String, target: File) -> Unit) {
        this.downloader = callback
    }

    private var downloader: ((dirUrl: String, fileName: String, target: File) -> Unit)? = null

    /**
     * Register callback responsible for uploading file.
     */
    fun upload(callback: (dirUrl: String, fileName: String, target: File) -> Unit) {
        this.uploader = callback
    }

    private var uploader: ((dirUrl: String, fileName: String, target: File) -> Unit)? = null

    /**
     * Register callback responsible for listing files.
     */
    fun list(callback: (dirUrl: String) -> List<FileEntry>) {
        this.lister = callback
    }

    private var lister: ((dirUrl: String) -> List<FileEntry>)? = null

    /**
     * Register callback responsible for deleting file.
     */
    fun delete(callback: (dirUrl: String, fileName: String) -> Unit) {
        this.deleter = callback
    }

    private var deleter: ((dirUrl: String, fileName: String) -> Unit)? = null

    /**
     * Register callback responsible for deleting files.
     */
    fun truncate(callback: (dirUrl: String) -> Unit) {
        this.truncater = callback
    }

    private var truncater: ((dirUrl: String) -> Unit)? = null

    /**
     * Register callback responsible for checking file existence.
     */
    fun exists(callback: (dirUrl: String, fileName: String) -> Boolean) {
        this.exister = callback
    }

    private var exister: ((dirUrl: String, fileName: String) -> Boolean)? = null

    // Below delegating lambdas to interface, nothing interesting :)

    override fun downloadFrom(dirUrl: String, fileName: String, target: File) {
        downloader?.invoke(dirUrl, fileName, target) ?: super.downloadFrom(dirUrl, fileName, target)
    }

    override fun uploadTo(dirUrl: String, fileName: String, source: File) {
        uploader?.invoke(dirUrl, fileName, source) ?: super.uploadTo(dirUrl, fileName, source)
    }

    override fun deleteFrom(dirUrl: String, fileName: String) {
        deleter?.invoke(dirUrl, fileName) ?: super.deleteFrom(dirUrl, fileName)
    }

    override fun list(dirUrl: String): List<FileEntry> {
        return lister?.invoke(dirUrl) ?: super.list(dirUrl)
    }

    override fun truncate(dirUrl: String) {
        truncater?.invoke(dirUrl) ?: super.truncate(dirUrl)
    }

    override fun exists(dirUrl: String, fileName: String): Boolean {
        return exister?.invoke(dirUrl, fileName) ?: super.exists(dirUrl)
    }
}
