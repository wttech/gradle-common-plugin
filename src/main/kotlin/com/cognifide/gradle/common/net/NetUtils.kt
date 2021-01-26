package com.cognifide.gradle.common.net

import java.net.InetSocketAddress
import java.net.Socket

object NetUtils {

    @Suppress("TooGenericExceptionCaught")
    fun isHostReachable(hostName: String, port: Int, timeout: Int = 1000): Boolean = try {
        Socket().use {
            it.connect(InetSocketAddress(hostName, port), timeout)
            true
        }
    } catch (e: Exception) {
        false
    }
}
