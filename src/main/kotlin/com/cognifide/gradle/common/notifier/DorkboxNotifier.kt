package com.cognifide.gradle.common.notifier

import dorkbox.notify.Notify
import dorkbox.notify.Theme
import org.apache.commons.lang3.StringUtils
import org.gradle.api.logging.LogLevel
import java.awt.Color
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@Suppress("TooGenericExceptionCaught")
class DorkboxNotifier(val facade: NotifierFacade, val configurer: Notify.() -> Unit) : Notifier {

    private val common = facade.common

    override fun notify(title: String, text: String, level: LogLevel, onClick: (Notify) -> Unit) {
        try {
            Notify.create().apply {
                text(DORKBOX_DARK_LIGHT_THEME)
                hideAfter(TimeUnit.SECONDS.toMillis(DORKBOX_HIDE_AFTER_SECONDS).toInt())
                assignImage(level)

                configurer()

                title(title)
                text(StringUtils.replace(text, "\n", "<br>"))
                onAction(onClick)

                show()
            }
        } catch (e: Exception) {
            common.logger.debug("Cannot show system notification", e)
        }
    }

    private fun Notify.assignImage(level: LogLevel) = try {
        when (level) {
            LogLevel.WARN, LogLevel.ERROR -> {
                val customIconFailure = facade.iconFailure.get().asFile
                if (customIconFailure.exists()) {
                    customImage(customIconFailure)
                } else {
                    typedImage("error.png")
                }
            }
            else -> {
                val customIconSuccess = facade.iconSuccess.get().asFile
                if (customIconSuccess.exists()) {
                    customImage(customIconSuccess)
                } else {
                    typedImage("info.png")
                }
            }
        }
    } catch (e: Exception) {
        common.logger.debug("Cannot assign notification image", e)
    }

    private fun Notify.typedImage(type: String) {
        image(ImageIO.read(javaClass.getResource("/notifier/$type").toURI().toURL()))
    }

    private fun Notify.customImage(file: File) {
        image(ImageIO.read(file.toURI().toURL()))
    }

    companion object {
        const val DORKBOX_HIDE_AFTER_SECONDS = 5L

        val DORKBOX_DARK_LIGHT_THEME = Theme(
            Notify.TITLE_TEXT_FONT,
            Notify.MAIN_TEXT_FONT,
            Color.DARK_GRAY,
            Color(168, 168, 168),
            Color(220, 220, 220),
            Color(220, 220, 220),
            Color.GRAY
        )
    }
}
