package com.cognifide.gradle.common

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal

open class CommonDefaultTask : DefaultTask(), CommonTask {

    @Internal
    final override val common = project.common
}
