package com.aurora.player

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aurora.player.audio.AudioAnalyzer
import com.aurora.player.media.NowPlaying
import com.aurora.player.media.Perms
import com.aurora.player.settings.PresetStore
import com.aurora.player.settings.SettingsStore
import com.aurora.player.ui.PermissionScreen
import com.aurora.player.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        val store = SettingsStore(applicationContext)
        val presets = PresetStore(applicationContext, store)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color.White,
                    onPrimary = Color.Black,
                    secondary = Color(0xFF00E5FF),
                    background = Color.Black,
                    surface = Color(0xFF121218),
                )
            ) {
                AppRoot(store, presets)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
fun AppRoot(store: SettingsStore, presets: PresetStore) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aurora_app", Context.MODE_PRIVATE) }
    val analyzer = remember { AudioAnalyzer() }
    var notifOk by remember { mutableStateOf(Perms.hasNotificationAccess(context)) }
    var micOk by remember { mutableStateOf(Perms.hasMic(context)) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifOk = Perms.hasNotificationAccess(context)
        micOk = Perms.hasMic(context)
        if (notifOk) NowPlaying.attach(context)
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micOk = granted
    }
    val requestMic: () -> Unit = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    // L'analisi audio gira solo quando l'app è visibile
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(micOk, lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> {
                    if (micOk) analyzer.start()
                }
                Lifecycle.Event.ON_STOP -> analyzer.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            analyzer.stop()
        }
    }

    if (!onboarded) {
        PermissionScreen(notifOk, micOk, requestMic) {
            onboarded = true
            prefs.edit().putBoolean("onboarded", true).apply()
        }
    } else {
        PlayerScreen(store, presets, analyzer, micOk, requestMic)
    }
}
