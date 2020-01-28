package com.cognifide.gradle.common.file

import com.cognifide.gradle.common.CommonException

open class FileException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
