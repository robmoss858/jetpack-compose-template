package com.ferhatozcelik.jetpackcomposetemplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val btClient = ElmBluetoothClient(applicationContext)
        val viewModel = EscapeObdViewModel(btClient)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EscapeObdScreen(viewModel = viewModel)
            }
        }
    }
}
