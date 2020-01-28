package com.cognifide.gradle.common

import com.cognifide.gradle.common.build.PropertyParser
import org.gradle.api.DefaultTask
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.Internal

open class CommonDefaultTask : DefaultTask(), CommonTask {

    @Internal
    final override val common = project.common

    private var doProjectEvaluated: () -> Unit = {}

    private var doProjectsEvaluated: () -> Unit = {}

    private var doTaskGraphReady: (TaskExecutionGraph) -> Unit = {}

    override fun projectEvaluated() {
        doProjectEvaluated()
    }

    fun projectEvaluated(callback: () -> Unit) {
        this.doProjectEvaluated = callback
    }

    override fun projectsEvaluated() {
        doProjectsEvaluated()
    }

    fun projectsEvaluated(callback: () -> Unit) {
        this.doProjectsEvaluated = callback
    }

    override fun taskGraphReady(graph: TaskExecutionGraph) {
        doTaskGraphReady(graph)
    }

    fun taskGraphReady(callback: (TaskExecutionGraph) -> Unit) {
        this.doTaskGraphReady = callback
    }

    fun checkForce() {
        taskGraphReady { graph ->
            if (graph.hasTask(this)) {
                if (!common.prop.force) {
                    throw CommonException("Unable to run unsafe task '$path' without param '-P${PropertyParser.FORCE_PROP}'!")
                }
            }
        }
    }
}
