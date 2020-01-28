package com.cognifide.gradle.common

import com.cognifide.gradle.common.tasks.TaskFacade
import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class CommonDefaultPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply(CommonPlugin::class.java)
        project.run { configureProject() }
    }

    abstract fun Project.configureProject()

    fun Project.tasks(configurer: TaskFacade.() -> Unit) {
        common.tasks.apply(configurer)
    }
}
