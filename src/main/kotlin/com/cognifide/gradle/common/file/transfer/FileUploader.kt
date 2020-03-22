package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.build.ProgressLogger
import com.cognifide.gradle.common.utils.Formats
import java.io.File
import java.io.OutputStream

class FileUploader(private val common: CommonExtension) {

    private var processedBytes: Long = 0

    private var loggedKb: Long = 0

    private var size: Long = 0

    private var startTime: Long = -1

    fun upload(file: File, output: OutputStream, cleanup: (File) -> Unit = {}) {
        common.progressLogger {
            file.inputStream().use { input ->
                size = file.length()
                startTime = System.currentTimeMillis()

                var finished = false

                try {
                    val buf = ByteArray(TRANSFER_CHUNK_100_KB)
                    var read = input.read(buf)

                    while (read >= 0) {
                        output.write(buf, 0, read)
                        logProgress("Uploading", read.toLong(), file)
                        read = input.read(buf)
                    }

                    output.flush()
                    finished = true
                } finally {
                    output.close()
                    if (!finished) {
                        cleanup(file)
                    }
                }
            }
        }
    }

    private fun ProgressLogger.logProgress(operation: String, readBytes: Long, file: File) {
        processedBytes += readBytes

        val processedKb = processedBytes / KILOBYTE
        if (processedKb > loggedKb) {
            val msg = if (size > 0) {
                "$operation: ${file.name} | ${Formats.fileSizeBytesToHuman(processedBytes)}/${Formats.fileSizeBytesToHuman(size)}" +
                        " (${Formats.percent(processedBytes, size)}," +
                        " time left: ${Formats.duration(remainingTime())})"
            } else {
                "$operation: ${file.name} | ${Formats.fileSizeBytesToHuman(processedBytes)}"
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
