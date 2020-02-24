[![Cognifide logo](docs/cognifide-logo.png)](http://cognifide.com)

[![Download](https://api.bintray.com/packages/cognifide/maven-public/gradle-common-plugin/images/download.svg) ](https://bintray.com/cognifide/maven-public/gradle-common-plugin/_latestVersion)
[![Build Status](https://dev.azure.com/gradle-aem/gradle-common-plugin/_apis/build/status/Cognifide.gradle-common-plugin?branchName=master)](https://dev.azure.com/gradle-aem/gradle-common-plugin/_build/latest?definitionId=8&branchName=master)[![Gradle Status](https://gradleupdate.appspot.com/Cognifide/gradle-common-plugin/status.svg?random=123)](https://gradleupdate.appspot.com/Cognifide/gradle-common-plugin/status)
[![Apache License, Version 2.0, January 2004](docs/apache-license-badge.svg)](http://www.apache.org/licenses/)

# Gradle Common Plugin

  * [About](#about)
  * [Setup](#setup)
  * [Configuration](#configuration)
  * [License](#license)

## About

Provides generic purpose Gradle utilities like: 

* File transfer (upload/download) via SMB/SFTP/HTTP,
* File watcher,
* Asynchronous progress logger,
* GUI notification service.

All above exposed as `common` [extension](src/main/kotlin/com/cognifide/gradle/common/CommonExtension.kt) to your build scripts to be consumed by your own Gradle tasks.

## Setup

As of plugin is published at official Gradle Plugin Portal, see instructions from [there](https://plugins.gradle.org/plugin/com.cognifide.common).

## Configuration

Illustrative example:

```kotlin
common {
   fileTransfer { // config for files over protocols HTTP/SFTP/SMB
        sftp {
            user = prop.string("fileTransfer.sftp.user")
            password = prop.string("fileTransfer.sftp.password")
        }
        smb {
            user = prop.string("fileTransfer.smb.user")
            password = prop.string("fileTransfer.smb.password")
            domain = prop.string("fileTransfer.smb.domain")
        }
        http {
            client {
                basicUser = prop.string("fileTransfer.http.user")
                basicPassword = prop.string("fileTransfer.http.password")
            }
        }
        credentials( // shorthand method for setting at once all user / password pairs and domain above
            prop.string("fileTransfer.user"), 
            prop.string("fileTransfer.password"),
            prop.string("fileTransfer.domain")
        ) 
    }
}
```

## License

**Gradle Common Plugin** is licensed under the [Apache License, Version 2.0 (the "License")](https://www.apache.org/licenses/LICENSE-2.0.txt)
