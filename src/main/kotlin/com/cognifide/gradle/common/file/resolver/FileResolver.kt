package com.cognifide.gradle.common.file.resolver

import com.cognifide.gradle.common.CommonExtension
import java.io.File

open class FileResolver(common: CommonExtension, downloadDir: File) : Resolver<FileGroup>(common, downloadDir) {

    override fun createGroup(name: String): FileGroup {
        return FileGroup(common, downloadDir, name)
    }
}
