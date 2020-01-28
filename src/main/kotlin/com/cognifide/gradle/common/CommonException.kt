package com.cognifide.gradle.common

import org.gradle.api.GradleException

open class CommonException : GradleException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
