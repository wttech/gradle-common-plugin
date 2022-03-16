package com.cognifide.gradle.common.utils

// https://github.com/Fleshgrinder/kotlin-case-format/blob/master/src/main/kotlin/CaseFormat.kt

@Suppress("ComplexMethod")
private fun formatCamelCase(input: String, ignore: CharArray, upperCase: Boolean): String {
    return if (input.isEmpty()) input else StringBuilder(input.length).also {
        var seenSeparator = upperCase
        var seenUpperCase = !upperCase

        input.forEach { c ->
            when (c) {
                in ignore -> {
                    it.append(c)
                    seenSeparator = upperCase
                    seenUpperCase = !upperCase
                }
                in '0'..'9' -> {
                    it.append(c)
                    seenSeparator = false
                    seenUpperCase = false
                }
                in 'a'..'z' -> {
                    it.append(if (seenSeparator) c.uppercaseChar() else c)
                    seenSeparator = false
                    seenUpperCase = false
                }
                in 'A'..'Z' -> {
                    it.append(if (seenUpperCase) c.lowercaseChar() else c)
                    seenSeparator = false
                    seenUpperCase = true
                }
                else -> if (it.isNotEmpty()) {
                    seenSeparator = true
                    seenUpperCase = false
                }
            }
        }
    }.toString()
}

fun String.toLowerCamelCase(vararg ignore: Char): String = formatCamelCase(this, ignore, false)
fun String.toUpperCamelCase(vararg ignore: Char): String = formatCamelCase(this, ignore, true)

@Suppress("ComplexMethod")
private fun formatCase(input: String, separator: Char, ignore: CharArray, upperCase: Boolean): String {
    return if (input.isEmpty()) input else StringBuilder(input.length).also {
        var seenSeparator = true
        var seenUpperCase = false

        input.forEach { c ->
            when (c) {
                in ignore -> {
                    it.append(c)
                    seenSeparator = true
                    seenUpperCase = false
                }
                in '0'..'9' -> {
                    it.append(c)
                    seenSeparator = false
                    seenUpperCase = false
                }
                in 'a'..'z' -> {
                    it.append(if (upperCase) c.uppercaseChar() else c)
                    seenSeparator = false
                    seenUpperCase = false
                }
                in 'A'..'Z' -> {
                    if (!seenSeparator && !seenUpperCase) it.append(separator)
                    it.append(if (upperCase) c else c.lowercaseChar())
                    seenSeparator = false
                    seenUpperCase = true
                }
                else -> {
                    if (!seenSeparator || !seenUpperCase) it.append(separator)
                    seenSeparator = true
                    seenUpperCase = false
                }
            }
        }
    }.toString()
}

private fun formatLowerCase(input: String, separator: Char, ignore: CharArray) = formatCase(input, separator, ignore, false)
private fun formatUpperCase(input: String, separator: Char, ignore: CharArray) = formatCase(input, separator, ignore, true)

fun String.toLowerCaseFormat(separator: Char, vararg ignore: Char) = formatLowerCase(this, separator, ignore)
fun String.toLowerDashCase(vararg ignore: Char): String = formatLowerCase(this, '-', ignore)
fun String.toLowerSnakeCase(vararg ignore: Char): String = formatLowerCase(this, '_', ignore)
fun String.toUpperCaseFormat(separator: Char, vararg ignore: Char) = formatUpperCase(this, separator, ignore)
fun String.toUpperDashCase(vararg ignore: Char): String = formatUpperCase(this, '-', ignore)
fun String.toUpperSnakeCase(vararg ignore: Char): String = formatUpperCase(this, '_', ignore)
