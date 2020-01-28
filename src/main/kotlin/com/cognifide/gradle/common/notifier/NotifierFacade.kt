package com.cognifide.gradle.common.notifier

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.build.BuildScope
import dorkbox.notify.Notify
import org.gradle.api.logging.LogLevel
import java.io.File

class NotifierFacade private constructor(val common: CommonExtension) {

    private val logger = common.logger

    private val prop = common.prop

    /**
     * Turn on/off default system notifications.
     */
    var enabled: Boolean = prop.flag("notifier.enabled")

    /**
     * Project specific image at path relative to root project.
     */
    var image: File = (prop.string("notifier.image") ?: "src/main/common/notifier/icon.png").let {
        common.project.rootProject.file(it)
    }

    /**
     * Hook for customizing notifications being displayed.
     *
     * To customize notification use one of concrete provider methods: 'dorkbox' or 'jcgay' (and optionally pass configuration lambda(s)).
     * Also it is possible to implement own notifier directly in build script by using provider method 'custom'.
     */
    var config: (NotifierFacade.() -> Notifier) = { dorkbox() }

    private val notifier: Notifier by lazy { config(this@NotifierFacade) }

    fun log(title: String) {
        log(title, "")
    }

    fun log(title: String, message: String) {
        log(title, message, LogLevel.INFO)
    }

    fun log(title: String, message: String, level: LogLevel) {
        common.logger.log(level, if (message.isNotBlank()) {
            "$title\n$message"
        } else {
            title
        })
    }

    fun notify(title: String) {
        notify(title, "")
    }

    @Suppress("TooGenericExceptionCaught")
    fun notify(title: String, text: String, level: LogLevel, onClick: (Notify) -> Unit = {}) {
        log(title, text, level)

        if (enabled) {
            try {
                notifier.notify(title, text, level, onClick)
            } catch (e: Exception) {
                logger.debug("AEM notifier is not available.", e)
            }
        }
    }

    fun notify(title: String, text: String) = lifecycle(title, text)

    fun dorkbox(configurer: Notify.() -> Unit = {}): Notifier {
        return DorkboxNotifier(this, configurer)
    }

    fun custom(notifier: (title: String, text: String, level: LogLevel, onClick: (Notify) -> Unit) -> Unit): Notifier {
        return object : Notifier {
            override fun notify(title: String, text: String, level: LogLevel, onClick: (Notify) -> Unit) {
                notifier(title, text, level, onClick)
            }
        }
    }

    fun lifecycle(title: String, text: String) = notify(title, text, LogLevel.LIFECYCLE)

    fun info(title: String, text: String) = notify(title, text, LogLevel.INFO)

    fun warn(title: String, text: String) = notify(title, text, LogLevel.WARN)

    fun error(title: String, text: String) = notify(title, text, LogLevel.ERROR)

    companion object {

        /**
         * Get project specific notifier (config can vary)
         */
        fun of(common: CommonExtension): NotifierFacade {
            return BuildScope.of(common.project).getOrPut(Notifier::class.java.canonicalName) { setup(common) }
        }

        /**
         * Register once (for root project only) listener for notifying about build errors (if any task executed).
         */
        private fun setup(common: CommonExtension): NotifierFacade {
            val notifier = NotifierFacade(common)
            if (common.project != common.project.rootProject) {
                return notifier
            }

            common.project.gradle.taskGraph.whenReady { graph ->
                if (graph.allTasks.isNotEmpty()) {
                    common.project.gradle.buildFinished { result ->
                        if (result.failure != null) {
                            val message = result.failure?.message ?: "no error message"

                            notifier.notify("Build failure", message, LogLevel.ERROR)
                        }
                    }
                }
            }

            return notifier
        }
    }
}
