package com.cognifide.gradle.common.build

import java.util.concurrent.Callable
import java.util.concurrent.Executors

object Parallel {

    val poolSize: Int get() = Runtime.getRuntime().availableProcessors()

    fun <A, B : Any> poolMap(iterable: Iterable<A>, mapper: (A) -> B) = poolMap(poolSize, iterable, mapper)

    fun <A, B : Any> poolMap(size: Int, iterable: Iterable<A>, mapper: (A) -> B): List<B> {
        val executor = Executors.newFixedThreadPool(size)
        return try {
            val futures = iterable.map { i -> executor.submit(Callable { mapper(i) }) }
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    fun <A, B : Any> map(iterable: Iterable<A>, mapper: (A) -> B): List<B> = poolMap(iterable.count(), iterable, mapper)

    fun <A> poolEach(iterable: Iterable<A>, callback: (A) -> Unit) = poolEach(poolSize, iterable, callback)

    fun <A> poolEach(size: Int, iterable: Iterable<A>, callback: (A) -> Unit) {
        poolMap(size, iterable) { callback(it); Unit }
    }

    fun <A> each(iterable: Iterable<A>, callback: (A) -> Unit) {
        map(iterable) { callback(it); Unit }
    }

    fun <A> poolWith(iterable: Iterable<A>, callback: A.() -> Unit) = poolWith(poolSize, iterable, callback)

    fun <A> poolWith(size: Int, iterable: Iterable<A>, callback: A.() -> Unit) {
        poolMap(size, iterable) {
            callback(it); Unit
        }
    }

    fun <A> with(iterable: Iterable<A>, callback: A.() -> Unit) {
        map(iterable) { callback(it); Unit }
    }
}
