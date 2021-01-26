package com.cognifide.gradle.common.java

import com.cognifide.gradle.common.CommonException

open class JavaException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
