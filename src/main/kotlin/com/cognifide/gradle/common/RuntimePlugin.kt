package com.cognifide.gradle.common

import com.cognifide.gradle.common.tasks.runtime.*
import org.gradle.api.Project

class RuntimePlugin : CommonDefaultPlugin() {

    override fun Project.configureProject() {
        tasks {
            register<Resolve>(Resolve.NAME)

            register<Up>(Up.NAME) {
                mustRunAfter(Destroy.NAME, Resolve.NAME)
            }

            register<Await>(Await.NAME) {
                mustRunAfter(Up.NAME)
            }

            register<Restart>(Restart.NAME) {
                dependsOn(Down.NAME, Up.NAME)
            }

            register<Down>(Down.NAME)

            register<Destroy>(Destroy.NAME) {
                dependsOn(Down.NAME)
            }

            register<Setup>(Setup.NAME) {
                dependsOn(Up.NAME)
                mustRunAfter(Destroy.NAME)
            }

            register<Resetup>(Resetup.NAME) {
                dependsOn(Destroy.NAME, Setup.NAME)
            }
        }
    }
}