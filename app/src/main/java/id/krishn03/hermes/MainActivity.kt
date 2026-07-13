package id.krishn03.hermes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import id.krishn03.hermes.ui.ChatViewModel
import id.krishn03.hermes.ui.HermesApp
import id.krishn03.hermes.ui.theme.HermesTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HermesTheme {
                HermesApp(viewModel)
            }
        }
    }
}
