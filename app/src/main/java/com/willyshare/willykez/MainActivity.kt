package com.willyshare.willykez

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.compose.viewModel
import com.willyshare.willykez.ui.PulseViewModel
import com.willyshare.willykez.ui.common.SparkPredictiveBackHandler
import com.willyshare.willykez.ui.screens.DashboardScreen
import com.willyshare.willykez.ui.screens.HistoryScreen
import com.willyshare.willykez.ui.screens.MyQrScreen
import com.willyshare.willykez.ui.screens.OnboardingScreen
import com.willyshare.willykez.ui.screens.ReceiveScreen
import com.willyshare.willykez.ui.screens.ScanQrScreen
import com.willyshare.willykez.ui.screens.SelectFilesScreen
import com.willyshare.willykez.ui.screens.SendScreen
import com.willyshare.willykez.ui.screens.SettingsScreen
import com.willyshare.willykez.ui.screens.SplashScreen
import com.willyshare.willykez.ui.screens.TransferringScreen
import com.willyshare.willykez.ui.theme.LocalReducedMotion
import com.willyshare.willykez.ui.theme.MyApplicationTheme
import com.willyshare.willykez.ui.theme.reducedMotionAwareSpec
import com.willyshare.willykez.util.ShareIntentHandler
import com.willyshare.willykez.util.isShareIntent

/** Screens that represent a bottom-nav root/tab: switching to one of these resets the back stack. */
private val ROOT_ROUTES = setOf("dashboard", "history", "settings")

private const val PREFS_NAME = "sparks_prefs"
private const val KEY_ONBOARDED = "has_onboarded"

class MainActivity : ComponentActivity() {
  /** Holds the latest incoming share intent so Compose can react to it once, via LaunchedEffect. */
  private val pendingShareIntent = mutableStateOf<Intent?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    com.willyshare.willykez.util.NotificationHelper.ensureChannels(this)
    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    if (intent?.isShareIntent() == true) pendingShareIntent.value = intent

    setContent {
      MyApplicationTheme {
        // Real back-stack: every navigate() call pushes, hardware/gesture back pops.
        // rememberSaveable (not plain remember) so that if Android reclaims this process
        // while backgrounded, the restored Activity lands back on the same screen instead
        // of cold-starting at the splash screen again.
        val backStack = rememberSaveable(
          saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
          )
        ) {
          val startRoute = if (pendingShareIntent.value != null) "dashboard" else "splash"
          mutableStateListOf(startRoute)
        }
        val currentScreen = backStack.last()
        val viewModel: PulseViewModel = viewModel()
        val incomingShare by remember { pendingShareIntent }

        fun goBack() {
          if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
        }

        fun navigate(route: String) {
          when {
            route == currentScreen -> Unit
            // Tapping a "back" affordance that leads to the screen already just under
            // the top of the stack should pop, not push a duplicate entry.
            backStack.size >= 2 && backStack[backStack.size - 2] == route -> goBack()
            route in ROOT_ROUTES -> {
              backStack.clear()
              backStack.add(route)
            }
            else -> backStack.add(route)
          }
        }

        // Another app shared files into Sparks ("Share to" from Gallery/Files/etc.):
        // stash them and jump straight to picking who to send them to.
        LaunchedEffect(incomingShare) {
          val shareIntent = incomingShare ?: return@LaunchedEffect
          val files = ShareIntentHandler.resolveSharedFiles(this@MainActivity, shareIntent)
          if (files.isNotEmpty()) {
            viewModel.setPendingSharedFiles(files)
            navigate("send")
          }
          pendingShareIntent.value = null
        }

        // System/gesture back: pop our own stack instead of the default (which would
        // otherwise skip screens or exit unexpectedly). Using the predictive variant so
        // Android 14+ shows the gesture back-preview instead of a hard cut.
        SparkPredictiveBackHandler(enabled = backStack.size > 1) { goBack() }

        // Direction-aware transition: pushing a screen slides the new one in from
        // the right (old screen drifts left), popping slides in from the left: a
        // real "forward/back" feel instead of a flat crossfade for every change.
        // Switching between bottom-nav tabs (root routes) just fades — no
        // directionality implied since tabs are peers, not a stack relationship.
        // All of it collapses to an instant cut when the user has reduced motion on.
        var previousStackSize by remember { mutableIntStateOf(backStack.size) }
        val isRootSwitch = currentScreen in ROOT_ROUTES && backStack.size == 1
        val isPush = !isRootSwitch && backStack.size > previousStackSize
        SideEffect { previousStackSize = backStack.size }
        val reducedMotion = LocalReducedMotion.current
        val rootFadeInSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
          reducedMotionAwareSpec(tween(180))
        val rootFadeOutSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
          reducedMotionAwareSpec(tween(120))
        val slideSpec: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> =
          reducedMotionAwareSpec(tween(280))
        val enterFadeSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
          reducedMotionAwareSpec(tween(280))
        val exitFadeSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
          reducedMotionAwareSpec(tween(200))

        AnimatedContent(
          targetState = currentScreen,
          transitionSpec = {
            when {
              reducedMotion ->
                EnterTransition.None togetherWith ExitTransition.None
              isRootSwitch ->
                fadeIn(rootFadeInSpec) togetherWith fadeOut(rootFadeOutSpec)
              isPush ->
                // Push: new screen slides in from right, old screen exits to the left
                (slideInHorizontally(slideSpec) { it } + fadeIn(enterFadeSpec)) togetherWith
                  (slideOutHorizontally(slideSpec) { -it / 3 } + fadeOut(exitFadeSpec))
              else ->
                // Pop: new screen slides in from left, old screen exits to the right
                (slideInHorizontally(slideSpec) { -it } + fadeIn(enterFadeSpec)) togetherWith
                  (slideOutHorizontally(slideSpec) { it / 3 } + fadeOut(exitFadeSpec))
            }
          },
          label = "screen_transition",
        ) { screen ->
          when (screen) {
            "splash" -> SplashScreen(
              onFinish = {
                val nextRoute = if (prefs.getBoolean(KEY_ONBOARDED, false)) "dashboard" else "onboarding"
                navigate(nextRoute)
              }
            )
            "onboarding" -> OnboardingScreen(
              onGetStarted = {
                prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                navigate("dashboard")
              }
            )
            "dashboard" -> DashboardScreen(viewModel = viewModel, onNavigate = ::navigate)
            "send" -> SendScreen(viewModel = viewModel, onNavigate = ::navigate)
            "receive" -> ReceiveScreen(viewModel = viewModel, onNavigate = ::navigate)
            "my_qr" -> MyQrScreen(viewModel = viewModel, onNavigate = ::navigate)
            "scan_qr" -> ScanQrScreen(viewModel = viewModel, onNavigate = ::navigate)
            "select" -> SelectFilesScreen(viewModel = viewModel, onNavigate = ::navigate, onGoBack = ::goBack)
            "browse" -> com.willyshare.willykez.ui.screens.BrowseFilesScreen(viewModel = viewModel, onNavigate = ::navigate)
            "transfer" -> TransferringScreen(viewModel = viewModel, onNavigate = ::navigate)
            "history" -> HistoryScreen(viewModel = viewModel, onNavigate = ::navigate)
            "settings" -> SettingsScreen(viewModel = viewModel, onNavigate = ::navigate)
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.isShareIntent()) pendingShareIntent.value = intent
  }

  companion object {
    /** Plain "bring the app to front" intent, used by notification taps. */
    fun newIntent(context: Context): Intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
  }
}
