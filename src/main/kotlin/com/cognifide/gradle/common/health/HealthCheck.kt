package com.cognifide.gradle.common.health

class HealthCheck(val name: String, val action: () -> Any?) {

    private var result: Any? = null

    @Suppress("TooGenericExceptionCaught")
    fun perform(): HealthStatus = try {
        HealthStatus(this, true, action())
    } catch (e: Exception) {
        HealthStatus(this, false, e.message)
    }

    override fun toString(): String = name
}
