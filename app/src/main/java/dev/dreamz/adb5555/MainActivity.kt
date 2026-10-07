package dev.dreamz.adb5555

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

private data class Status(val paired: Boolean, val wireless: Boolean, val port: Boolean)

@Composable
private fun Screen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun read() = Status(AdbClient.isPaired(context), WirelessDebugging.isOn(context), AdbClient.isPortOpen())
    var status by remember { mutableStateOf(Status(false, false, false)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) { visible = true; onPauseOrDispose { visible = false } }
    // Port and Wireless debugging change outside the app; poll while the screen is showing.
    LaunchedEffect(visible) {
        while (visible) {
            status = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { read() }
            delay(1500)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startPairing(context) { message = it }
        else message = "Pairing needs notifications: the code is typed into one."
    }

    fun run(action: suspend () -> AdbClient.Outcome) {
        busy = true; message = null
        scope.launch {
            message = describe(action())
            status = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { read() }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ADB 5555", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Turns on ADB over TCP port 5555 (like adb tcpip 5555) without a computer.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRow("Paired with Wireless debugging", status.paired)
                StatusRow("Wireless debugging on", status.wireless)
                StatusRow("ADB on port 5555", status.port)
            }
        }

        Button(
            onClick = { run { AdbClient.get(context).enable() } },
            enabled = !busy && !status.port,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (status.port) "Port 5555 is on" else "Enable 5555")
        }
        if (status.port) {
            OutlinedButton(
                onClick = { run { AdbClient.get(context).disable() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Turn off (USB only)") }
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (status.paired) "Pair again" else "One-time setup: pair", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1. Tap Pair below. A notification appears.\n" +
                        "2. In Wireless debugging, tap \"Pair device with pairing code\".\n" +
                        "3. Leave that dialog open, pull down the notification shade and type only the 6-digit code " +
                        "into the notification. The app finds the port by itself.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) startPairing(context) { message = it } else notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text("Pair") }
                    TextButton(onClick = { context.startActivity(WirelessDebugging.settingsIntent()) }) {
                        Text("Open Wireless debugging")
                    }
                }
            }
        }

        Text(
            "After each reboot: turn on Wireless debugging, then tap Enable 5555 here or on the ADB 5555 " +
                "Quick Settings tile (edit your Quick Settings to add it).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun startPairing(context: android.content.Context, onMessage: (String) -> Unit) {
    PairingService.start(context)
    context.startActivity(WirelessDebugging.settingsIntent())
    onMessage("Tap \"Pair device with pairing code\", then type the code into the notification.")
}

@Composable
private fun StatusRow(label: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Filled green dot = yes, empty ring = no; spoken as Yes / No.
        val dot = Modifier.size(18.dp).semantics { contentDescription = if (on) "Yes" else "No" }
        if (on) Box(dot.background(Color(0xFF2E7D32), CircleShape))
        else Box(dot.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

fun describe(outcome: AdbClient.Outcome): String = when (outcome) {
    AdbClient.Outcome.Done -> "Done."
    AdbClient.Outcome.AlreadyOn -> "Port 5555 is already on."
    AdbClient.Outcome.NeedsPairing -> "This phone doesn't trust the app yet. Pair first (one time)."
    AdbClient.Outcome.WirelessDebuggingOff -> "Wireless debugging isn't on (it needs Wi-Fi). Turn it on and try again."
    is AdbClient.Outcome.Failed -> "Didn't work: ${outcome.message}"
}
