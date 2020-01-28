package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.utils.Patterns

abstract class ProtocolFileTransfer(common: CommonExtension) : AbstractFileTransfer(common) {

    abstract val protocols: List<String>

    override fun handles(fileUrl: String): Boolean {
        return !fileUrl.isBlank() && Patterns.wildcard(fileUrl, protocols)
    }
}
