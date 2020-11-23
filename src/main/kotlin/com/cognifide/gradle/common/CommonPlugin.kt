package com.cognifide.gradle.common

import com.cognifide.gradle.common.utils.using
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin

class CommonPlugin : Plugin<Project> {

    override fun apply(project: Project) = project.using {
        if (common.prop.boolean("common.plugin.base") != false) {
            plugins.apply(BasePlugin::class.java)
        }
        val common = extensions.create(CommonExtension.NAME, CommonExtension::class.java, this)
        if (common.prop.boolean("common.repository.jcenter") != false) {
            repositories.jcenter()
        }
    }

    companion object {
        const val ID = "com.cognifide.common"
    }
}
