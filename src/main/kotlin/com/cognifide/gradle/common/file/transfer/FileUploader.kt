package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.utils.Formats
import java.io.File
import java.io.OutputStream

class FileUploader(private val common: CommonExtension) {

    private var processedBytes: Long = 0

    private var size: Long = 0

    private var startTime: Long = -1

    var chunkSize: Int = common.prop.int("fileTransfer.uploader.chunkSize") ?: CHUNK_SIZE

    fun upload(file: File, output: OutputStream, cleanup: (File) -> Unit = {}) {
        common.progress {
            updater { update(currentProgress(file)) }

            file.inputStream().use { input ->
                size = file.length()
                startTime = System.currentTimeMillis()

                var finished = false

                try {
                    val buf = ByteArray(chunkSize)
                    var read = input.read(buf)

                    while (read >= 0) {
                        output.write(buf, 0, read)
                        processedBytes += read.toLong()
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

    private fun currentProgress(file: File): String {
        return if (size > 0) {
            "Uploading: ${file.name} | ${Formats.fileSizeBytesToHuman(processedBytes)}/${Formats.fileSizeBytesToHuman(size)}" +
                " (${Formats.percent(processedBytes, size)}," +
                " time left: ${Formats.duration(remainingTime())})"
        } else {
            "Uploading: ${file.name} | ${Formats.fileSizeBytesToHuman(processedBytes)}"
        }
    }

    private fun remainingTime(): Long {
        if (processedBytes == 0L) return 0
        val elapsedTime = System.currentTimeMillis() - startTime
        val allTime = (elapsedTime * size / processedBytes)

        return (allTime - elapsedTime).coerceAtLeast(0L)
    }

    companion object {
        const val KILOBYTE = 1024

        const val CHUNK_SIZE = 512 * KILOBYTE
    }
}
