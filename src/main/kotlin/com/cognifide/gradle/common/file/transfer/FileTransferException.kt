package com.cognifide.gradle.common.file.transfer

import com.cognifide.gradle.common.CommonException

open class FileTransferException : CommonException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
