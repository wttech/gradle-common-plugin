package com.cognifide.gradle.common.health

class HealthStatus(val check: HealthCheck, val succeed: Boolean, val details: Any? = null) {

    val indicator get() = if (succeed) "+" else "-"

    val message get() = listOfNotNull(check.name, details).joinToString(" | ").trim()

    val status: String get() = "[$indicator] $message"

    override fun toString() = status
}
