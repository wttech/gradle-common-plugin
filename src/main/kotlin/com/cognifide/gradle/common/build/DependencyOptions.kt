package com.cognifide.gradle.common.build

import com.cognifide.gradle.common.CommonException
import com.cognifide.gradle.common.utils.Patterns
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Project
import org.gradle.internal.typeconversion.UnsupportedNotationException
import java.io.File

object DependencyOptions {

    private val NOTATION_PATTERNS = listOf(
            "*:*:*@*", // TODO regex [\w-_.]+
            "*:*:*"
    )

    fun isNotation(text: String) = Patterns.wildcard(text, NOTATION_PATTERNS)

    fun hintNotation(notation: Any, extensionHint: String): Any = when (notation) {
        is String -> StringUtils.appendIfMissing(notation, "@$extensionHint")
        else -> notation
    }

    fun resolveFile(project: Project, notation: Any): File {
        val dependency = try {
            project.dependencies.create(notation)
        } catch (e: UnsupportedNotationException) {
            throw CommonException("Cannot resolve file using dependency notation: $notation", e)
        }
        val config = project.configurations.detachedConfiguration(dependency).apply {
            isTransitive = false
        }

        return config.singleFile
    }

    fun determineFileName(project: Project, notation: Any): String {
        return "group-name-version.zip" // TODO
    }
}
