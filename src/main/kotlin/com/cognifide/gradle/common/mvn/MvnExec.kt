package com.cognifide.gradle.common.mvn

import com.cognifide.gradle.common.CommonDefaultTask
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

open class MvnExec : CommonDefaultTask() {

    @Nested
    val invoker = MvnInvoker(common)

    fun invoker(options: MvnInvoker.() -> Unit) {
        invoker.apply(options)
    }

    @TaskAction
    fun exec() {
        invoker.invoke()
    }
}
