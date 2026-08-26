package com.kmpgaraj.kmpescposprintersampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import dev.escpos.sample.data.platform.PlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(context = PlatformContext(applicationContext))
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(context = PlatformContext(LocalContext.current))
}