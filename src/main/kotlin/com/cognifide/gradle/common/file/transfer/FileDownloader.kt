package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.utils.Formats
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FileDownloader(private val common: CommonExtension) {

    private var processedBytes: Long = 0

    private var startTime: Long = -1

    var size: Long = 0

    var chunkSize: Int = common.prop.int("fileTransfer.downloader.chunkSize") ?: CHUNK_SIZE

    fun download(input: InputStream, target: File) {
        common.progress {
            updater { update(currentProgress(target)) }
            input.use { inputStream ->
                target.parentFile.mkdirs()
                startTime = System.currentTimeMillis()

                val output = FileOutputStream(target)
                var finished = false

                try {
                    val buf = ByteArray(chunkSize)
                    var read = inputStream.read(buf)

                    while (read >= 0) {
                        output.write(buf, 0, read)
                        processedBytes += read.toLong()
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

    private fun currentProgress(file: File): String {
        val fileName = file.name.removeSuffix(FileTransferManager.TMP_SUFFIX)
        return if (size > 0) {
            "Downloading: $fileName | ${Formats.fileSizeBytesToHuman(processedBytes)}/${Formats.fileSizeBytesToHuman(size)}" +
                " (${Formats.percent(processedBytes, size)}," +
                " time left: ${Formats.duration(remainingTime())})"
        } else {
            "Downloading: $fileName | ${Formats.fileSizeBytesToHuman(processedBytes)}"
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
