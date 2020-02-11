package com.cognifide.gradle.common.tasks

import com.cognifide.gradle.common.CommonDefaultTask
import com.cognifide.gradle.common.CommonException
import com.cognifide.gradle.common.CommonTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.Serializable

@Suppress("TooManyFunctions")
class TaskFacade(val project: Project) : Serializable {

    val tests get() = getAll(Test::class.java)

    val checks get() = listOf(get(LifecycleBasePlugin.CHECK_TASK_NAME, Task::class.java))

    // Generic API & internals

    fun pathed(path: String): TaskProvider<Task> {
        val projectPath = path.substringBeforeLast(":", project.path).ifEmpty { ":" }
        val taskName = path.substringAfterLast(":")
        val subproject = project.project(projectPath)

        return try {
            subproject.tasks.named(taskName)
        } catch (e: UnknownTaskException) {
            throw composeException(taskName, project = subproject)
        }
    }

    fun pathed(paths: Collection<Any>): List<TaskProvider<out Task>> {
        return paths.map { path ->
            when (path) {
                is String -> pathed(path)
                is TaskProvider<*> -> path
                else -> throw IllegalArgumentException("Illegal task argument: $path")
            }
        }
    }

    fun named(name: String): TaskProvider<Task> {
        return try {
            project.tasks.named(name)
        } catch (e: UnknownTaskException) {
            throw composeException(name)
        }
    }

    inline fun <reified T : Task> named(name: String, noinline configurer: T.() -> Unit = {}): TaskProvider<T> {
        return named(name, T::class.java, configurer)
    }

    fun <T : Task> named(name: String, type: Class<T>, configurer: T.() -> Unit): TaskProvider<T> {
        try {
            return project.tasks.named(name, type, configurer)
        } catch (e: UnknownTaskException) {
            throw composeException(name)
        }
    }

    inline fun <reified T : Task> typed(noinline configurer: T.() -> Unit) {
        typed(T::class.java, configurer)
    }

    fun <T : Task> typed(type: Class<T>, configurer: T.() -> Unit) {
        project.tasks.withType(type).configureEach(configurer)
    }

    inline fun <reified T : Task> registerOrConfigure(name: String, noinline configurer: T.() -> Unit = {}): TaskProvider<T> {
        return try {
            project.tasks.named(name, T::class.java, configurer)
        } catch (e: UnknownTaskException) {
            register(name, T::class.java, configurer)
        }
    }

    inline fun <reified T : Task> registerOrConfigure(vararg names: String, noinline configurer: T.() -> Unit = {}) {
        names.forEach { registerOrConfigure(it, configurer) }
    }

    inline fun <reified T : Task> register(name: String, noinline configurer: T.() -> Unit = {}): TaskProvider<T> {
        return register(name, T::class.java, configurer)
    }

    fun <T : Task> register(name: String, clazz: Class<T>, configurer: T.() -> Unit = {}): TaskProvider<T> {
        with(project) {
            val provider = tasks.register(name, clazz, configurer)

            afterEvaluate { provider.configure { if (it is CommonTask) it.projectEvaluated() } }
            gradle.projectsEvaluated { provider.configure { if (it is CommonTask) it.projectsEvaluated() } }
            gradle.taskGraph.whenReady { graph -> provider.configure { if (it is CommonTask) it.taskGraphReady(graph) } }

            return provider
        }
    }

    fun register(name: String, configurer: CommonDefaultTask.() -> Unit) {
        register(name, CommonDefaultTask::class.java, configurer)
    }

    inline fun <reified T : Task> get(path: String) = get(path, T::class.java)

    @Suppress("unchecked_cast")
    @Synchronized
    fun <T : Task> get(path: String, type: Class<T>): T {
        val task = if (path.contains(":")) {
            project.tasks.findByPath(path)
        } else {
            project.tasks.findByName(path)
        }

        if (task == null || !type.isInstance(task)) {
            throw composeException(path, type)
        }

        return task as T
    }

    inline fun <reified T : Task> getAll() = getAll(T::class.java)

    @Synchronized // TODO https://github.com/Cognifide/gradle-aem-plugin/issues/543
    fun <T : Task> getAll(type: Class<T>) = project.tasks.withType(type).toList()

    fun registerSequence(name: String, sequenceOptions: TaskSequence.() -> Unit) = registerSequence(name, {}, sequenceOptions)

    fun registerSequence(name: String, taskOptions: Task.() -> Unit, sequenceOptions: TaskSequence.() -> Unit): TaskProvider<Task> {
        return project.tasks.register(name) { task ->
            val options = TaskSequence().apply(sequenceOptions)

            task.dependsOn(options.dependentTasks).mustRunAfter(options.afterTasks)
            task.apply(taskOptions)

            val dependentTasks = pathed(options.dependentTasks)
            val afterTasks = pathed(options.afterTasks)

            if (dependentTasks.size > 1) {
                for (i in 1 until dependentTasks.size) {
                    val previous = dependentTasks[i - 1]
                    val current = dependentTasks[i]

                    current.configure { it.mustRunAfter(previous) }
                }
            }
            dependentTasks.forEach { dependentTask ->
                dependentTask.configure { it.mustRunAfter(afterTasks) }
            }
        }
    }

    private fun composeException(
        taskName: String,
        type: Class<*>? = null,
        cause: Exception? = null,
        project: Project = this.project
    ): CommonException {
        val msg = if (type != null) {
            "${project.displayName.capitalize()} does not have task '$taskName' of type '$type'. Ensure correct plugins applied."
        } else {
            "${project.displayName.capitalize()} does not have task '$taskName'. Ensure correct plugins applied."
        }

        return if (cause != null) {
            CommonException(msg, cause)
        } else {
            CommonException(msg)
        }
    }
}
