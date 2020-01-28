package com.cognifide.gradle.common.http

import com.cognifide.gradle.common.CommonException

open class HttpException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
