package com.cognifide.gradle.common.file.transfer

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

interface FileTransferHandler : FileTransfer {

    /**
     * Unique identifier.
     */
    val name: String

    /**
     * When enabled, transfer will be considered when finding transfer handling particular URL.
     */
    val enabled: Property<Boolean>

    /**
     * Determines if operations using this transfer could be done in parallel.
     */
    val parallelable: Provider<Boolean>
}
