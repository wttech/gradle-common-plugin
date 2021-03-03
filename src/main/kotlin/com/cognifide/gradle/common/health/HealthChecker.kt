package com.cognifide.gradle.common.health

import com.cognifide.gradle.common.CommonException
import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.build.ProgressIndicator
import com.cognifide.gradle.common.build.Retry
import com.cognifide.gradle.common.http.HttpClient
import com.cognifide.gradle.common.net.NetUtils
import com.cognifide.gradle.common.utils.Formats
import org.apache.http.HttpStatus
import java.util.concurrent.TimeUnit
import kotlin.math.min

class HealthChecker(val common: CommonExtension) {

    private val logger = common.logger

    private val prop = common.prop

    private val checks = mutableListOf<HealthCheck>()

    private var httpOptions: HttpClient.() -> Unit = {
        authorizationPreemptive.set(true)
        connectionRetries.apply {
            convention(false)
            prop.boolean("healthChecker.http.connectionRetries")?.let { set(it) }
        }
        connectionTimeout.apply {
            convention(5_000)
            prop.int("healthChecker.http.connectionTimeout")?.let { set(it) }
        }
    }

    val verbose = common.obj.boolean {
        convention(true)
        common.prop.boolean("healthChecker.verbose")?.let { set(it) }
    }

    var retry = common.retry { afterSquaredSecond(prop.long("healthChecker.retry") ?: 10) }

    val waitBefore = common.obj.long {
        convention(TimeUnit.SECONDS.toMillis(0))
        common.prop.long("healthChecker.waitBefore")?.let { set(it) }
    }

    val waitAfter = common.obj.long {
        convention(TimeUnit.SECONDS.toMillis(0))
        common.prop.long("healthChecker.waitAfter")?.let { set(it) }
    }

    fun wait(before: Long, after: Long) {
        waitBefore.set(before)
        waitAfter.set(after)
    }

    var assuranceRetry = common.retry {
        afterSecond(prop.long("healthChecker.assuranceRetry") ?: 3)
    }

    fun check(name: String, check: () -> Any?) {
        checks += HealthCheck(name, check)
    }

    fun String.invoke(check: () -> Any?) = check(this, check)

    // Evaluation

    var all = listOf<HealthStatus>()

    var passed = listOf<HealthStatus>()

    var failed = listOf<HealthStatus>()

    val count get() = "${passed.size}/${all.size} (${Formats.percent(passed.size, all.size)})"

    @Suppress("ComplexMethod")
    fun start(verbose: Boolean = this.verbose.get(), retry: Retry = this.retry): List<HealthStatus> {
        common.progress(checks.size) {
            step = "Health checking"
            if (waitBefore.get() > 0) {
                common.progressCountdown(waitBefore.get())
            }

            try {
                assuranceRetry.withSleepTillEnd { no ->
                    step = "Health checking ($no/${assuranceRetry.times})"
                    start(retry, verbose)
                }
            } catch (e: HealthException) {
                val message = listOf(
                    "Health checking failed. Success ratio: $count:",
                    all.sortedWith(compareBy({ it.succeed }, { it.check.name })).joinToString("\n")
                ).joinToString("\n")
                when {
                    verbose -> throw HealthException(message)
                    else -> logger.error(message)
                }
            }

            step = "Health checking"
            if (waitAfter.get() > 0) {
                common.progressCountdown(waitAfter.get())
            }

            val message = "Health checking succeed.\n" +
                    all.sortedWith(compareBy({ it.succeed }, { it.check.name })).joinToString("\n")
            logger.lifecycle(message)
        }

        return all
    }

    private fun ProgressIndicator.start(retry: Retry, verbose: Boolean) = retry.withSleep<Unit, HealthException> { no ->
        reset()

        message = when {
            failed.isNotEmpty() -> "Attempt $no/${retry.times}, ${failed.size} failed"
            else -> "Attempt $no/${retry.times}"
        }

        all = common.parallel.map(checks) { check ->
            increment(check.name) {
                check.perform()
            }
        }.toList()
        passed = all.filter { it.succeed }
        failed = all - passed

        if (verbose) {
            logger.info(failed.sortedBy { it.check.name }.joinToString("\n"))
        }

        if (failed.isNotEmpty()) {
            throw HealthException("Health checks failed. Retrying...")
        }
    }

    // Shorthand methods for defining health checks

    /**
     * Check URL using specified criteria (HTTP options and e.g text & status code assertions).
     */
    fun http(checkName: String, url: String, statusCode: Int = HttpStatus.SC_OK) {
        http(checkName, url) { respondsWith(statusCode) }
    }

    fun http(checkName: String, url: String, containedText: String, statusCode: Int = HttpStatus.SC_OK) {
        http(checkName, url) { containsText(containedText, statusCode) }
    }

    fun http(checkName: String, url: String, criteria: HttpCheck.() -> Unit) = check(checkName) {
        var result: Any? = null
        common.http {
            val check = HttpCheck(url).apply(criteria)
            apply(httpOptions)
            apply(check.options)
            request(check.method, check.url) { response ->
                result = "${check.method} ${check.url} -> ${response.statusLine}"
                check.checks.forEach { it(response) }
            }
        }
        result
    }

    fun noHttp(checkName: String, url: String, criteria: HttpCheck.() -> Unit = {}) = check(checkName) {
        common.http {
            val check = HttpCheck(url).apply(criteria)
            apply(httpOptions)
            apply(check.options)
            var responds = false
            try {
                request(check.method, check.url) {
                    responds = true
                }
            } catch (e: CommonException) {
                // ignore known errors
            }
            if (responds) {
                throw IllegalStateException("HTTP ${check.method.toUpperCase()} '${check.url}' is available")
            }
        }
    }

    fun host(checkName: String, hostName: String, port: Int, timeout: Int = 1000) = check(checkName) {
        if (!NetUtils.isHostReachable(hostName, port, timeout)) {
            throw IllegalStateException("Host '$hostName' at port $port is not reachable")
        }
    }

    fun noHost(checkName: String, hostName: String, port: Int, timeout: Int = 1000) = check(checkName) {
        if (NetUtils.isHostReachable(hostName, port, timeout)) {
            throw IllegalStateException("Host '$hostName' at port $port is reachable")
        }
    }

    // Default options

    fun httpOptions(options: HttpClient.() -> Unit) {
        this.httpOptions = options
    }
}
