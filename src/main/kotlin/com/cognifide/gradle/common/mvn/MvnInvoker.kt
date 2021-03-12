package com.cognifide.gradle.common.mvn

import com.cognifide.gradle.common.CommonExtension
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecSpec
import java.io.File

class MvnInvoker(private val common: CommonExtension) {

    val workingDir = common.obj.dir()

    fun workingDir(dir: File) {
        workingDir.set(dir)
    }

    val executableDir = common.obj.dir {
        common.prop.file("mvn.executableDir")?.let { set(it) }
    }

    val executableOld = common.obj.boolean {
        convention(false)
        common.prop.boolean("mvn.executableOld")?.let { set(it) }
    }

    val executableArgs = common.obj.strings {
        set(common.project.provider {
            val windows = OperatingSystem.current().isWindows
            val wrapper = workingDir.get().asFile.run { resolve("mvnw.cmd").exists() || resolve("mvnw").exists() }
            val bin = when {
                wrapper -> when {
                    windows -> if (executableOld.get()) "mvnw.bat" else "mvnw.cmd"
                    else -> "mvnw"
                }
                else -> "mvn"
            }
            val executable = executableDir.orNull?.asFile?.resolve(bin)?.absolutePath ?: bin
            when {
                windows -> listOf("cmd", "/c", executable)
                else -> listOf(executable)
            }
        })
    }

    val args = common.obj.strings {
        set(listOf("-B"))
        common.prop.string("mvn.args")?.let { set(it.split(" ")) }
    }

    fun args(vararg values: String) = args(values.asIterable())

    fun args(values: Iterable<String>) {
        args.addAll(values)
    }

    private var specOptions: ExecSpec.() -> Unit = {}

    fun spec(options: ExecSpec.() -> Unit) {
        this.specOptions = options
    }

    @Suppress("TooGenericExceptionCaught")
    fun invoke() {
        val dir = workingDir.get().asFile
        if (!dir.exists()) {
            throw MvnException("Cannot run Maven build at non-existing directory: '$workingDir'!")
        }
        val clArgs = executableArgs.get() + args.get()
        try {
            common.project.exec { spec ->
                spec.apply(specOptions)
                spec.workingDir(workingDir)
                spec.commandLine = clArgs
            }
        } catch (e: Exception) {
            throw MvnException(listOf(
                "Cannot invoke Maven properly!",
                "Directory: $dir",
                "Args: ${clArgs.joinToString(" ")}",
                "Cause: ${e.message}"
            ).joinToString("\n"), e)
        }
    }
}
