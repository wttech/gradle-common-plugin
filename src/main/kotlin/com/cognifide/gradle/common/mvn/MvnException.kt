package com.cognifide.gradle.common.mvn

import com.cognifide.gradle.common.CommonException

class MvnException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
