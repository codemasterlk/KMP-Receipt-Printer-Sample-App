package com.kmpgaraj.kmpescposprintersampleapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ESC/POS diagnostic sample (desktop)",
    ) {
        App(context = Unit)
    }
}