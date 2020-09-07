package com.cognifide.gradle.common.zip

import com.cognifide.gradle.common.CommonException

class ZipException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
