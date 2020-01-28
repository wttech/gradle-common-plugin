package com.cognifide.gradle.common

import org.gradle.api.Plugin
import org.gradle.api.Project

class CommonPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            setupExtensions()
        }
    }

    private fun Project.setupExtensions() {
        extensions.create(CommonExtension.NAME, CommonExtension::class.java, this)
    }

    companion object {
        const val ID = "com.cognifide.aem.common"
    }
}
