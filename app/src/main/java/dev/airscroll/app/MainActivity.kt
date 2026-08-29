package dev.airscroll.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dev.airscroll.app.ui.AirScrollApp
import dev.airscroll.core.designsystem.AirScrollTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AirScrollTheme {
                AirScrollApp()
            }
        }
    }
}
