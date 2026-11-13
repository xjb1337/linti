package com.xjb.linti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.xjb.linti.ui.LintiRoot
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.theme.Background
import com.xjb.linti.ui.theme.LintiTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LintiViewModel by viewModels {
        val app = application as LintiApp
        LintiViewModel.Factory(app, app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LintiTheme {
                Surface(color = Background) {
                    LintiRoot(viewModel)
                }
            }
        }
    }
}
