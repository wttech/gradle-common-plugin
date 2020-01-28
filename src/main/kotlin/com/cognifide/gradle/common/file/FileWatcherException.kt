package com.cognifide.gradle.common.file

import com.cognifide.gradle.common.CommonException

class FileWatcherException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
