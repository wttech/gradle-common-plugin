[![WTT logo](docs/wtt-logo.png)](https://www.wundermanthompson.com)

[![Gradle Status](https://gradleupdate.appspot.com/wttech/gradle-common-plugin/status.svg)](https://gradleupdate.appspot.com/wtt/gradle-common-plugin/status)
[![Apache License, Version 2.0, January 2004](docs/apache-license-badge.svg)](http://www.apache.org/licenses/)

# Gradle Common Plugin

  * [About](#about)
  * [Setup](#setup)
  * [Usage](#usage)
  * [Configuration](#configuration)
  * [License](#license)

## About

Provides generic purpose Gradle utilities like: 

* File transfer (upload/download) via SMB/SFTP/HTTP,
* File watcher,
* Asynchronous progress logger,
* GUI notification service,
* Health checking service,
* Java toolchains service.

All above exposed as `common` [extension](src/main/kotlin/com/cognifide/gradle/common/CommonExtension.kt) to your build scripts to be consumed by your own Gradle tasks.

## Setup

As of plugin is published at official Gradle Plugin Portal, see instructions from [there](https://plugins.gradle.org/plugin/com.cognifide.common).

## Usage

Use common extension to implement any custom Gradle task with improved UX:

```kotlin
tasks {
    register("transferFile") {
        doLast {
            common {
                // download file using unified file resolver with interactive progress logger
                val mavenFile = resolveFile("some-group:some-package:1.0.0@zip") // Maven coordinates / Gradle dependency notation
                val urlFile = resolveFile("sftp://my-file-storage.com/some/path/my-file.zip") // some HTTP/SMB/SFTP URL
    
                // upload file using interactive progress logger
                fileTransfer.upload("smb://other-file-storage/other/path", mavenFile)
                fileTransfer.upload("sftp://yet-another-file-storage.com/other/path", urlFile)
    
                notifier.info("Files transferred successfully!")
            }
        }       
    }

    register("calculateNums") {
        doLast {
            common {
                // user input service is not available in public Gradle API however plugin is exposing it
                if (userInput.askYesNoQuestion("Calc in parallel?")) { 
                    val nums = listOf(1, 2, 3)
        
                    // do tasks in parallel using coroutines with interactive, async progress logger
                    progress(nums.size) {
                        parallel.each(nums) { num ->
                            increment("Calculating for '$num'")
                                waitFor(5000) // heavy calculation here
                            }   
                        }
                    }   
                }
            
                // this will freeze task a little bit but with showing nice countdown timer
                progressCountdown(3_000)
    
                notifier.info("Calculated successfully!")
            }
        }       
    }
}
```

## Configuration

Illustrative example:

```kotlin
common {
   fileTransfer {

        // default values for all protocols

        user.set(prop.string("fileTransfer.user"))
        password.set(prop.string("fileTransfer.password"))
        domain.set(prop.string("fileTransfer.domain"))

        // protocol specific credentials

        sftp {
            user.set(prop.string("fileTransfer.sftp.user"))
            password.set(prop.string("fileTransfer.sftp.password"))
        }
        smb {
            user.set(prop.string("fileTransfer.smb.user"))
            password.set(prop.string("fileTransfer.smb.password"))
            domain.set(prop.string("fileTransfer.smb.domain"))
        }
        http {
            client {
                basicUser.set(prop.string("fileTransfer.http.user"))
                basicPassword.set(prop.string("fileTransfer.http.password"))
            }
        }
    }
    notifier {
        enabled.set(prop.boolean("notifier.enabled"))
    }
}
```

## License

**Gradle Common Plugin** is licensed under the [Apache License, Version 2.0 (the "License")](https://www.apache.org/licenses/LICENSE-2.0.txt)
