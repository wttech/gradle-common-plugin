package com.cognifide.gradle.common.build

import com.cognifide.gradle.common.CommonException
import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.utils.Formats
import kotlin.math.max

class Retry(val common: CommonExtension) {

    var times = 1L

    var delay: (Long) -> Long = { 0L }

    fun never() {
        times = 1L
        delay = { 0L }
    }

    fun after(times: Long, delay: (Long) -> Long) {
        this.delay = delay
        this.times = max(1, times)
    }

    fun after(times: Long, delay: Long) = after(times) { delay }

    fun afterSecond(times: Long) = after(times) { SECOND_MILLIS }

    fun afterSquaredSecond(times: Long) = after(times) { n -> n * n * SECOND_MILLIS }

    @Suppress("TooGenericExceptionCaught")
    inline fun <T, reified E> withCountdown(operation: String, block: (Long) -> T): T {
        lateinit var exception: Exception
        for (i in 0 until times) {
            val no = i + 1

            try {
                return block(no)
            } catch (e: Exception) {
                exception = e

                if (e !is E) {
                    throw exception
                }

                if (i < times) {
                    val delay = delay(no)

                    common.logger.lifecycle("Retrying ($no/$times) $operation after delay: ${Formats.duration(delay)}")
                    common.logger.debug("Retrying due to exception", e)

                    common.progressCountdown {
                        time = delay
                        timePrefix = "Retrying ($no/$times) $operation after"
                    }
                }
            }
        }

        throw exception
    }

    fun withCountdown(operation: String, block: (Long) -> Unit) = withCountdown<Unit, CommonException>(operation, block)

    @Suppress("TooGenericExceptionCaught")
    inline fun <T, reified E> withSleep(block: (Long) -> T): T {
        lateinit var exception: Exception
        for (i in 0 until times) {
            val no = i + 1
            try {
                return block(no)
            } catch (e: Exception) {
                exception = e

                if (e !is E) {
                    throw exception
                }

                if (i < times) {
                    val delay = delay(no)
                    Thread.sleep(delay)
                }
            }
        }

        throw exception
    }

    fun withSleep(block: (Long) -> Unit) = withSleep<Unit, CommonException>(block)

    fun withSleepTillEnd(block: (Long) -> Unit) {
        for (i in 0 until times) {
            val no = i + 1
            block(no)
            if (i < times) {
                val delay = delay(no)
                Thread.sleep(delay)
            }
        }
    }

    companion object {
        const val SECOND_MILLIS = 1000L
    }
}
