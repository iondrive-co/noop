package io.iondrive.nop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.iondrive.nop.ui.App
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.ui.ComponentStyling
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) = application {
    val projectPath: Path = if (args.isNotEmpty()) Paths.get(args[0]) else Paths.get(".").toAbsolutePath().normalize()

    Window(
        onCloseRequest = ::exitApplication,
        title = "nop — ${projectPath.fileName}",
    ) {
        IntUiTheme(
            theme = JewelTheme.darkThemeDefinition(),
            styling = ComponentStyling.default(),
        ) {
            App(projectPath)
        }
    }
}
