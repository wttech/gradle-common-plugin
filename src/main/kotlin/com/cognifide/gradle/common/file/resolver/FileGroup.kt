package com.cognifide.gradle.common.file.resolver

import java.io.File

open class FileGroup(val resolver: Resolver<FileGroup>, val name: String) {

    private val _resolutions = mutableListOf<FileResolution>()

    internal var parallelable = true

    val resolutions: List<FileResolution> get() = _resolutions.toList()

    val files: List<File> get() = _resolutions.map { it.file }

    val dirs: List<File> get() = _resolutions.map { it.dir }

    protected open fun createResolution(id: String, resolver: (FileResolution) -> File) = FileResolution(this, id, resolver)

    fun resolve(id: String, resolver: (FileResolution) -> File): FileResolution = createResolution(id, resolver).apply { _resolutions += this }

    fun resolve() = files
}
