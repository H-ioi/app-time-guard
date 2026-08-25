package com.apptime.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.apptime.guard.ui.AppViewModel
import com.apptime.guard.ui.child.ChildScreen
import com.apptime.guard.ui.onboarding.OnboardingFlow
import com.apptime.guard.ui.parent.ParentScreen
import com.apptime.guard.ui.parent.PinDialog
import com.apptime.guard.ui.theme.AppTimeTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTimeTheme {
                AppRoot(viewModel)
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = AppTimeApp.get(context)
    val onboarded by app.settings.onboarded.collectAsState(initial = false)
    val isParent by viewModel.isParent.collectAsState()
    var showPin by rememberSaveable { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf("enter_parent") }

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            !onboarded -> {
                OnboardingFlow(
                    onFinished = { viewModel.refresh() }
                )
            }
            isParent -> {
                ParentScreen(
                    onExit = { viewModel.exitParentMode() }
                )
            }
            else -> {
                ChildScreen(
                    viewModel = viewModel,
                    onLongPressClock = {
                        pinTarget = "enter_parent"
                        showPin = true
                    }
                )
                if (showPin) {
                    PinDialog(
                        title = "家长验证",
                        verify = true,
                        onDismiss = { showPin = false },
                        onSuccess = {
                            showPin = false
                            viewModel.enterParentMode()
                        }
                    )
                }
            }
        }
    }
}
