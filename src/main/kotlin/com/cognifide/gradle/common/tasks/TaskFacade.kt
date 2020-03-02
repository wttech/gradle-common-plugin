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

class TaskFacade(val project: Project) : Serializable {

    val tests get() = getAll<Test>()

    val checks get() = listOf(get<Task>(LifecycleBasePlugin.CHECK_TASK_NAME))

    // Generic API & internals

    inline fun <reified T : Task> pathed(path: String): TaskProvider<T> {
        val projectPath = path.substringBeforeLast(":", project.path).ifEmpty { ":" }
        val taskName = path.substringAfterLast(":")
        val subproject = project.project(projectPath)

        return try {
            subproject.tasks.named(taskName, T::class.java)
        } catch (e: UnknownTaskException) {
            throw composeException(taskName, project = subproject)
        }
    }

    fun pathed(paths: Collection<Any>): List<TaskProvider<out Task>> {
        return paths.map { path ->
            when (path) {
                is String -> pathed<Task>(path)
                is TaskProvider<*> -> path
                else -> throw IllegalArgumentException("Illegal task argument: $path")
            }
        }
    }

    inline fun <reified T : Task> named(name: String, noinline configurer: T.() -> Unit = {}): TaskProvider<T> {
        try {
            return project.tasks.named(name, T::class.java, configurer)
        } catch (e: UnknownTaskException) {
            throw composeException(name)
        }
    }

    inline fun <reified T : Task> typed(noinline configurer: T.() -> Unit) {
        project.tasks.withType(T::class.java).configureEach(configurer)
    }

    inline fun <reified T : Task> register(name: String, noinline configurer: T.() -> Unit = {}): TaskProvider<T> {
        with(project) {
            val provider = tasks.register(name, T::class.java, configurer)

            afterEvaluate { provider.configure { if (it is CommonTask) it.projectEvaluated() } }
            gradle.projectsEvaluated { provider.configure { if (it is CommonTask) it.projectsEvaluated() } }
            gradle.taskGraph.whenReady { graph -> provider.configure { if (it is CommonTask) it.taskGraphReady(graph) } }

            return provider
        }
    }

    fun define(name: String, configurer: CommonDefaultTask.() -> Unit = {}) = register(name, configurer)

    inline fun <reified T : Task> get(path: String): T {
        val type = T::class.java
        val task = getSafely(path)

        if (task == null || !type.isInstance(task)) {
            throw composeException(path, type)
        }

        return task as T
    }

    inline fun <reified T : Task> getAll() = getAllSafely(T::class.java)

    @Synchronized // TODO https://github.com/Cognifide/gradle-aem-plugin/issues/543
    fun getSafely(path: String): Task? = when {
        path.contains(":") -> project.tasks.findByPath(path)
        else -> project.tasks.findByName(path)
    }

    @Synchronized // TODO https://github.com/Cognifide/gradle-aem-plugin/issues/543
    fun <T : Task> getAllSafely(type: Class<T>): List<T> {
        return project.tasks.withType(type).toList()
    }

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

    fun composeException(
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

fun <T : Task> TaskProvider<T>.configureApply(action: T.() -> Unit) = configure(action)
