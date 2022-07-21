package com.cognifide.gradle.common.os

import org.buildobjects.process.ProcBuilder
import org.gradle.internal.os.OperatingSystem

object OS {

    fun arch() = System.getProperty("os.arch")

    fun archOfHost() = if (detectRosetta()) "arm64" else arch()

    fun detectRosetta(): Boolean = OperatingSystem.current().isMacOsX &&
        exec("sysctl", "-n", "sysctl.proc_translated").trim() == "1"

    @Suppress("SpreadOperator")
    fun exec(command: String, vararg args: String) = ProcBuilder(command)
        .withArgs(*args)
        .ignoreExitStatus()
        .run().outputString
}
