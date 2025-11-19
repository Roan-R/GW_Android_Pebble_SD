package uk.org.openseizuredetector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class ComposeMainActivity : ComponentActivity() {

    // 1. Hold a reference to the Java Service
    private var sdService: SdServer? = null
    private var isBound by mutableStateOf(false)

    // 2. The Connection Bridge
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SdServer.SdBinder
            sdService = binder.service
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            sdService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 3. Bind to the existing Service (Started by StartupActivity)
        val intent = Intent(this, SdServer::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            MaterialTheme {
                // Pass the service reference to the UI
                DashboardScreen(sdService, isBound)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun DashboardScreen(service: SdServer?, isBound: Boolean) {
    // State variables to hold data for the UI
    var heartRate by remember { mutableStateOf("--") }
    var alarmState by remember { mutableStateOf("Waiting...") }
    var batteryLevel by remember { mutableStateOf("--%") }
    var dataSource by remember { mutableStateOf("None") }
    var statusColor by remember { mutableStateOf(Color.Gray) }

    // The Polling Loop
    LaunchedEffect(isBound) {
        while (isActive) {
            if (isBound && service != null && service.mSdData != null) {
                // Pull data from the Java object
                heartRate = String.format("%.1f BPM", service.mSdData.mHR)
                dataSource = service.mSdData.dataSourceName ?: "Unknown"
                batteryLevel = "${service.mSdData.phoneBatteryPc}%"

                // Logic to determine status text and color
                val state = service.mSdData.alarmState
                alarmState = service.mSdData.alarmPhrase ?: "OK"

                // === FIX HERE: Add .toInt() to ensure we compare Int to Int ===
                statusColor = when (state.toInt()) {
                    2 -> Color(0xFFFF5252) // Alarm (Red)
                    1 -> Color(0xFFFFC107) // Warning (Amber)
                    else -> Color(0xFF4CAF50) // OK (Green)
                }
            }
            delay(1000) // Refresh every second
        }
    }

    // The Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Seizure Detector",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = statusColor)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Current Status", color = Color.White, fontSize = 16.sp)
                Text(
                    text = alarmState,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Data Grid
        Row(modifier = Modifier.fillMaxWidth()) {
            DataCard(
                title = "Heart Rate",
                value = heartRate,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            DataCard(
                title = "Phone Battery",
                value = batteryLevel,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DataCard(
            title = "Data Source",
            value = dataSource,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun DataCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = Color.Gray, fontSize = 14.sp)
            Text(text = value, color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}