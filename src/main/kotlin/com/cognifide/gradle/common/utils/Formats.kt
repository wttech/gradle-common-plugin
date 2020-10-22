package com.cognifide.gradle.common.utils

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.apache.commons.lang3.time.DurationFormatUtils
import org.gradle.api.Project
import org.gradle.util.GradleVersion
import org.jsoup.parser.Parser
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.nio.file.Paths
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

@Suppress("MagicNumber", "TooManyFunctions")
object Formats {

    // Versions

    fun asVersion(value: String, partsTaken: Int = 3): GradleVersion {
        if (value.isBlank()) {
            return versionUnknown()
        }

        return try {
            GradleVersion.version(value.split(".").take(partsTaken).joinToString("."))
        } catch (e: IllegalArgumentException) {
            return versionUnknown()
        }
    }

    fun versionAtLeast(actual: String, required: String) = asVersion(actual) >= asVersion(required)

    fun versionUnknown() = GradleVersion.version("0.0.0")

    // JSON convertions

    private fun jsonMapper() = ObjectMapper().apply {
        registerModule(KotlinModule())
    }

    private val ObjectMapper.mapType get() = typeFactory.constructMapType(HashMap::class.java, String::class.java, Any::class.java)

    private fun jsonWriter(pretty: Boolean) = jsonMapper().run {
        when {
            pretty -> writer(DefaultPrettyPrinter().apply {
                indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
            })
            else -> writer()
        }
    }

    fun asJson(input: InputStream) = jsonMapper().readTree(input)

    fun asJson(text: String) = jsonMapper().readTree(text)

    fun toMapFromJson(jsonNode: JsonNode): Map<String, Any> {
        if (jsonNode.nodeType != JsonNodeType.OBJECT) {
            throw FormatException("Only JSON node of type '${JsonNodeType.OBJECT}' " +
                    "can be converted to map but type '${jsonNode.nodeType}' detected!\n" +
                    "Ensure that JSON to be converted is not blank.")
        }
        return jsonMapper().run { convertValue<Map<String, Any>>(jsonNode, mapType) } ?: mapOf()
    }

    inline fun <reified T : Any> toObjectFromJson(json: String) = toObjectFromJson(json, T::class.java)

    fun <T> toObjectFromJson(input: InputStream, clazz: Class<T>): T = jsonMapper().readValue(input, clazz)

    fun <T> toObjectFromJson(json: String, clazz: Class<T>): T = jsonMapper().readValue(json, clazz)

    fun toMapFromJson(input: InputStream) = toMapFromJson(asJson(input))

    fun toMapFromJson(text: String) = toMapFromJson(asJson(text))

    fun toJson(value: Any, pretty: Boolean = true): String = jsonWriter(pretty).writeValueAsString(value) ?: ""

    fun toJson(value: Map<String, Any?>, pretty: Boolean = true): String = jsonWriter(pretty).writeValueAsString(value) ?: "{}"

    // HTML

    fun asHtml(value: String) = Parser.htmlParser().parseInput(value, "")

    fun asHtml(input: InputStream) = Parser.htmlParser().parseInput(input.bufferedReader(), "")

    // XML

    fun asXml(value: String) = Parser.xmlParser().parseInput(value, "")

    fun asXml(input: InputStream) = Parser.xmlParser().parseInput(input.bufferedReader(), "")

    // Strings (e.g raw strings parsing from 'gradle.properties')

    fun toList(value: String?, delimiter: String = ","): List<String>? {
        if (value.isNullOrBlank()) {
            return null
        }

        val between = StringUtils.substringBetween(value, "[", "]") ?: value
        if (between.isBlank()) {
            return null
        }

        return between.split(delimiter)
    }

    fun toMap(value: String?, valueDelimiter: String = ",", keyDelimiter: String = "="): Map<String, String>? {
        return toList(value, valueDelimiter)?.map { v ->
            v.split(keyDelimiter).let { e ->
                when (e.size) {
                    2 -> e[0] to e[1]
                    else -> v to ""
                }
            }
        }?.toMap()
    }

    // Encoding

    fun toPassword(value: String) = "*".repeat(value.length)

    fun toBase64(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())

    fun toMd5(text: String): String {
        val messageDigest = MessageDigest.getInstance("MD5")
        val data = text.toByteArray()
        messageDigest.update(data, 0, data.size)
        val result = BigInteger(1, messageDigest.digest())
        return String.format("%1$032x", result)
    }

    fun toHashCodeHex(value: Any) = Integer.toHexString(HashCodeBuilder().append(value).toHashCode())

    // Math & numbers and transformations

    fun toChecksum(file: File) = file.inputStream().use { DigestUtils.md5Hex(it) }

    fun toChecksumFile(file: File) = file.resolveSibling("${file.name}.md5")

    fun checksum(file: File, recalculate: Boolean = false) = toChecksumFile(file).let { checksumFile ->
        when {
            recalculate || !checksumFile.exists() -> toChecksum(file).also { checksumFile.writeText(it) }
            else -> checksumFile.readText()
        }
    }

    fun fileSize(file: File): String = fileSizeBytesToHuman(when {
        file.exists() -> FileUtils.sizeOf(file)
        else -> 0L
    })

    fun fileSizeBytesToHuman(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> (bytes / 1024).toString() + " KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun percent(current: Int, total: Int): String = percent(current.toLong(), total.toLong())

    fun percentExplained(current: Int, total: Int): String = "$current/$total=${percent(current, total)}"

    fun percent(current: Long, total: Long): String {
        val value: Double = when (total) {
            0L -> 0.0
            else -> current.toDouble() / total.toDouble()
        }

        return "${"%.2f".format(value * 100.0)}%"
    }

    fun percentExplained(current: Long, total: Long) = "$current/$total=${percent(current, total)}"

    // Date & time

    fun localDateTimeAt(timestamp: Long, zoneId: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)

    fun date(date: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)

    fun dateAt(timestamp: Long, zoneId: ZoneId): Date = Date.from(localDateTimeAt(timestamp, zoneId).atZone(ZoneId.systemDefault()).toInstant())

    fun dateFileName(date: Date = Date()): String = SimpleDateFormat("yyyyMMddHHmmss").format(date)

    fun duration(millis: Long): String = DurationFormatUtils.formatDuration(millis.coerceAtLeast(0L), "mm:ss")

    fun durationSince(millis: Long) = duration(System.currentTimeMillis() - millis)

    fun durationFit(thenMillis: Long, thenZoneId: ZoneId, durationMillis: Long): Boolean {
        val nowTimestamp = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val thenTimestamp = localDateTimeAt(thenMillis, thenZoneId)
        val diffMillis = ChronoUnit.MILLIS.between(thenTimestamp, nowTimestamp)

        return diffMillis < durationMillis
    }

    // Files & structure

    fun rootProjectPath(file: File, project: Project): String {
        return rootProjectPath(file.absolutePath, project)
    }

    fun rootProjectPath(path: String, project: Project): String {
        return projectPath(path, project.rootProject)
    }

    fun projectPath(file: File, project: Project): String {
        return projectPath(file.absolutePath, project)
    }

    fun projectPath(path: String, project: Project): String {
        return relativePath(path, project.projectDir.absolutePath)
    }

    fun relativePath(path: String, basePath: String): String {
        val source = Paths.get(path)
        val base = Paths.get(basePath)

        return base.relativize(source).toString()
    }

    fun normalizePath(path: String): String {
        return path.replace("\\", "/")
    }
}

fun JsonNode.asMap() = Formats.toMapFromJson(this)

fun String.asXml() = Formats.asXml(this)

fun String.asJson() = Formats.asJson(this)

fun String.asHtml() = Formats.asHtml(this)
