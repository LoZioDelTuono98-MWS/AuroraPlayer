package com.aurora.player.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionScreen(notifOk: Boolean, micOk: Boolean, onRequestMic: () -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1A0B3D), Color(0xFF0B0716), Color(0xFF002B36))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Aurora", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
            Text(
                "Il tuo visualizzatore musicale a tutto schermo. Funziona con Spotify, SimpMusic e qualsiasi altro player del telefono.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
            )
            StepCard(
                n = 1,
                title = "Brano in riproduzione",
                body = "Serve per mostrare titolo, artista e copertina e per i tasti indietro, pausa e avanti. Aurora non legge il contenuto delle tue notifiche.",
                done = notifOk,
                action = "Apri impostazioni",
            ) {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            if (!notifOk) {
                Text(
                    "Se su Android 13 o superiore l'interruttore è grigio: apri Info app, tocca ⋮ in alto a destra, scegli \"Consenti impostazioni con restrizioni\" e riprova.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    }
                }) { Text("Apri Info app", color = Color.White) }
            }
            StepCard(
                n = 2,
                title = "Audio reattivo",
                body = "Permette di analizzare in tempo reale le frequenze di ciò che ascolti, così le grafiche ballano a ritmo. Nulla viene registrato o salvato.",
                done = micOk,
                action = "Consenti",
                onAction = onRequestMic,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) { Text(if (notifOk) "Inizia" else "Continua comunque", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(18.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (done) Color(0xFF00E5A0) else Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Rounded.Check, null, tint = Color.Black)
            else Text("$n", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            if (!done) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color.Black),
                ) { Text(action) }
            }
        }
    }
}
