package com.cognifide.gradle.common.build

import com.cognifide.gradle.common.utils.Formats
import com.mitchellbosecke.pebble.PebbleEngine
import com.mitchellbosecke.pebble.lexer.Syntax
import com.mitchellbosecke.pebble.loader.StringLoader
import org.gradle.api.Project
import java.io.File
import java.io.IOException
import java.io.StringWriter

class PropertyParser(anyProject: Project) {

    private val project = anyProject.rootProject

    private fun find(name: String): String? {
        if (project.hasProperty(name)) {
            return project.property(name).toString()
        }

        val systemValue = System.getProperty(name)
        if (systemValue != null) {
            return systemValue
        }

        val envValue = System.getenv(name)
        if (envValue != null) {
            return envValue
        }

        return null
    }

    private fun prop(name: String): String? = find(name)?.ifBlank { null }

    fun flag(vararg names: String) = names.any { flag(it) }

    fun flag(name: String): Boolean {
        val value = find(name) ?: return false
        return if (!value.isBlank()) value.toBoolean() else true
    }

    fun list(name: String, delimiter: String = ","): List<String>? = when (val value = prop(name)) {
        null -> group(name)?.values?.toList()
        EMPTY_LIST -> listOf()
        else -> Formats.toList(value, delimiter)
    }

    fun map(name: String, valueDelimiter: String = ",", keyDelimiter: String = "=") = when (val value = prop(name)) {
        null -> group(name)
        EMPTY_MAP -> mapOf()
        else -> Formats.toMap(value, valueDelimiter, keyDelimiter)
    }

    fun group(name: String): Map<String, String>? = project.properties
            .filterKeys { it.startsWith("$name.") }
            .map { it.key.removePrefix("$name.") to (it.value ?: "").toString() }
            .sortedBy { it.first }
            .toMap()
            .ifEmpty { null }

    fun boolean(name: String) = prop(name)?.toBoolean()

    fun long(name: String) = prop(name)?.toLong()

    fun int(name: String) = prop(name)?.toInt()

    fun string(name: String) = prop(name)

    fun file(name: String, projectRelative: Project): File? = string(name)?.let { projectRelative.file(it) }

    fun file(name: String, projectRootFallback: Boolean = true): File? = string(name)?.let {
        project.file(it) ?: if (projectRootFallback) project.rootProject.file(it) else null
    }

    fun expand(file: File, props: Map<String, Any?>) = file.writeText(expand(file.readText(), props, file.toString()))

    fun expand(source: String, props: Map<String, Any?>, context: String? = null): String {
        try {
            val expanded = StringWriter()
            TEMPLATE_ENGINE.getTemplate(source).evaluate(expanded, expandProps + props)
            return expanded.toString()
        } catch (e: IOException) {
            var msg = "Cannot expand properly all properties. Probably used non-existing field name or unescaped char detected. Source: '${source.trim()}'."
            if (!context.isNullOrBlank()) msg += " Context: $context"
            throw PropertyException(msg, e)
        }
    }

    private val expandProps: Map<String, Any> by lazy {
        mapOf(
                "rootProject" to project,
                "system" to System.getProperties().entries.fold(mutableMapOf<String, Any>()) { props, prop ->
                    props[prop.key.toString()] = prop.value.toString(); props
                },
                "env" to System.getenv()
        )
    }

    val force: Boolean get() = flag(FORCE_PROP)

    companion object {

        const val FORCE_PROP = "force"

        const val EMPTY_MAP = "[]"

        const val EMPTY_LIST = "[]"

        private const val TEMPLATE_VAR_PREFIX = "{{"

        private const val TEMPLATE_VAR_SUFFIX = "}}"

        private val TEMPLATE_ENGINE = PebbleEngine.Builder()
                .autoEscaping(false)
                .cacheActive(false)
                .strictVariables(true)
                .newLineTrimming(false)
                .loader(StringLoader())
                .syntax(Syntax.Builder()
                        .setEnableNewLineTrimming(false)
                        .setPrintOpenDelimiter(TEMPLATE_VAR_PREFIX)
                        .setPrintCloseDelimiter(TEMPLATE_VAR_SUFFIX)
                        .build()
                )
                .build()
    }
}
