package com.cognifide.gradle.common.build

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * Provides factory methods for property objects for most common cases.
 */
class ObjectFactory(private val project: Project) {

    val layout = project.layout

    val factory = project.objects

    fun <T> provider(value: () -> T) = project.provider(value)

    // Generics

    inline fun <reified T> typed(options: Property<T>.() -> Unit = {}) = factory.property(T::class.java).apply(options)

    inline fun <reified T> list(options: ListProperty<T>.() -> Unit = {}) = factory.listProperty(T::class.java).apply(options)

    inline fun <reified K, reified V> map(options: MapProperty<K, V>.() -> Unit = {}) = factory.mapProperty(K::class.java, V::class.java).apply(options)

    // Base types

    fun file(options: RegularFileProperty.() -> Unit = {}) = factory.fileProperty().apply(options)

    fun files(options: ConfigurableFileCollection.() -> Unit = {}) = factory.fileCollection().apply(options)

    fun dir(options: DirectoryProperty.() -> Unit = {}) = factory.directoryProperty().apply(options)

    fun string(options: Property<String>.() -> Unit = {}) = factory.property(String::class.java).apply(options)

    fun strings(options: ListProperty<String>.() -> Unit = {}) = factory.listProperty(String::class.java).apply(options)

    fun int(options: Property<Int>.() -> Unit = {}) = factory.property(Int::class.java).apply(options)

    fun long(options: Property<Long>.() -> Unit = {}) = factory.property(Long::class.java).apply(options)

    fun boolean(options: Property<Boolean>.() -> Unit = {}) = factory.property(Boolean::class.java).apply(options)

    // Shorthand conventions

    fun projectFile(path: String) = file().convention(layout.projectDirectory.file(path))

    fun projectDir(path: String) = dir().convention(layout.projectDirectory.dir(path))

    fun buildFile(path: String) = file().convention(layout.buildDirectory.file(path))

    fun buildDir(path: String) = dir().convention(layout.buildDirectory.dir(path))

    fun relativeDir(dir: DirectoryProperty, path: String) = dir().convention(dir.map { it.dir(path) })

    fun relativeFile(dir: DirectoryProperty, path: String) = file().convention(dir.map { it.file(path) })
}
