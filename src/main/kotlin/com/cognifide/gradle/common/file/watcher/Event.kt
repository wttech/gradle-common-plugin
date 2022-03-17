package com.cognifide.gradle.common.file.watcher

import java.io.File
import java.util.*

class Event(val file: File, val type: EventType) {

    override fun toString(): String {
        return "$file [${type.name.lowercase(Locale.getDefault()).replace("_", " ")}]"
    }
}
