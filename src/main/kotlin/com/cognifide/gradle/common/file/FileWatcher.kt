package com.cognifide.gradle.common.file

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.watcher.DelegatingFileAlterationListener
import com.cognifide.gradle.common.file.watcher.Event
import com.cognifide.gradle.common.utils.Patterns
import java.io.File
import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver

open class FileWatcher(val common: CommonExtension) {

    lateinit var dirs: List<File>

    lateinit var onChange: (Event) -> Unit

    var interval = 500L

    var ignores = mutableListOf("**/*___jb_*___")

    fun ignore(vararg paths: String) = ignore(paths.toList())

    fun ignore(paths: Iterable<String>) {
        ignores.addAll(paths)
    }

    fun start() {
        if (!::dirs.isInitialized) {
            throw FileWatcherException("File watcher directories are not specified!")
        }

        if (!::onChange.isInitialized) {
            throw FileWatcherException("File watcher on change callback is not specified!")
        }

        // register watching

        val monitor = FileAlterationMonitor(interval).apply {
            dirs.forEach { dir ->
                addObserver(FileAlterationObserver(dir).apply {
                    addListener(DelegatingFileAlterationListener { event ->
                        if (!Patterns.wildcard(event.file, ignores)) {
                            onChange(event)
                        }
                    })
                })
            }

            start()
        }

        // handle on exit
        // TODO remove/refactor shutdown hook somehow
        Runtime.getRuntime().addShutdownHook(Thread(Runnable {
            try {
                monitor.stop()
            } catch (ignored: Exception) {
                // ignore
            }
        }))
    }
}
