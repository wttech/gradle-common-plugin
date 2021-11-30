package com.cognifide.gradle.common

import com.cognifide.gradle.common.file.FileUtil
import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.common.utils.using
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin

class CommonPlugin : Plugin<Project> {

    override fun apply(project: Project) = project.using {
        val common = extensions.create(CommonExtension.NAME, CommonExtension::class.java, this)

        if (common.prop.boolean("common.path.check") ?: pathCheckDefault(project)) {
            val rootPath = FileUtil.systemPath(project.projectDir.absolutePath)
            val sanitizedPath = FileUtil.systemPath(FileUtil.sanitizePath(rootPath))
            if (sanitizedPath != rootPath) {
                throw CommonException(
                    "Project path contains problematic characters!\n" +
                            "Shell scripts could run improperly with such paths.\n" +
                            "Consider relocating project - updating the path from '$rootPath' to '$sanitizedPath'."
                )
            }
        }

        if (common.prop.boolean("common.plugin.base") != false) {
            plugins.apply(BasePlugin::class.java)
        }
        if (common.prop.boolean("common.repository.mavenCentral") != false) {
            repositories.mavenCentral()
        }
    }

    /**
     * Exceptionally, disable path checking in some circumstances.
     * Some CI/CD systems like Jenkins may allow spaces in paths.
     */
    private fun pathCheckDefault(project: Project): Boolean {
        val normalizedPath = Formats.normalizePath(project.projectDir.absolutePath)
        val excludedPathFragments = listOf("/jenkins/")
        return excludedPathFragments.none { normalizedPath.contains(it) }
    }

    companion object {
        const val ID = "com.cognifide.common"
    }
}
