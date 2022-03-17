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

        if (common.prop.boolean("common.path.check") ?: checkPathEnabled(project)) {
            checkPath(project)
        }
        if (common.prop.boolean("common.plugin.base") != false) {
            plugins.apply(BasePlugin::class.java)
        }
        if (common.prop.boolean("common.repository.mavenCentral") != false) {
            repositories.mavenCentral()
        }
    }

    /**
     * Detect project paths that may be a root cause of hard to investigate later problems.
     */
    private fun checkPath(project: Project) {
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

    /**
     * Exceptionally, disable path checking in some circumstances.
     * Some CI/CD systems like Jenkins may allow spaces in paths.
     */
    private fun checkPathEnabled(project: Project): Boolean {
        val envVars = listOf("JENKINS_HOME")
        if (envVars.any { (System.getenv(it) ?: "").isNotBlank() }) {
            return false
        }

        val projectPath = Formats.normalizePath(project.projectDir.absolutePath)
        val pathFragments = listOf("/jenkins/")
        if (pathFragments.any { projectPath.contains(it) }) {
            return false
        }

        return true
    }

    companion object {
        const val ID = "com.cognifide.common"
    }
}
