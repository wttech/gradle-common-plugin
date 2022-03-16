package com.cognifide.gradle.common

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class CommonPluginTest {
    @Test
    fun `plugin could be set up`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cognifide.gradle.common")

        // Verify the result
        // assertNotNull(project.tasks.findByName("greeting"))
    }
}
