package com.cognifide.gradle.common.http

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.http.HttpFileTransfer
import com.cognifide.gradle.common.guava.net.UrlEscapers
import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.common.utils.Utils
import com.cognifide.gradle.common.utils.asMap
import com.fasterxml.jackson.databind.JsonNode
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.*
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import org.apache.http.ssl.SSLContextBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.StandardCharsets

@Suppress("TooManyFunctions")
open class HttpClient(private val common: CommonExtension) {

    private val logger = common.logger

    val connectionTimeout = common.obj.int { convention(30000) }

    val connectionIgnoreSsl = common.obj.boolean { convention(true) }

    val connectionRetries = common.obj.boolean { convention(true) }

    val authorizationPreemptive = common.obj.boolean { convention(false) }

    val baseUrl = common.obj.string()

    val basicUser = common.obj.string()

    val basicPassword = common.obj.string()

    var basicCredentials: Pair<String?, String?>
        get() = when {
            basicUser.isPresent && basicPassword.isPresent -> (basicUser.get() to basicPassword.get())
            else -> throw HttpException("HTTP client basic credentials are missing!")
        }
        set(value) {
            basicUser.set(value.first)
            basicPassword.set(value.second)
        }

    val multipartTextType = common.obj.typed<ContentType> { convention(ContentType.create(ContentType.TEXT_PLAIN.mimeType, StandardCharsets.UTF_8)) }

    val multipartBinaryType = common.obj.typed<ContentType> { convention(ContentType.DEFAULT_BINARY) }

    val proxyHost = common.obj.string()

    val proxyPort = common.obj.int()

    val proxyScheme = common.obj.string()

    fun requestConfigurer(configurer: HttpRequestBase.() -> Unit) {
        this.requestConfigurer = configurer
    }

    private var requestConfigurer: HttpRequestBase.() -> Unit = {}

    fun clientBuilder(builder: HttpClientBuilder.() -> Unit) {
        this.clientBuilder = builder
    }

    private var clientBuilder: HttpClientBuilder.() -> Unit = {
        useSystemProperties()
        useDefaults()
    }

    fun HttpClientBuilder.useDefaults() {
        if (authorizationPreemptive.get()) {
            addInterceptorFirst(PreemptiveAuthInterceptor())
        }

        setDefaultRequestConfig(RequestConfig.custom().apply {
            setCookieSpec(CookieSpecs.STANDARD)

            if (!connectionRetries.get()) {
                setSocketTimeout(connectionTimeout.get())
            }
            setConnectTimeout(connectionTimeout.get())
            setConnectionRequestTimeout(connectionTimeout.get())
        }.build())

        if (!proxyHost.orNull.isNullOrBlank() && proxyPort.isPresent) {
            setProxy(HttpHost(proxyHost.get(), proxyPort.get(), proxyScheme.get()))
        }

        if (!basicUser.orNull.isNullOrBlank() && !basicPassword.orNull.isNullOrBlank()) {
            setDefaultCredentialsProvider(BasicCredentialsProvider().apply {
                setCredentials(AuthScope.ANY, UsernamePasswordCredentials(basicUser.get(), basicPassword.get()))
            })
        }

        if (connectionIgnoreSsl.get()) {
            setSSLSocketFactory(SSLConnectionSocketFactory(SSLContextBuilder()
                    .loadTrustMaterial(null) { _, _ -> true }
                    .build(), NoopHostnameVerifier.INSTANCE))
        }
        if (!connectionRetries.get()) {
            disableAutomaticRetries()
        }
    }

    val client by lazy { HttpClientBuilder.create().apply(clientBuilder).build() }

    fun responseHandler(handler: (HttpResponse) -> Unit) {
        this.responseHandler = handler
    }

    private var responseHandler: (HttpResponse) -> Unit = {}

    val responseChecks = common.obj.boolean { convention(true) }

    fun responseChecker(checker: (HttpResponse) -> Unit) {
        this.responseChecker = checker
    }

    private var responseChecker: (HttpResponse) -> Unit = { checkStatus(it) }

    fun <T> request(method: String, uri: String, handler: HttpClient.(HttpResponse) -> T) = when (method.toLowerCase()) {
        "get" -> get(uri, handler)
        "post" -> post(uri, handler)
        "put" -> put(uri, handler)
        "patch" -> patch(uri, handler)
        "head" -> head(uri, handler)
        "delete" -> delete(uri, handler)
        else -> throw RequestException("Invalid HTTP client method: '$method'")
    }

    fun get(uri: String) = get(uri) { checkStatus(it) }

    fun <T> get(uri: String, handler: HttpClient.(HttpResponse) -> T): T = get(uri, handler) {}

    fun <T> get(uri: String, handler: HttpClient.(HttpResponse) -> T, options: HttpGet.() -> Unit): T {
        return execute(HttpGet(baseUrl(uri)).apply(options), handler)
    }

    fun head(uri: String) = head(uri) { checkStatus(it) }

    fun <T> head(uri: String, handler: HttpClient.(HttpResponse) -> T): T = head(uri, handler) {}

    fun <T> head(uri: String, handler: HttpClient.(HttpResponse) -> T, options: HttpHead.() -> Unit): T {
        return execute(HttpHead(baseUrl(uri)).apply(options), handler)
    }

    fun delete(uri: String) = delete(uri) { checkStatus(it) }

    fun <T> delete(uri: String, handler: HttpClient.(HttpResponse) -> T): T = delete(uri, handler) {}

    fun <T> delete(uri: String, handler: HttpClient.(HttpResponse) -> T, options: HttpDelete.() -> Unit): T {
        return execute(HttpDelete(baseUrl(uri)).apply(options), handler)
    }

    fun put(uri: String) = put(uri) { checkStatus(it) }

    fun <T> put(uri: String, handler: HttpClient.(HttpResponse) -> T): T = put(uri, handler) {}

    fun <T> put(uri: String, handler: HttpClient.(HttpResponse) -> T, options: HttpPut.() -> Unit): T {
        return execute(HttpPut(baseUrl(uri)).apply(options), handler)
    }

    fun patch(path: String) = patch(path) { checkStatus(it) }

    fun <T> patch(uri: String, handler: HttpClient.(HttpResponse) -> T): T = patch(uri, handler) {}

    fun <T> patch(uri: String, handler: HttpClient.(HttpResponse) -> T, options: HttpPatch.() -> Unit): T {
        return execute(HttpPatch(baseUrl(uri)).apply(options), handler)
    }

    fun post(url: String, params: Map<String, Any?> = mapOf()) = postUrlencoded(url, params)

    fun <T> post(uri: String, params: Map<String, Any?> = mapOf(), handler: HttpClient.(HttpResponse) -> T): T {
        return postUrlencoded(uri, params, handler)
    }

    fun postUrlencoded(uri: String, params: Map<String, Any?> = mapOf()) = postUrlencoded(uri, params) { checkStatus(it) }

    fun <T> postUrlencoded(uri: String, params: Map<String, Any?> = mapOf(), handler: HttpClient.(HttpResponse) -> T): T {
        return post(uri, handler) { entity = createEntityUrlencoded(params) }
    }

    fun postMultipart(uri: String, params: Map<String, Any?> = mapOf()) = postMultipart(uri, params) { checkStatus(it) }

    fun <T> postMultipart(uri: String, params: Map<String, Any?> = mapOf(), handler: HttpClient.(HttpResponse) -> T): T {
        return post(uri, handler) { entity = createEntityMultipart(params) }
    }

    fun <T> post(uri: String, handler: HttpClient.(HttpResponse) -> T): T = post(uri, handler) {}

    fun <T> post(uri: String, handler: HttpClient.(HttpResponse) -> T, options: HttpPost.() -> Unit): T {
        return execute(HttpPost(baseUrl(uri)).apply(options), handler)
    }

    fun asStream(response: HttpResponse): InputStream {
        if (responseChecks.get()) {
            responseChecker(response)
        }

        return response.entity.content
    }

    fun asJson(response: HttpResponse): JsonNode = Formats.asJson(asStream(response))

    fun asJson(jsonString: String): JsonNode = Formats.asJson(jsonString)

    fun asHtml(response: HttpResponse): Document = Formats.asHtml(asStream(response))

    fun asHtml(htmlString: String): Document = Formats.asHtml(htmlString)

    fun asXml(response: HttpResponse): Document = Formats.asXml(asStream(response))

    fun asXml(xmlString: String): Document = Formats.asXml(xmlString)

    fun asString(response: HttpResponse): String = IOUtils.toString(asStream(response), Charsets.UTF_8) ?: ""

    inline fun <reified T : Any> asObjectFromJson(response: HttpResponse) = asObjectFromJson(response, T::class.java)

    fun <T> asObjectFromJson(response: HttpResponse, clazz: Class<T>): T = try {
        Formats.toObjectFromJson(asStream(response), clazz)
    } catch (e: IOException) {
        throw ResponseException("Cannot parse response JSON as object, because response is probably malformed. Cause: ${e.message}\n$response", e)
    }

    fun asMapFromJson(response: HttpResponse) = asJson(response).asMap()

    fun asMapFromJson(response: HttpResponse, jsonPointer: String) = asNodeFromJson(response, jsonPointer).asMap()

    @Suppress("TooGenericExceptionCaught")
    fun asNodeFromJson(response: HttpResponse, jsonPointer: String): JsonNode {
        val json = asJson(response)

        return try {
            json.at(jsonPointer).also {
                if (it.isMissingNode) {
                    throw ResponseException("Cannot parse response JSON as map, because node at pointer '$jsonPointer' is missing!\n$response")
                }
            }
        } catch (e: Exception) {
            throw ResponseException("Cannot parse response JSON as map, because response is probably malformed. Cause: ${e.message}\n$response", e)
        }
    }

    fun checkStatus(response: HttpResponse, statusCodes: IntRange = STATUS_CODE_VALID) {
        if (response.statusLine.statusCode !in statusCodes) {
            throwStatusException(response)
        }
    }

    fun checkStatus(response: HttpResponse, statusCode: Int) = checkStatus(response, listOf(statusCode))

    fun checkStatus(response: HttpResponse, statusCodes: List<Int>) {
        if (!statusCodes.contains(response.statusLine.statusCode)) {
            throwStatusException(response)
        }
    }

    open fun throwStatusException(response: HttpResponse) {
        throw ResponseException("Unexpected response status detected: ${response.statusLine}")
    }

    fun checkText(response: HttpResponse, containedText: String, ignoreCase: Boolean = true) {
        val text = asString(response)
        if (!text.contains(containedText, ignoreCase)) {
            logger.debug("Actual text:\n$text")
            throw ResponseException("Response does not contain text: $containedText")
        }
    }

    fun checkHtml(response: HttpResponse, validator: Document.() -> Boolean) {
        val html = asString(response)
        val doc = Jsoup.parse(html)
        if (!validator(doc)) {
            logger.debug("Actual HTML:\n$html")
            throw ResponseException("Response HTML does not pass validation")
        }
    }

    /**
     * Fix for HttpClient's: 'escaped absolute path not valid'
     * https://stackoverflow.com/questions/13652681/httpclient-invalid-uri-escaped-absolute-path-not-valid
     */
    open fun baseUrl(uri: String): String {
        return "${baseUrl.orNull ?: ""}${UrlEscapers.urlFragmentEscaper().escape(uri)}"
    }

    @Suppress("TooGenericExceptionCaught")
    open fun <T> execute(method: HttpRequestBase, handler: HttpClient.(HttpResponse) -> T): T {
        try {
            requestConfigurer(method)
            val response = client.execute(method)
            responseHandler(response)

            return handler.invoke(this, response)
        } catch (e: Exception) {
            throw RequestException("Failed request to $method! Cause: ${e.message}", e)
        } finally {
            method.releaseConnection()
        }
    }

    fun execute(method: HttpRequestBase) = execute(method) { checkStatus(it) }

    open fun createEntityUrlencoded(params: Map<String, Any?>): HttpEntity {
        return UrlEncodedFormEntity(params.entries.fold(mutableListOf<NameValuePair>()) { result, (key, value) ->
            Utils.unroll(value) { addEntityUrlencoded(result, key, it) }
            result
        })
    }

    private fun addEntityUrlencoded(result: MutableList<NameValuePair>, key: String, value: Any?) {
        result.add(BasicNameValuePair(key, value?.toString() ?: ""))
    }

    open fun createEntityMultipart(params: Map<String, Any?>): HttpEntity {
        return MultipartEntityBuilder.create().apply {
            params.forEach { (key, value) -> Utils.unroll(value) { addEntityMultipart(key, it) } }
        }.build()
    }

    private fun MultipartEntityBuilder.addEntityMultipart(key: String, value: Any?) {
        if (value is File && value.exists()) {
            addBinaryBody(key, value, multipartBinaryType.get(), value.name)
        } else {
            addTextBody(key, value?.toString() ?: "", multipartTextType.get())
        }
    }

    fun download(fileUrl: String) = fileTransfer { download(fileUrl) }

    fun download(fileUrl: String, target: File) = fileTransfer { download(fileUrl, target) }

    fun downloadTo(fileUrl: String, dir: File) = fileTransfer { downloadTo(fileUrl, dir) }

    private fun <T> fileTransfer(operation: HttpFileTransfer.() -> T): T = common.httpFile { client = this@HttpClient; operation() }

    companion object {
        val STATUS_CODE_VALID = 200 until 300
    }
}
