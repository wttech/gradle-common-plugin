package com.cognifide.gradle.common.file.resolver

import com.cognifide.gradle.common.CommonExtension

open class FileResolver(common: CommonExtension) : Resolver<FileGroup>(common) {

    override fun createGroup(name: String): FileGroup {
        return FileGroup(this, name)
    }
}
