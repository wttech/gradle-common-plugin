package com.cognifide.gradle.common.build

import java.util.*
import java.util.concurrent.TimeUnit
import org.gradle.api.Project
import org.gradle.internal.logging.progress.ProgressLogger as BaseLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory

@Suppress("SpreadOperator")
open class ProgressLogger private constructor(private val project: Project) {

    var header: String = "Operation in progress"

    var progressEach: (String) -> Unit = { message -> project.logger.debug("$header: $message") }

    var progressWindow = TimeUnit.SECONDS.toMillis(1)

    private val baseParents: Queue<BaseLogger>
        get() = parents(project)

    private lateinit var base: BaseLogger

    private var progressTime = 0L

    private fun create(): BaseLogger = ServiceAccessor(project)
            .get<ProgressLoggerFactory>()
            .newOperation(javaClass, baseParents.peek())

    fun <T> launch(block: ProgressLogger.() -> T): T {
        base = create()
        baseParents.add(base)

        try {
            progressTime = System.currentTimeMillis()
            base.description = header
            base.started()

            return run(block)
        } finally {
            base.completed()
            baseParents.remove(base)
            progressTime = 0L
        }
    }

    fun progress(message: String) {
        base.progress(message)

        val progressCurrentTime = System.currentTimeMillis()
        if (progressTime > 0 && (progressCurrentTime - progressTime) >= progressWindow) {
            progressTime = progressCurrentTime
            progressEach(message)
        }
    }

    companion object {
        fun of(project: Project): ProgressLogger {
            return ProgressLogger(project)
        }

        fun parents(project: Project): Queue<BaseLogger> {
            return BuildScope.of(project).getOrPut("${ProgressLogger::class.java.canonicalName}_${project.path}") {
                LinkedList()
            }
        }
    }
}
