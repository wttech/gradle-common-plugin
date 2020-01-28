import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
}

defaultTasks("clean", "publishToMavenLocal")
description = "Gradle Common Plugin"
group = "com.cognifide.gradle"

repositories {
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("org.apache.commons:commons-text:1.8")
    implementation("commons-io:commons-io:2.6")
    implementation("org.reflections:reflections:0.9.9")
    implementation("org.samba.jcifs:jcifs:1.3.18-kohsuke-1")
//    implementation("org.zeroturnaround:zt-zip:1.13")
//    implementation("net.lingala.zip4j:zip4j:1.3.3")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")
    implementation("org.jsoup:jsoup:1.12.1")

    implementation("org.apache.sshd:sshd-sftp:2.3.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.10")
    implementation("org.apache.httpcomponents:httpmime:4.5.10")
    implementation("io.pebbletemplates:pebble:3.1.2")
    implementation("com.dorkbox:Notify:3.7")
    implementation("com.jayway.jsonpath:json-path:2.4.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

gradlePlugin {
    val greeting by plugins.creating {
        id = "com.cognifide.gradle.common"
        implementationClass = "com.cognifide.gradle.common.CommonPlugin"
    }
}

val functionalTestSourceSet = sourceSets.create("functionalTest") {}

gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.getByName("functionalTestImplementation").extendsFrom(configurations.getByName("testImplementation"))

val functionalTest by tasks.creating(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
}

val check by tasks.getting(Task::class) {
    dependsOn(functionalTest)
}

tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            freeCompilerArgs = freeCompilerArgs + "-Xuse-experimental=kotlin.Experimental"
        }
    }