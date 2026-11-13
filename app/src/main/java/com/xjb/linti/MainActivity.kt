package com.xjb.linti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.xjb.linti.ui.LintiRoot
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.theme.LintiTheme
import com.xjb.linti.ui.theme.LocalLintiPalette

class MainActivity : ComponentActivity() {
    private val viewModel: LintiViewModel by viewModels {
        val app = application as LintiApp
        LintiViewModel.Factory(app, app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LintiTheme {
                Surface(color = LocalLintiPalette.current.background) {
                    LintiRoot(viewModel)
                }
            }
        }
    }
}
