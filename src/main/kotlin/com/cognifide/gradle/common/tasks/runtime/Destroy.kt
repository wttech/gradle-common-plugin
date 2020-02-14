package com.cognifide.gradle.common.tasks.runtime

import com.cognifide.gradle.common.RuntimeDefaultTask

open class Destroy : RuntimeDefaultTask() {

    init {
        description = "Destroys all runtimes."
    }

    companion object {
        const val NAME = "destroy"
    }
}
