package com.cognifide.gradle.common

import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.Internal

interface CommonTask : Task {

    @get:Internal
    val common: CommonExtension

    fun projectEvaluated() {
        // intentionally empty
    }

    fun projectsEvaluated() {
        // intentionally empty
    }

    fun taskGraphReady(graph: TaskExecutionGraph) {
        // intentionally empty
    }
}
