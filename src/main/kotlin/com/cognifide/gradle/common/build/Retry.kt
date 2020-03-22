package com.cognifide.gradle.common.build

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.utils.Formats

class Retry(val common: CommonExtension) {

    var times = 0L

    var delay: (Long) -> Long = { 0L }

    fun never() {
        times = 0L
        delay = { 0L }
    }

    fun after(times: Long, delay: (Long) -> Long) {
        this.delay = delay
        this.times = times
    }

    fun afterSecond(times: Long) {
        after(times) { SECOND_MILLIS }
    }

    fun afterSquaredSecond(times: Long) {
        after(times) { n -> n * n * SECOND_MILLIS }
    }

    @Suppress("TooGenericExceptionCaught")
    inline fun <T, reified E> withCountdown(operation: String, block: (Long) -> T): T {
        lateinit var exception: Exception
        for (i in 0..times) {
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

    @Suppress("TooGenericExceptionCaught")
    inline fun <T, reified E> withSleep(block: (Long) -> T): T {
        lateinit var exception: Exception
        for (i in 0..times) {
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

    companion object {
        fun none(common: CommonExtension) = Retry(common)

        const val SECOND_MILLIS = 1000L
    }
}
