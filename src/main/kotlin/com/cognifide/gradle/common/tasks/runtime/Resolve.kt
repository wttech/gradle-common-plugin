package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

open class Resolve : RuntimeDefaultTask() {

    init {
        description = "Resolves all resources needed by all runtimes."
    }

    companion object {
        const val NAME = "resolve"
    }
}
