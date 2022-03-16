package com.cognifide.gradle.common.file

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.watcher.DelegatingFileAlterationListener
import com.cognifide.gradle.common.file.watcher.Event
import com.cognifide.gradle.common.utils.Patterns
import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver

open class FileWatcher(val common: CommonExtension) {

    val dirs = common.obj.files()

    val interval = common.obj.long { convention(500L) }

    val ignores = common.obj.strings { convention(listOf("**/*___jb_*___")) }

    fun onChange(action: (Event) -> Unit) {
        this.onChange = action
    }

    private var onChange: ((Event) -> Unit)? = null

    fun start() {
        if (dirs.isEmpty) {
            throw FileWatcherException("File watcher directories are not specified!")
        }

        if (onChange == null) {
            throw FileWatcherException("File watcher on change callback is not specified!")
        }

        // Start watching

        val monitor = FileAlterationMonitor(interval.get()).apply {
            dirs.forEach { dir ->
                addObserver(
                    FileAlterationObserver(dir).apply {
                        addListener(
                            DelegatingFileAlterationListener { event ->
                                if (!Patterns.wildcard(event.file, ignores.get())) {
                                    onChange!!.invoke(event)
                                }
                            }
                        )
                    }
                )
            }

            start()
        }

        // Clean up on exit // TODO remove/refactor shutdown hook somehow

        Runtime.getRuntime().addShutdownHook(
            Thread(
                Runnable {
                    try {
                        monitor.stop()
                    } catch (ignored: Exception) {
                        // ignore
                    }
                }
            )
        )
    }
}
