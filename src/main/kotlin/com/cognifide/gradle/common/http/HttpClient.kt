package com.cognifide.gradle.common.http

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.common.file.transfer.http.HttpFileTransfer
import com.cognifide.gradle.common.guava.net.UrlEscapers
import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.common.utils.Utils
import com.cognifide.gradle.common.utils.asMap
import com.fasterxml.jackson.databind.JsonNode
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
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
import org.apache.http.entity.FileEntity
import org.apache.http.entity.InputStreamEntity
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicNameValuePair
import org.apache.http.ssl.SSLContextBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.*

@Suppress("TooManyFunctions")
open class HttpClient(private val common: CommonExtension) {

    private val logger = common.logger

    val connectionTimeout = common.obj.int {
        convention(30_000)
        common.prop.int("httpClient.connectionTimeout")?.let { set(it) }
    }

    val connectionIgnoreSsl = common.obj.boolean {
        convention(true)
        common.prop.boolean("httpClient.connectionIgnoreSsl")?.let { set(it) }
    }

    val connectionRetries = common.obj.boolean {
        convention(true)
        common.prop.boolean("httpClient.connectionRetries")?.let { set(it) }
    }

    val authorizationPreemptive = common.obj.boolean {
        convention(false)
        common.prop.boolean("httpClient.authorizationPreemptive")?.let { set(it) }
    }

    val baseUrl = common.obj.string()

    val escapeUrl = common.obj.boolean { convention(false) }

    val basicUser = common.obj.string {
        common.prop.string("httpClient.basicUser")?.let { set(it) }
    }

    val basicPassword = common.obj.string {
        common.prop.string("httpClient.basicPassword")?.let { set(it) }
    }

    var basicCredentials: Pair<String, String>?
        get() = if (basicUser.isPresent && basicPassword.isPresent) (basicUser.get() to basicPassword.get()) else null
        set(value) {
            value?.let {
                basicUser.set(value.first)
                basicPassword.set(value.second)
            }
        }

    val fileTransferCredentials = {
        basicCredentials = common.fileTransfer.credentials
    }

    var bearerToken = common.obj.string {}

    val fileTransferBearerToken = {
        bearerToken = common.fileTransfer.bearerToken
    }

    val multipartTextType = common.obj.typed<ContentType> {
        convention(
            ContentType.create(
                ContentType.TEXT_PLAIN.mimeType,
                StandardCharsets.UTF_8
            )
        )
    }

    val multipartBinaryType = common.obj.typed<ContentType> { convention(ContentType.DEFAULT_BINARY) }

    val proxyHost = common.obj.string {
        common.prop.string("httpClient.proxyHost")?.let { set(it) }
    }

    val proxyPort = common.obj.int {
        common.prop.int("httpClient.proxyPort")?.let { set(it) }
    }

    val proxyScheme = common.obj.string {
        common.prop.string("httpClient.proxyScheme")?.let { set(it) }
    }

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

    private fun HttpClientBuilder.useDefaults() {
        if (!basicUser.orNull.isNullOrBlank() && !basicPassword.orNull.isNullOrBlank()) {
            if (authorizationPreemptive.get()) {
                addInterceptorFirst(PreemptiveAuthInterceptor())
            }

            setDefaultCredentialsProvider(
                BasicCredentialsProvider().apply {
                    setCredentials(AuthScope.ANY, UsernamePasswordCredentials(basicUser.get(), basicPassword.get()))
                }
            )
        }

        if (bearerToken.isPresent && bearerToken.get().isNotBlank()) {
            val headers = listOf(
                BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"),
                BasicHeader(HttpHeaders.AUTHORIZATION, "Bearer ${bearerToken.get()}")
            )
            setDefaultHeaders(headers)
        }

        setDefaultRequestConfig(
            RequestConfig.custom().apply {
                setCookieSpec(CookieSpecs.STANDARD)

                if (!connectionRetries.get()) {
                    setSocketTimeout(connectionTimeout.get())
                }
                setConnectTimeout(connectionTimeout.get())
                setConnectionRequestTimeout(connectionTimeout.get())
            }.build()
        )

        if (!proxyHost.orNull.isNullOrBlank() && proxyPort.isPresent) {
            setProxy(HttpHost(proxyHost.get(), proxyPort.get(), proxyScheme.get()))
        }

        if (connectionIgnoreSsl.get()) {
            setSSLSocketFactory(
                SSLConnectionSocketFactory(
                    SSLContextBuilder()
                        .loadTrustMaterial(null) { _, _ -> true }
                        .build(),
                    NoopHostnameVerifier.INSTANCE
                )
            )
        }

        if (!connectionRetries.get()) {
            disableAutomaticRetries()
        }
    }

    private val client get() = HttpClientBuilder.create().apply(clientBuilder).build()

    fun <T> client(action: CloseableHttpClient.() -> T) = client.use(action)

    fun responseHandler(handler: (HttpResponse) -> Unit) {
        this.responseHandler = handler
    }

    private var responseHandler: (HttpResponse) -> Unit = {}

    val responseChecks = common.obj.boolean { convention(true) }

    fun responseChecker(checker: (HttpResponse) -> Unit) {
        this.responseChecker = checker
    }

    private var responseChecker: (HttpResponse) -> Unit = { checkStatus(it) }

    fun <T> request(method: String, uri: String, handler: HttpClient.(HttpResponse) -> T) =
        request(method, uri, {}, handler)

    fun <T> request(
        method: String,
        uri: String,
        options: HttpRequestBase.() -> Unit,
        handler: HttpClient.(HttpResponse) -> T
    ) = when (method.lowercase(Locale.getDefault())) {
        "get" -> get(uri, handler, options)
        "post" -> post(uri, handler, options)
        "put" -> put(uri, handler, options)
        "patch" -> patch(uri, handler, options)
        "head" -> head(uri, handler, options)
        "delete" -> delete(uri, handler, options)
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

    fun put(uri: String, entity: File) = put(uri, entity) { checkStatus(it) }

    fun <T> put(uri: String, entity: File, handler: HttpClient.(HttpResponse) -> T) = put(uri, entity, handler) {}

    fun <T> put(uri: String, entity: File, handler: HttpClient.(HttpResponse) -> T, options: HttpPut.() -> Unit): T {
        return put(uri, handler) { this.entity = createEntity(entity); options() }
    }

    fun patch(path: String) = patch(path) { checkStatus(it) }

    fun <T> patch(uri: String, handler: HttpClient.(HttpResponse) -> T): T = patch(uri, handler) {}

    fun <T> patch(uri: String, handler: HttpClient.(HttpResponse) -> T, options: HttpPatch.() -> Unit): T {
        return execute(HttpPatch(baseUrl(uri)).apply(options), handler)
    }

    fun patch(uri: String, entity: File) = patch(uri, entity) { checkStatus(it) }

    fun <T> patch(uri: String, entity: File, handler: HttpClient.(HttpResponse) -> T) = patch(uri, entity, handler) {}

    fun <T> patch(
        uri: String,
        entity: File,
        handler: HttpClient.(HttpResponse) -> T,
        options: HttpPatch.() -> Unit
    ): T {
        return patch(uri, handler) { this.entity = createEntity(entity); options() }
    }

    fun post(url: String, params: Map<String, Any?> = mapOf()) = postUrlencoded(url, params)

    fun <T> post(uri: String, params: Map<String, Any?> = mapOf(), handler: HttpClient.(HttpResponse) -> T): T {
        return postUrlencoded(uri, params, handler)
    }

    fun postUrlencoded(uri: String, params: Map<String, Any?> = mapOf()) =
        postUrlencoded(uri, params) { checkStatus(it) }

    fun <T> postUrlencoded(
        uri: String,
        params: Map<String, Any?> = mapOf(),
        handler: HttpClient.(HttpResponse) -> T
    ): T {
        return post(uri, handler) { entity = createEntityUrlencoded(params) }
    }

    fun postMultipart(uri: String, params: Map<String, Any?> = mapOf()) = postMultipart(uri, params) { checkStatus(it) }

    fun <T> postMultipart(
        uri: String,
        params: Map<String, Any?> = mapOf(),
        handler: HttpClient.(HttpResponse) -> T
    ): T {
        return post(uri, handler) { entity = createEntityMultipart(params) }
    }

    fun <T> post(uri: String, handler: HttpClient.(HttpResponse) -> T): T = post(uri, handler) {}

    fun post(uri: String, entity: File) = post(uri, entity) { checkStatus(it) }

    fun <T> post(uri: String, entity: File, handler: HttpClient.(HttpResponse) -> T) = post(uri, entity, handler) {}

    fun <T> post(uri: String, entity: File, handler: HttpClient.(HttpResponse) -> T, options: HttpPost.() -> Unit) =
        post(uri, handler) {
            this.entity = createEntity(entity); options()
        }

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
        throw ResponseException(
            "Cannot parse response JSON as object, because response is probably malformed. Cause: ${e.message}\n$response",
            e
        )
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
            throw ResponseException(
                "Cannot parse response JSON as map, because response is probably malformed. Cause: ${e.message}\n$response",
                e
            )
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

    fun checkText(response: HttpResponse, containedText: String, ignoreCase: Boolean = true) =
        checkTexts(response, listOf(containedText), ignoreCase)

    fun checkTexts(response: HttpResponse, containedTexts: Iterable<String>, ignoreCase: Boolean = true) {
        val text = asString(response)
        containedTexts.forEach { containedText ->
            if (!text.contains(containedText, ignoreCase)) {
                logger.debug("Actual text:\n$text")
                throw ResponseException("Response does not contain text: $containedText")
            }
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
        val escapedUri = if (escapeUrl.get()) UrlEscapers.urlFragmentEscaper().escape(uri) else uri
        return "${baseUrl.orNull ?: ""}$escapedUri"
    }

    @Suppress("TooGenericExceptionCaught")
    open fun <T> execute(method: HttpRequestBase, handler: HttpClient.(HttpResponse) -> T) = try {
        client {
            requestConfigurer(method)
            val response = execute(method)
            responseHandler(response)
            this@HttpClient.handler(response)
        }
    } catch (e: Exception) {
        throw RequestException("Failed request to $method! Cause: ${e.message}", e)
    }

    fun execute(method: HttpRequestBase) = execute(method) { checkStatus(it) }

    open fun createEntityUrlencoded(params: Map<String, Any?>): HttpEntity {
        return UrlEncodedFormEntity(
            params.entries.fold(mutableListOf<NameValuePair>()) { result, (key, value) ->
                Utils.unroll(value) { addEntityUrlencoded(result, key, it) }
                result
            }
        )
    }

    private fun addEntityUrlencoded(result: MutableList<NameValuePair>, key: String, value: Any?) {
        result.add(BasicNameValuePair(key, value?.toString() ?: ""))
    }

    open fun createEntityMultipart(params: Map<String, Any?>): HttpEntity {
        return MultipartEntityBuilder.create().apply {
            params.forEach { (key, value) -> Utils.unroll(value) { addEntityMultipart(key, it) } }
        }.build()
    }

    fun createEntity(value: Any) = when (value) {
        is File -> createEntity(value)
        is String -> createEntity(value)
        is InputStream -> createEntity(value)
        else -> throw RequestException("Unsupported request entity type '${value.javaClass}'!")
    }

    fun createEntity(text: String) = StringEntity(text)

    fun createEntity(file: File) = FileEntity(file)

    fun createEntity(input: InputStream) = InputStreamEntity(input)

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

    private fun <T> fileTransfer(operation: HttpFileTransfer.() -> T): T =
        common.httpFile { client = this@HttpClient; operation() }

    companion object {
        val STATUS_CODE_VALID = 200 until 300
    }
}
