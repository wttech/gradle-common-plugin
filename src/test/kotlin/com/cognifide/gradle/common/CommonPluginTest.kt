package com.cognifide.gradle.common

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class CommonPluginTest {

    @Test
    fun `plugin could be set up`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cognifide.common")
    }
}
