package com.cognifide.gradle.common.file.resolver

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.FileException
import com.cognifide.gradle.common.file.transfer.http.HttpFileTransfer
import com.cognifide.gradle.common.file.transfer.resolve.ResolveFileTransfer
import com.cognifide.gradle.common.file.transfer.sftp.SftpFileTransfer
import com.cognifide.gradle.common.file.transfer.smb.SmbFileTransfer
import com.cognifide.gradle.common.utils.Patterns
import com.google.common.hash.HashCode
import java.io.File
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.gradle.api.InvalidUserDataException

/**
 * File downloader with groups supporting files from multiple sources: local and remote (SFTP, SMB, HTTP).
 */
@Suppress("TooManyFunctions")
abstract class Resolver<G : FileGroup>(val common: CommonExtension) {

    val downloadDir = common.obj.buildDir(DOWNLOAD_DIR_DEFAULT)

    private val project = common.project

    private val groupList = mutableListOf<G>()

    private val groupDefault get() = groupNamed(GROUP_DEFAULT)

    private var _groupCurrent: G? = null

    private var groupCurrent
        get() = _groupCurrent ?: groupDefault
        set(group) {
            _groupCurrent = group
        }

    /**
     * Controls count of groups resolved in parallel.
     */
    val parallelLevel = common.obj.int {
        convention(3)
        common.prop.int("resolver.parallelLevel")?.let { set(it) }
    }

    /**
     * Files respected when searching for recent local files.
     */
    val localFilePatterns = common.obj.strings {
        convention(listOf("**/*.zip", "**/*.jar"))
        common.prop.list("resolver.localFilePatterns")?.let { set(it) }
    }

    val groups get() = groupList.filter { it.resolutions.isNotEmpty() }

    fun resolveGroups(groupName: String) = groups.filter { Patterns.wildcard(it.name, groupName) }.apply {
        common.progress {
            step = "Resolving files"
            total = size.toLong()

            if (parallelLevel.get() <= 1) {
                forEach { group ->
                    increment("Group '${group.name}'") { group.resolve() }
                }
            } else {
                val (parallel, sequential) = partition { it.parallelable }
                common.parallel.poolEach(parallelLevel.get(), "resolver", parallel) { group ->
                    increment("Group '${group.name}'") { group.resolve() }
                }
                sequential.forEach { group ->
                    increment("Group '${group.name}'") { group.resolve() }
                }
            }
        }
    }

    fun groupFiles(name: String) = resolveGroups(name).flatMap { it.files }

    val files get() = groupFiles(Patterns.WILDCARD)

    fun group(name: String) = groupList.find { it.name == name }
            ?: throw FileException("File group '$name' is not defined.")

    /**
     * Resolve files in case of various type of specified value: file, url to file, dependency notation, project dependency.
     */
    fun getAll(vararg values: Any) = getAll(values.asIterable())

    /**
     * Resolve files in case of various type of specified value: file, url to file, dependency notation, project dependency.
     */
    fun getAll(values: Iterable<Any>) = values.forEach { get(it) }

    /**
     * Resolve file in case of various type of specified value: file, url to file, dependency notation, project dependency.
     */
    @Suppress("NestedBlockDepth")
    fun get(value: Any): FileResolution = common.fileTransfer.run {
        try {
            // local file
            useLocal(project.file(value))
        } catch (e: InvalidUserDataException) {
            // files resolved using protocols
            if (value is String) {
                handling(value).run {
                    if (this is ResolveFileTransfer) { // support for special protocol 'resolve'
                        resolveFile(value, parallelable.get()) { resolve.resolve(value) }
                    } else { // other protocols like 'http', 'sftp', 'smb'
                        resolveFileUrl(value, parallelable.get()) { downloadUsing(this, value, it) }
                    }
                }
            } else {
                // cross project / build dependencies
                resolveFile(value, false) { resolve.resolve(value) }
            }
        }
    }

    /**
     * Resolve file by dependency notation using defined Gradle repositories (Maven, Ivy etc).
     */
    fun resolve(dependencyNotation: Any): FileResolution = get(dependencyNotation)

    /**
     * Download files from same URL using automatically determined file transfer (HTTP, SFTP, SMB, URL, local file system).
     */
    fun download(urlDir: String, vararg fileNames: String) = download(urlDir, fileNames.asIterable())

    /**
     * Download files from same URL using automatically determined file transfer (HTTP, SFTP, SMB, URL, local file system).
     */
    fun download(urlDir: String, fileNames: Iterable<String>) = fileNames.map { fileName ->
        download("$urlDir/$fileName")
    }

    /**
     * Download file using automatically determined file transfer (HTTP, SFTP, SMB, URL, local file system).
     *
     * Same global settings (like basic auth credentials of HTTP) of each particular file transfer will be used
     * for all files downloaded. This shorthand method assumes that mostly only single HTTP / SFTP / SMB server
     * will be used to download all files.
     *
     * To use many remote servers with different settings, simply use dedicated methods 'download[Http/Sftp/Smb]'
     * when declaring each file to be downloaded.
     */
    fun download(url: String): FileResolution = get(url as Any)

    /**
     * Download file using HTTP file transfer with custom settings (like basic auth credentials).
     *
     * Use only when using more than one remote HTTP server to download files.
     */
    fun downloadHttp(url: String, options: HttpFileTransfer.() -> Unit): FileResolution {
        return resolveFileUrl(url, true) { common.httpFile { options(); download(url, it) } }
    }

    /**
     * Download file using SFTP file transfer with custom settings (different credentials).
     *
     * Use only when using more than one remote SFTP server to download files.
     */
    fun downloadSftp(url: String, options: SftpFileTransfer.() -> Unit): FileResolution {
        return resolveFileUrl(url, true) { common.sftpFile { options(); download(url, it) } }
    }

    /**
     * Download file using SMB file transfer with custom settings (different credentials, domain).
     *
     * Use only when using more than one remote SMB server to download files.
     */
    fun downloadSmb(url: String, options: SmbFileTransfer.() -> Unit): FileResolution {
        return resolveFileUrl(url, true) { common.smbFile { options(); download(url, it) } }
    }

    /**
     * Use local file directly (without copying).
     */
    fun useLocal(path: String): FileResolution = useLocal(project.file(path))

    /**
     * Use local file directly (without copying).
     */
    fun useLocal(sourceFile: File): FileResolution = useLocal(sourceFile, true)

    /**
     * Use local file from directory or file when not found any.
     */
    fun useLocalBy(dir: Any, filePatterns: Iterable<String>, selector: (Iterable<File>).() -> File?): FileResolution? {
        return resolveFile(listOf(dir, filePatterns), true) {
            common.project.fileTree(dir) { it.include(filePatterns) }.run(selector)
                    ?: throw FileException("Cannot find any local file under directory '$dir' matching file pattern '$filePatterns'!")
        }
    }

    /**
     * Use local file from directory or file when not found any.
     */
    fun useLocalBy(dir: Any, selector: (Iterable<File>).() -> File?) = useLocalBy(dir, localFilePatterns.get(), selector)

    /**
     * Use local file with name being highest version located in directory or fail when not found any.
     * Highest version is determined by descending alphanumeric sorting.
     */
    fun useLocalLastNamed(dir: Any) = useLocalBy(dir) { maxBy { it.name } }

    /**
     * Use last modified local file located in directory or fail when not found any.
     */
    fun useLocalLastModified(dir: Any) = useLocalBy(dir) { maxBy { it.lastModified() } }

    /**
     * Use last modified local file located in directory or fail when not found any.
     */
    fun useLocalRecent(dir: Any) = useLocalLastModified(dir)

    /**
     * Customize configuration for particular file group.
     */
    fun config(configurer: G.() -> Unit) {
        groupCurrent.apply(configurer)
    }

    @Synchronized
    private fun groupNamed(name: String): G {
        return groupList.find { it.name == name } ?: createGroup(name).apply { groupList.add(this) }
    }

    @Synchronized
    fun group(name: String, configurer: Resolver<G>.() -> Unit) {
        groupCurrent = groupNamed(name)
        this.apply(configurer)
        groupCurrent = groupDefault
    }

    operator fun String.invoke(value: Any, options: G.() -> Unit = {}) = group(this) {
        get(value)
        config(options)
    }

    operator fun String.invoke(url: String, vararg fileNames: String, options: G.() -> Unit = {}) = group(this) {
        when {
            fileNames.isEmpty() -> download(url)
            else -> download(url, fileNames.asIterable())
        }
        config(options)
    }

    operator fun String.invoke(configurer: Resolver<G>.() -> Unit) = group(this, configurer)

    /**
     * Shorthand for creating named group with single file only to be downloaded.
     */
    fun group(name: String, downloadUrl: String) = group(name) { download(downloadUrl) }

    abstract fun createGroup(name: String): G

    private fun resolveFile(hash: Any, parallel: Boolean, resolver: (FileResolution) -> File): FileResolution {
        val id = HashCode.fromInt(HashCodeBuilder().append(hash).toHashCode()).toString()
        if (!parallel) {
            groupCurrent.parallelable = false
        }

        return groupCurrent.resolve(id, resolver)
    }

    private fun resolveFileUrl(url: String, parallel: Boolean, resolver: (File) -> Unit): FileResolution {
        return resolveFile(url, parallel) { File(it.dir, FilenameUtils.getName(url)).apply { resolver(this) } }
    }

    private fun useLocal(sourceFile: File, parallel: Boolean) = resolveFile(sourceFile.absolutePath, parallel) { sourceFile }

    companion object {
        const val GROUP_DEFAULT = "default"

        const val DOWNLOAD_DIR_DEFAULT = "download"
    }
}
