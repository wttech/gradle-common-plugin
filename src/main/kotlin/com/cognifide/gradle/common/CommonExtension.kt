package com.cognifide.gradle.common

import com.cognifide.gradle.common.file.FileWatcher
import com.cognifide.gradle.common.file.resolver.FileResolver
import com.cognifide.gradle.common.file.transfer.FileTransferManager
import com.cognifide.gradle.common.file.transfer.http.HttpFileTransfer
import com.cognifide.gradle.common.file.transfer.sftp.SftpFileTransfer
import com.cognifide.gradle.common.file.transfer.smb.SmbFileTransfer
import com.cognifide.gradle.common.http.HttpClient
import com.cognifide.gradle.common.notifier.NotifierFacade
import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.common.utils.Patterns
import com.cognifide.gradle.common.build.*
import com.cognifide.gradle.common.tasks.TaskFacade
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.api.provider.Provider
import java.io.File

open class CommonExtension(val project: Project) {

    val logger = project.logger

    /**
     * Allows to read project property specified in command line and system property as a fallback.
     */
    val prop = PropertyParser(project)

    /**
     * Reduces boilerplate related to lazy configuration API
     */
    val obj = ObjectFactory(project)

    /**
     * Accessor for internal Gradle services.
     */
    val services = ServiceAccessor(project)

    /**
     * Define settings for file transfer facade which allows to perform basic file operations on remote servers
     * like uploading and downloading files.
     *
     * Supports multiple protocols: HTTP, SFTP, SMB and other supported by JVM.
     */
    val fileTransfer = FileTransferManager(this)

    /**
     * Configures file transfer facade.
     */
    fun fileTransfer(options: FileTransferManager.() -> Unit) {
        fileTransfer.apply(options)
    }

    /**
     * Provides API for displaying interactive notification during running build tasks.
     */
    val notifier = NotifierFacade.of(this)

    fun notifier(configurer: NotifierFacade.() -> Unit) {
        notifier.apply(configurer)
    }

    val tasks = TaskFacade(project)

    /**
     * Allows to register tasks with hooks working nicely with task configuration avoidance.
     */
    fun tasks(configurer: TaskFacade.() -> Unit) {
        tasks.apply(configurer)
    }

    /**
     * Allows easily provide artifact in a lazy way (e.g via task provider).
     *
     * @see <https://github.com/gradle/gradle/issues/7958>
     */
    fun <T: Any> publicationArtifact(provider: Provider<T>) = LazyPublishArtifact(provider)

    /**
     * Allows easily provide artifact in a lazy way (e.g via task provider).
     *
     * @see <https://github.com/gradle/gradle/issues/7958>
     */
    fun publicationArtifact(taskPath: String) = publicationArtifact(project.tasks.named(taskPath))

    /**
     * Show asynchronous 0 indicator with percentage while performing some action.
     */
    fun <T> progress(total: Int, action: ProgressIndicator.() -> T): T = progress(total.toLong(), action)

    /**
     * Show asynchronous progress indicator with percentage while performing some action.
     */
    fun <T> progress(total: Long, action: ProgressIndicator.() -> T): T {
        return ProgressIndicator(project).apply { this.total = total }.launch(action)
    }

    fun <T> progress(action: ProgressIndicator.() -> T) = progressIndicator(action)

    /**
     * Show asynchronous progress indicator while performing some action.
     */
    fun <T> progressIndicator(action: ProgressIndicator.() -> T): T = ProgressIndicator(project).launch(action)

    /**
     * Show synchronous progress logger while performing some action.
     */
    fun <T> progressLogger(action: ProgressLogger.() -> T): T = ProgressLogger.of(project).launch(action)

    /**
     * Grab user input interactively.
     */
    val userInput by lazy { ServiceAccessor(project).get<UserInputHandler>() }

    /**
     * Wait some time after performing asynchronous operation.
     */
    fun waitFor(time: Long) = progressCountdown(time)

    /**
     * Show synchronous progress countdown / time to wait after performing asynchronous operation.
     */
    fun progressCountdown(time: Long) = progressCountdown { this.time = time }

    /**
     * Show synchronous progress countdown / time to wait after performing asynchronous operation.
     */
    fun progressCountdown(options: ProgressCountdown.() -> Unit) = ProgressCountdown(project).apply(options).run()

    /**
     * Determine temporary directory for particular service (any name).
     */
    fun temporaryFile(path: String): File = project.buildDir.resolve(path)

    /**
     * Predefined temporary directory.
     */
    val temporaryDir: File get() = temporaryFile(TEMPORARY_DIR)

    /**
     * Factory method for configuration object determining how operation should be retried.
     */
    fun retry(options: Retry.() -> Unit = {}) = Retry(this).apply(options)

    /**
     * React on file changes under configured directories.
     */
    fun watchFiles(options: FileWatcher.() -> Unit) {
        FileWatcher(this).apply(options).start()
    }

    /**
     * Resolve single file from defined repositories or by using defined file transfers.
     */
    fun resolveFile(value: Any) = resolveFile { get(value) }

    /**
     * Resolve single file from defined repositories or by using defined file transfers.
     */
    fun resolveFile(options: FileResolver.() -> Unit) = resolveFiles(options).firstOrNull()
            ?: throw CommonException("File not resolved!")

    /**
     * Resolve files from defined repositories or by using defined file transfers.
     */
    fun resolveFiles(vararg values: Any): List<File> = resolveFiles(values.asIterable())

    /**
     * Resolve files from defined repositories or by using defined file transfers.
     */
    fun resolveFiles(values: Iterable<Any>): List<File> = resolveFiles { values.forEach { get(it) } }

    /**
     * Resolve files from defined repositories or by using defined file transfers.
     */
    fun resolveFiles(options: FileResolver.() -> Unit): List<File> = FileResolver(this).apply(options).files

    /**
     * Perform any HTTP requests to external endpoints.
     */
    fun <T> http(consumer: HttpClient.() -> T) = HttpClient(this).run(consumer)

    /**
     * Download files using HTTP protocol using custom settings.
     */
    fun <T> httpFile(consumer: HttpFileTransfer.() -> T) = fileTransfer.factory.http(consumer)

    /**
     * Transfer files using over SFTP protocol using custom settings.
     */
    fun <T> sftpFile(consumer: SftpFileTransfer.() -> T) = fileTransfer.factory.sftp(consumer)

    /**
     * Transfer files using over SMB protocol using custom settings.
     */
    fun <T> smbFile(consumer: SmbFileTransfer.() -> T) = fileTransfer.factory.smb(consumer)

    // Utilities (to use without imports)

    val parallel = Parallel

    val formats = Formats

    val patterns = Patterns

    val buildScope = BuildScope.of(project)

    companion object {

        const val NAME = "common"

        const val TEMPORARY_DIR = "tmp"

        private val PLUGIN_IDS = listOf(CommonPlugin.ID)

        fun of(project: Project): CommonExtension {
            return project.extensions.findByType(CommonExtension::class.java)
                    ?: throw CommonException("${project.displayName.capitalize()} must have at least one of following plugins applied: $PLUGIN_IDS")
        }
    }
}

val Project.common get() = CommonExtension.of(project)

@Synchronized
fun Project.pluginProject(id: String): Project? = when {
    plugins.hasPlugin(id) -> this
    else -> rootProject.allprojects.firstOrNull { it.plugins.hasPlugin(id) }
}
