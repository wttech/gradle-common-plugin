package com.cognifide.gradle.common.health

class HealthCheck(val name: String, val action: () -> Any?) {

    @Suppress("TooGenericExceptionCaught")
    fun perform(): HealthStatus = try {
        HealthStatus(this, true, action().takeIf { it != Unit })
    } catch (e: Exception) {
        HealthStatus(this, false, e.message)
    }

    override fun toString(): String = name
}
