package com.cognifide.gradle.common.os

import org.gradle.internal.os.OperatingSystem
import java.io.BufferedReader
import java.io.InputStreamReader

object OS {

    /**
     * Checks if OS architecture is ARM64.
     */
    fun isArm64() = System.getProperty("os.arch") == "arm64" || isRosetta()

    /**
     * Checks if OS architecture is emulated as AMD64 but hosted on ARM64
     */
    fun isRosetta(): Boolean = OperatingSystem.current().isMacOsX
            && execAsString("sysctl", "systctl.proc_translated") == "sysctl.proc_translated: 1"

    fun execAsString(vararg command: String) = execAsString(command.toList())

    fun execAsString(command: List<String>): String {
        val process = ProcessBuilder(command).start()
        val input = BufferedReader(InputStreamReader(process.inputStream))
        return input.use { it.readText() }
    }
}