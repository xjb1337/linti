package com.xjb.linti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            val app = application as LintiApp
            val appearanceMode by app.container.quizPreferences.appearanceMode.collectAsStateWithLifecycle()
            val colorTheme by app.container.quizPreferences.colorTheme.collectAsStateWithLifecycle()
            LintiTheme(
                appearanceMode = appearanceMode,
                colorTheme = colorTheme,
            ) {
                Surface(color = LocalLintiPalette.current.background) {
                    LintiRoot(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}
