import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    id("com.gradle.plugin-publish") version "0.20.0"
    id("io.gitlab.arturbosch.detekt") version "1.20.0-RC1"
    id("net.researchgate.release") version "2.8.1"
    id("com.github.breadmoirai.github-release") version "2.2.12"
}

defaultTasks("build", "publishToMavenLocal")
description = "Gradle Common Plugin"
group = "com.cognifide.gradle"

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins.withId("java") {
        java {
            withJavadocJar()
            withSourcesJar()
        }
        tasks.withType<JavaCompile>().configureEach{
            sourceCompatibility = JavaVersion.VERSION_1_8.toString()
            targetCompatibility = JavaVersion.VERSION_1_8.toString()
        }
        tasks.withType<Test>().configureEach {
            testLogging.showStandardStreams = true
            useJUnitPlatform()
        }
    }
    plugins.withId("kotlin") {
        tasks.withType<KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_1_8.toString()
            }
        }
    }
}

dependencies {
    // Build environment
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.20.0-RC1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")

    // External dependencies
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.sshd:sshd-sftp:2.8.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("org.apache.httpcomponents:httpmime:4.5.13")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.1")

    implementation("org.codelibs:jcifs:1.3.18.3")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("commons-io:commons-io:2.11.0")
    implementation("io.pebbletemplates:pebble:3.1.5")
    implementation("com.dorkbox:Notify:3.7")
    implementation("net.lingala.zip4j:zip4j:2.9.1")
}

val functionalTestSourceSet = sourceSets.create("functionalTest")
gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.getByName("functionalTestImplementation").extendsFrom(configurations.getByName("testImplementation"))

val functionalTest by tasks.creating(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    dependsOn("detektFunctionalTest")
}

val check by tasks.getting(Task::class) {
    dependsOn(functionalTest)
}

tasks {
    test {
        dependsOn("detektTest")
    }

    afterReleaseBuild {
        dependsOn("publishPlugins")
    }

    named("githubRelease") {
        mustRunAfter("release")
    }

    register("fullRelease") {
        dependsOn("release", "githubRelease")
    }
}

detekt {
    config.from(file("detekt.yml"))
    parallel = true
    autoCorrect = true
}

gradlePlugin {
    plugins {
        create("common") {
            id = "com.cognifide.common"
            implementationClass = "com.cognifide.gradle.common.CommonPlugin"
            displayName = "Common Plugin"
            description = "Provides generic purpose Gradle utilities like: file transfer (upload/download)" +
                    " via SMB/SFTP/HTTP, file watcher, async progress logger, GUI notification service."
        }
        create("runtime") {
            id = "com.cognifide.common.runtime"
            implementationClass = "com.cognifide.gradle.common.RuntimePlugin"
            displayName = "Runtime Plugin"
            description = "Introduces base lifecycle tasks (like 'up', 'down') for controlling runtimes (servers, applications)"
        }
    }
}

val pluginTags = listOf(
    "sftp", "smb", "ssh", "progress", "file watcher", "file download", "file upload",
    "gui notification", "gradle-plugin", "gradle plugin development"
)

pluginBundle {
    website = "https://github.com/wttech/gradle-common-plugin"
    vcsUrl = "https://github.com/wttech/gradle-common-plugin.git"
    description = "Gradle Common Plugin"
    tags = pluginTags
}

githubRelease {
    owner("wttech")
    repo("gradle-common-plugin")
    token((project.findProperty("github.token") ?: "").toString())
    tagName(project.version.toString())
    releaseName(project.version.toString())
    releaseAssets(tasks["jar"], tasks["sourcesJar"], tasks["javadocJar"])
    draft((project.findProperty("github.draft") ?: "false").toString().toBoolean())
    prerelease((project.findProperty("github.prerelease") ?: "false").toString().toBoolean())
    overwrite((project.findProperty("github.override") ?: "true").toString().toBoolean())

    body { """
    |# What's new
    |
    |TBD
    |
    |# Upgrade notes
    |
    |Nothing to do.
    |
    |# Contributions
    |
    |None.
    """.trimMargin()
    }
}
