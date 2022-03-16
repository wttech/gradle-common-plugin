package com.cognifide.gradle.common.build

import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.*

object Parallel {

    fun <A, B : Any> map(iterable: Iterable<A>, mapper: CoroutineScope.(A) -> B): Collection<B> {
        return map(iterable, { true }, mapper)
    }

    fun <A, B : Any> map(iterable: Iterable<A>, filter: (A) -> Boolean, mapper: CoroutineScope.(A) -> B): List<B> {
        return map(Dispatchers.IO, iterable) {
            if (filter(it)) { mapper(it) } else { null }
        }
    }

    private fun <A, B : Any> map(context: CoroutineContext, iterable: Iterable<A>, mapper: CoroutineScope.(A) -> B?): List<B> {
        return runBlocking(context) {
            iterable.map { value -> async { if (value != null) mapper(value) else null } }.mapNotNull { it.await() }
        }
    }

    fun <A> each(iterable: Iterable<A>, callback: CoroutineScope.(A) -> Unit) {
        map(iterable) { callback(it); Unit }
    }

    fun <A> with(iterable: Iterable<A>, callback: A.() -> Unit) {
        map(iterable) { it.apply(callback); Unit }
    }

    fun <A, B : Any> poolMap(iterable: Iterable<A>, mapper: CoroutineScope.(A) -> B) = poolMap(poolThreads, iterable, mapper)

    fun <A, B : Any> poolMap(threads: Int, iterable: Iterable<A>, mapper: CoroutineScope.(A) -> B): List<B> {
        return map(Executors.newFixedThreadPool(threads).asCoroutineDispatcher(), iterable, mapper)
    }

    fun <A> poolEach(iterable: Iterable<A>, callback: CoroutineScope.(A) -> Unit) = poolEach(poolThreads, iterable, callback)

    fun <A> poolEach(threads: Int, iterable: Iterable<A>, callback: CoroutineScope.(A) -> Unit) {
        poolMap(threads, iterable) { callback(it); Unit }
    }

    val poolThreads: Int get() = Runtime.getRuntime().availableProcessors()
}
