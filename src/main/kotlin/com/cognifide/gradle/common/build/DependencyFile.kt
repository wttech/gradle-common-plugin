package com.cognifide.gradle.common.build

import com.cognifide.gradle.common.CommonException
import com.cognifide.gradle.common.utils.Patterns
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.internal.typeconversion.UnsupportedNotationException
import java.io.File

class DependencyFile(private val project: Project, notation: Any) {

    val dependency: Dependency by lazy {
        try {
            project.dependencies.create(notation)
        } catch (e: UnsupportedNotationException) {
            throw CommonException("Cannot resolve file using dependency notation: $notation", e)
        }
    }

    val file: File by lazy {
        project.configurations.detachedConfiguration(dependency).run {
            isTransitive = false
            singleFile
        }
    }

    companion object {

        private val NOTATION_PATTERNS = listOf(
                "*:*:*@*", // TODO regex [\w-_.]+
                "*:*:*"
        )

        fun isNotation(text: String) = Patterns.wildcard(text, NOTATION_PATTERNS)

        fun hintNotation(notation: Any, extensionHint: String): Any = when {
            notation is String && isNotation(notation) -> "${notation.substringBeforeLast("@")}@$extensionHint"
            else -> notation
        }
    }
}
