package com.cognifide.gradle.common.build

import com.cognifide.gradle.common.utils.Patterns
import org.gradle.api.Project
import java.io.File

object DependencyOptions {

    private val NOTATION_PATTERNS = listOf(
            "*:*:*@*", // TODO regex [\w-_.]+
            "*:*:*"
    )

    fun isNotation(text: String) = Patterns.wildcard(text, NOTATION_PATTERNS)

    fun resolve(project: Project, notation: Any): File {
        val dependency = project.dependencies.create(notation)
        val config = project.configurations.detachedConfiguration(dependency).apply {
            isTransitive = false
        }

        return config.singleFile
    }
}
