package com.cognifide.gradle.common.zip

import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import org.apache.commons.lang3.StringUtils
import java.io.File
import net.lingala.zip4j.ZipFile as Base

/**
 * Only Zip4j correctly extracts big ZIP files (bigger than 10 GB)
 */
class ZipFile(val baseFile: File) {

    private val base = Base(baseFile)

    private fun ensureExists() {
        if (!baseFile.exists()) {
            throw ZipException("ZIP file '$baseFile' does not exist!")
        }
    }

    fun contains(fileName: String): Boolean {
        ensureExists()
        return base.getFileHeader(fileName) != null
    }

    fun containsDir(dirName: String): Boolean {
        ensureExists()
        return base.getFileHeader(StringUtils.appendIfMissing(dirName, "/")) != null
    }

    fun unpackAll(targetPath: String) = unpackAll(File(targetPath))

    fun unpackAll(targetDir: File) {
        ensureExists()
        base.extractAll(targetDir.absolutePath)
    }

    fun packAll(sourceDir: String, options: ZipParameters.() -> Unit = {}) = packAll(File(sourceDir), options)

    fun packAll(sourceDir: File, options: ZipParameters.() -> Unit = {}) {
        val effectiveOptions = options(options)
        (sourceDir.listFiles() ?: arrayOf<File>()).forEach { f ->
            when {
                f.isDirectory -> base.addFolder(f, effectiveOptions)
                f.isFile -> base.addFile(f, effectiveOptions)
            }
        }
    }

    private fun allFileHeaders(): Sequence<FileHeader> {
        ensureExists()
        return base.run { (fileHeaders as List<FileHeader>).asSequence() }
    }

    private fun dirFileHeaders(dirName: String): Sequence<FileHeader> {
        ensureExists()

        val dirFileName = StringUtils.appendIfMissing(dirName, "/")
        if (!contains(dirFileName)) {
            throw ZipException("ZIP file '$baseFile' does not contain directory '$dirName'!")
        }

        return base.run {
            (fileHeaders as List<FileHeader>).asSequence()
                .filter { it.fileName.startsWith(dirFileName) }
        }
    }

    fun unpackDir(dirName: String, dir: String) = unpackDir(dirName, File(dir))

    fun unpackDir(dirName: String, dir: File) {
        dirFileHeaders(dirName).forEach { base.extractFile(it, dir.absolutePath) }
    }

    fun unpackDirTo(fileName: String, targetPath: String) = unpackDirTo(fileName, File(targetPath))

    fun unpackDirTo(dirName: String, targetDir: File) {
        dirFileHeaders(dirName).forEach {
            val fileName = it.fileName
            val fileRelativeName = fileName.substringAfterLast("$dirName/")
            val targetFile = targetDir.resolve(fileRelativeName)
            unpackFile(fileName, targetFile)
        }
    }

    fun walkDir(dirName: String, callback: (FileHeader) -> Unit) = dirFileHeaders(dirName).forEach { callback(it) }

    fun listAll() = allFileHeaders().map { it.fileName }

    fun listDir(dirName: String) = dirFileHeaders(dirName).map { it.fileName }

    fun unpackFile(fileName: String, targetPath: String) = unpackFile(fileName, File(targetPath))

    fun unpackFile(fileName: String, targetFile: File) {
        if (!contains(fileName)) {
            throw ZipException("ZIP file '$baseFile' does not contain file '$fileName'!")
        }

        base.extractFile(fileName, targetFile.parentFile.absolutePath, targetFile.name)
    }

    fun unpackFileTo(fileName: String, targetPath: String) = unpackFileTo(fileName, File(targetPath))

    fun unpackFileTo(fileName: String, targetDir: File) {
        unpackFile(fileName, targetDir.resolve(fileName.substringAfterLast("/")))
    }

    fun addDir(sourcePath: String, options: ZipParameters.() -> Unit = {}) = addDir(File(sourcePath), options)

    fun addDir(sourceDir: File, options: ZipParameters.() -> Unit = {}) {
        baseFile.parentFile.mkdirs()
        base.addFolder(sourceDir, options(options))
    }

    fun readFile(fileName: String): ZipInputStream {
        ensureExists()

        return base.getFileHeader(fileName)?.let { base.getInputStream(it) }
            ?: throw ZipException("ZIP file '$baseFile' does not contain file '$fileName'!")
    }

    fun readFileAsText(fileName: String) = readFile(fileName).use { it.bufferedReader().readText() }

    fun options(options: ZipParameters.() -> Unit) = ZipParameters().apply(OPTIONS_DEFAULT).apply(options)

    companion object {
        private val OPTIONS_DEFAULT: ZipParameters.() -> Unit = {
            compressionMethod = CompressionMethod.STORE
        }
    }
}
