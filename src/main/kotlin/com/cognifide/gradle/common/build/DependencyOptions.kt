package com.cognifide.gradle.common.build

import com.cognifide.gradle.common.utils.Patterns
import com.cognifide.gradle.common.utils.Utils
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import java.io.File

/**
 * Based on: org.gradle.kotlin.dsl.accessors.runtime
 */
@Suppress("LongParameterList")
class DependencyOptions {

    lateinit var group: String

    lateinit var name: String

    var version: String? = null

    var configuration: String? = null

    var classifier: String? = null

    var ext: String? = null

    fun dependency(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null
    ) {
        this.group = group
        this.name = name
        this.version = version
        this.configuration = configuration
        this.classifier = classifier
        this.ext = ext
    }

    val notation: String get() = listOfNotNull(group, name, version, classifier)
            .joinToString(":") + classifier?.run { "@$this" }.orEmpty()

    companion object {

        private val NOTATION_PATTERNS = listOf(
                "*:*:*@*", // TODO regex [\w-_.]+
                "*:*:*"
        )

        fun isNotation(text: String) = Patterns.wildcard(text, NOTATION_PATTERNS)

        fun create(project: Project, notation: Any): Dependency = project.dependencies.create(notation)

        fun create(project: Project, options: DependencyOptions.() -> Unit): Dependency {
            return DependencyOptions().apply(options).run {
                project.dependencies.create(Utils.mapOfNonNullValues(
                        "group" to group,
                        "name" to name,
                        "version" to version,
                        "configuration" to configuration,
                        "classifier" to classifier,
                        "ext" to ext
                ))
            }
        }

        fun add(project: Project, configuration: String, options: DependencyOptions.() -> Unit) {
            project.dependencies.add(configuration, create(project, options))
        }

        fun add(project: Project, configuration: String, notation: Any) {
            project.dependencies.add(configuration, notation)
        }

        fun resolve(project: Project, notation: Any): File {
            val dependency = project.dependencies.create(notation)
            val config = project.configurations.detachedConfiguration(dependency).apply {
                isTransitive = false
            }

            return config.singleFile
        }
    }
}
