package app.altitude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.altitude.ui.AltitudeRoot
import app.altitude.ui.AltitudeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AltitudeViewModel = viewModel()
            AltitudeRoot(viewModel)
        }
    }
}
