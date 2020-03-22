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

    private var startTime: Long = -1

    var size: Long = 0

    fun download(input: InputStream, target: File) {
        common.progressLogger {
            input.use { inputStream ->
                target.parentFile.mkdirs()
                startTime = System.currentTimeMillis()

                val output = FileOutputStream(target)
                var finished = false

                try {
                    val buf = ByteArray(TRANSFER_CHUNK_100_KB)
                    var read = inputStream.read(buf)

                    while (read >= 0) {
                        output.write(buf, 0, read)
                        logProgress("Downloading", read.toLong(), target)
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

    private fun ProgressLogger.logProgress(operation: String, readBytes: Long, file: File) {
        processedBytes += readBytes

        val processedKb = processedBytes / KILOBYTE
        if (processedKb > loggedKb) {
            val fileName = file.name.removeSuffix(FileTransferManager.TMP_SUFFIX)
            val msg = if (size > 0) {
                "$operation: $fileName | ${Formats.fileSizeBytesToHuman(processedBytes)}/${Formats.fileSizeBytesToHuman(size)}" +
                        " (${Formats.percent(processedBytes, size)}," +
                        " time left: ${Formats.duration(remainingTime())})"
            } else {
                "$operation: $fileName | ${Formats.fileSizeBytesToHuman(processedBytes)}"
            }

            progress(msg)

            loggedKb = processedKb
        }
    }

    private fun remainingTime(): Long {
        val elapsedTime = System.currentTimeMillis() - startTime
        val allTime = (elapsedTime * size / processedBytes)

        return (allTime - elapsedTime).coerceAtLeast(0L)
    }

    companion object {
        const val TRANSFER_CHUNK_100_KB = 100 * 1024

        const val KILOBYTE = 1024
    }
}
