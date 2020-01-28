package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.build.ProgressLogger
import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.common.CommonExtension
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FileDownloader(private val common: CommonExtension) {

    private var processedBytes: Long = 0

    private var loggedKb: Long = 0

    fun remainingDownloadTime(startDownloadTime: Long, fullLength: Long): Long {
        val elapsedDownloadTime = System.currentTimeMillis() - startDownloadTime
        val allDownloadTime = (elapsedDownloadTime * fullLength / processedBytes)

        return (allDownloadTime - elapsedDownloadTime).coerceAtLeast(0L)
    }

    fun ProgressLogger.logProgress(
        operation: String,
        readLength: Long,
        fullLength: Long,
        file: File,
        startDownloadTime: Long
    ) {
        processedBytes += readLength

        val processedKb = processedBytes / KILOBYTE
        if (processedKb > loggedKb) {
            val fileName = file.name.removeSuffix(FileTransferManager.TMP_SUFFIX)
            val msg = if (fullLength > 0) {
                "$operation: $fileName | ${Formats.fileSizeBytesToHuman(processedBytes)}/${Formats.fileSizeBytesToHuman(fullLength)}" +
                        " (${Formats.percent(processedBytes, fullLength)}," +
                        " time left: ${Formats.duration(remainingDownloadTime(startDownloadTime, fullLength))})"
            } else {
                "$operation: $fileName | ${Formats.fileSizeBytesToHuman(processedBytes)}"
            }

            progress(msg)

            loggedKb = processedKb
        }
    }

    fun download(size: Long, input: InputStream, target: File) {
        common.progressLogger {
            input.use { inputStream ->
                target.parentFile.mkdirs()

                val output = FileOutputStream(target)
                var finished = false
                val startDownloadTime = System.currentTimeMillis()

                try {
                    val buf = ByteArray(TRANSFER_CHUNK_100_KB)
                    var read = inputStream.read(buf)

                    while (read >= 0) {
                        output.write(buf, 0, read)
                        logProgress("Downloading", read.toLong(), size, target, startDownloadTime)
                        read = inputStream.read(buf)
                    }

                    output.flush()
                    finished = true
                } finally {
                    output.close()
                    if (!finished) {
                        target.delete()
                    }
                }
            }
        }
    }

    companion object {
        const val TRANSFER_CHUNK_100_KB = 100 * 1024

        const val KILOBYTE = 1024
    }
}
