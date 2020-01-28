package com.cognifide.gradle.common.build

import com.cognifide.gradle.common.CommonException

open class PropertyException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
