package com.cognifide.gradle.common

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class CommonPluginFunctionalTest {

    @Test
    fun `can run task`() {
        // Setup the test build
        val projectDir = File("build/functionalTest").apply {
            deleteRecursively()
            mkdirs()
            resolve("settings.gradle.kts").writeText("")
            resolve("build.gradle.kts").writeText(
                """
                plugins {
                    id("com.cognifide.common")
                }
            """
            )
        }

        // Run the build
        val result = GradleRunner.create().run {
            forwardOutput()
            withPluginClasspath()
            withArguments("tasks")
            withProjectDir(projectDir)
            build()
        }

        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)

        // val result = runner.build()

        // Verify the result
        // assertTrue(result.output.contains("Hello from plugin 'com.cognifide.gradle.common'"))
    }
}
