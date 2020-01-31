package com.cognifide.gradle.common.build

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty

/**
 * Provides factory methods for property objects for most common cases.
 */
class ObjectFactory(private val project: Project) {

    private val layout = project.layout

    private val factory = project.objects

    // Defaults

    fun projectFile(path: String) = factory.fileProperty().convention(layout.projectDirectory.file(path))

    fun projectFileConvention(path: String) = factory.fileProperty().convention(layout.projectDirectory.file(path))

    fun projectDirConvention(path: String) = factory.directoryProperty().convention(layout.projectDirectory.dir(path))

    fun projectDir(path: String) = factory.directoryProperty().convention(layout.projectDirectory.dir(path))

    fun buildFile(path: String) = factory.fileProperty().convention(layout.buildDirectory.file(path))

    fun buildFileConvention(path: String) = factory.fileProperty().convention(layout.buildDirectory.file(path))

    fun buildDir(path: String) = factory.directoryProperty().convention(layout.buildDirectory.dir(path))

    fun buildDirConvention(path: String) = factory.directoryProperty().convention(layout.buildDirectory.dir(path))

    // Relatives

    fun relativeDir(dir: DirectoryProperty, relativePath: String) = project.objects.directoryProperty().set(dir.map { it.dir(relativePath) })

    fun relativeDir(dir: DirectoryProperty, relativePathProvider: () -> String) = project.objects.directoryProperty().set(dir.map { it.dir(relativePathProvider()) })

    fun relativeDirConvention(dir: DirectoryProperty, relativePath: String) = project.objects.directoryProperty().convention(dir.map { it.dir(relativePath) })

    fun relativeDirConvention(dir: DirectoryProperty, relativePathProvider: () -> String) = project.objects.directoryProperty().convention(dir.map { it.dir(relativePathProvider()) })

    fun relativeFile(dir: DirectoryProperty, relativePath: String) = project.objects.fileProperty().set(dir.map { it.file(relativePath) })

    fun relativeFile(dir: DirectoryProperty, relativePathProvider: () -> String) = project.objects.fileProperty().set(dir.map { it.file(relativePathProvider()) })

    fun relativeFileConvention(dir: DirectoryProperty, relativePath: String) = project.objects.fileProperty().convention(dir.map { it.file(relativePath) })

    fun relativeFileConvention(dir: DirectoryProperty, relativePathProvider: () -> String) = project.objects.fileProperty().convention(dir.map { it.file(relativePathProvider()) })
}