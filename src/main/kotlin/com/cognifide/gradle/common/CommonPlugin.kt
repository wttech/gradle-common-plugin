package com.cognifide.gradle.common

import com.cognifide.gradle.common.utils.using
import org.gradle.api.Plugin
import org.gradle.api.Project

class CommonPlugin : Plugin<Project> {

    override fun apply(project: Project) = using(project) {
        registerExtension()
    }

    private fun Project.registerExtension() {
        extensions.create(CommonExtension.NAME, CommonExtension::class.java, this)
    }

    companion object {
        const val ID = "com.cognifide.aem.common"
    }
}