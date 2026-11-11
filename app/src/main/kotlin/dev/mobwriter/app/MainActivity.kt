package dev.mobwriter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.mobwriter.app.ui.MobWriterApp
import dev.mobwriter.app.ui.MobWriterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobWriterTheme {
                MobWriterApp()
            }
        }
    }
}
