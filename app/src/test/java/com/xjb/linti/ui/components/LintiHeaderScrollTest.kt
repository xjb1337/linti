package com.xjb.linti.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.ui.theme.LintiPageBackground
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class LintiHeaderScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedHeaderCollapsesAndKeepsBackAction() {
        var wentBack = false
        compose.setContent {
            LintiTheme {
                LintiPageBackground {
                    Column(Modifier.fillMaxSize()) {
                        LintiPageHeader("虚构页面", subtitle = "状态", onBack = { wentBack = true })
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("scroll-content")) {
                            Spacer(Modifier.height(2000.dp))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("scroll-content").performTouchInput { swipeUp() }
        compose.onNodeWithTag("linti-header-compact").assertIsDisplayed()
        val titleCenter = compose.onNodeWithText("虚构页面").fetchSemanticsNode().boundsInRoot.center.x
        val pageCenter = compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(kotlin.math.abs(titleCenter - pageCenter) < 1f)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertTrue(wentBack) }
        compose.onNodeWithTag("scroll-content").performTouchInput { swipeDown() }
        compose.onNodeWithTag("linti-header-compact").assertDoesNotExist()
    }
}
