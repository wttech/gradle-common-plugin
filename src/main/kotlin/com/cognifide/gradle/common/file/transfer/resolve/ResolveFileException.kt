package com.cognifide.gradle.common.file.transfer.resolve

import com.cognifide.gradle.common.file.FileException

class ResolveFileException : FileException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
