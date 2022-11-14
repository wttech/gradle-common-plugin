package com.cognifide.gradle.common.build

class PropertyGroup(val parser: PropertyParser, val group: String, val member: String) {

    fun string(prop: String) = parser.string("$group.$member.$prop") ?: parser.string("$group.$DEFAULT.$prop")

    fun strings(prop: String) = parser.list("$group.$member.$prop") ?: parser.list("$group.$DEFAULT.$prop")

    fun int(prop: String) = parser.int("$group.$member.$prop") ?: parser.int("$group.$DEFAULT.$prop")

    fun long(prop: String) = parser.long("$group.$member.$prop") ?: parser.long("$group.$DEFAULT.$prop")

    fun boolean(prop: String) = parser.boolean("$group.$member.$prop") ?: parser.boolean("$group.$DEFAULT.$prop")

    fun file(prop: String) = parser.file("$group.$member.$prop") ?: parser.file("$group.$member.$DEFAULT")

    fun map(prop: String) = parser.map("$group.$member.$prop") ?: parser.map("$group.$DEFAULT.$prop")

    companion object {
        const val DEFAULT = "default"
    }
}
