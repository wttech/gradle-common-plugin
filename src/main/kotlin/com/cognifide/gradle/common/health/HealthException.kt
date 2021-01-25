package com.cognifide.gradle.common.health

import com.cognifide.gradle.common.CommonException

open class HealthException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
