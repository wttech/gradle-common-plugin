package com.cognifide.gradle.common.http

class ResponseException : HttpException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
