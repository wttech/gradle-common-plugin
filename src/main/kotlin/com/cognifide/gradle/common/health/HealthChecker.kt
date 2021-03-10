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

    var retry = common.retry {
        after(
            prop.long("healthChecker.retry.times") ?: 60,
            prop.long("healthChecker.retry.delay") ?: 5_000L,
        )
    }

    var assuranceRetry = common.retry {
        after(
            prop.long("healthChecker.assuranceRetry.times") ?: 2,
            prop.long("healthChecker.assuranceRetry.delay") ?: 1_000L,
        )
    }

    val waitBefore = common.obj.long {
        convention(TimeUnit.SECONDS.toMillis(0))
        common.prop.long("healthChecker.wait.before")?.let { set(it) }
    }

    val waitAfter = common.obj.long {
        convention(TimeUnit.SECONDS.toMillis(0))
        common.prop.long("healthChecker.wait.after")?.let { set(it) }
    }

    fun wait(before: Long, after: Long) {
        waitBefore.set(before)
        waitAfter.set(after)
    }

    fun check(name: String, check: () -> Any?) {
        checks += HealthCheck(name, check)
    }

    fun String.invoke(check: () -> Any?) = check(this, check)

    // Evaluation

    var all = listOf<HealthStatus>()

    val allStatuses get() = all.sortedWith(compareBy({ it.succeed }, { it.check.name })).joinToString("\n")

    var passed = listOf<HealthStatus>()

    val passedRatio get() = "${passed.size}/${all.size} (${Formats.percent(passed.size, all.size)})"

    var failed = listOf<HealthStatus>()

    @Suppress("ComplexMethod")
    fun start(verbose: Boolean = this.verbose.get(), retry: Retry = this.retry): List<HealthStatus> {
        common.progress(checks.size) {
            step = "Health checking"

            message = "Wait Before"
            if (waitBefore.get() > 0) {
                common.progressCountdown(waitBefore.get())
            }

            try {
                assuranceRetry.withSleepTillEnd { no ->
                    start(retry, verbose)
                    increment()
                    logger.info("Health checking passed ($no/${assuranceRetry.times})")
                }
            } catch (e: HealthException) {
                val message = "Health checking failed. Success ratio: $passedRatio:\n$allStatuses"
                when {
                    verbose -> throw HealthException(message)
                    else -> logger.error(message)
                }
            }

            message = "Wait After"
            if (waitAfter.get() > 0) {
                common.progressCountdown(waitAfter.get())
            }

            logger.lifecycle("Health checking succeed.\n$allStatuses")
        }

        return all
    }

    private fun ProgressIndicator.start(retry: Retry, verbose: Boolean) = retry.withSleep<Unit, HealthException> { no ->
        message = when {
            failed.isNotEmpty() -> "Attempt $no/${retry.times}, ${failed.size} failed"
            else -> "Attempt $no/${retry.times}"
        }

        all = common.parallel.map(checks) { check ->
            check.perform()
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
