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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

val FrozenWater = Color(0xFFD7FFF1)
val MauveBark = Color(0xFF664E4C)
val CarbonBlack = Color(0xFF172121)
val AirForceBlue = Color(0xFF6A8D92)
val PaleOak = Color(0xFFE2D0B6)
val PureWhite = Color(0xFFFFFFFF)
val Yellow = Color(0xFFEEBC)

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
}@Composable
fun BatteryProgress(batteryLevel: Int) {
    val progress = batteryLevel / 100f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .padding(16.dp)
    ) {
        // Background circle (always full, white)
        CircularProgressIndicator(
            progress = 1f,
            strokeWidth = 12.dp,
            color = PureWhite,
            modifier = Modifier.fillMaxSize()
        )

        // Foreground circle (fills with AirForceBlue)
        CircularProgressIndicator(
            progress = progress,
            strokeWidth = 12.dp,
            color = AirForceBlue,
            modifier = Modifier.fillMaxSize()
        )

        // Center text
        Text(
            text = "Phone Battery\n${batteryLevel}%",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PaleOak,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DashboardScreen(service: SdServer?, isBound: Boolean) {
    // State variables to hold data for the UI
    var heartRate by remember { mutableStateOf("--") }
    var alarmState by remember { mutableStateOf("Waiting...") }
    var batteryLevel by remember { mutableStateOf("--%") }
    var batteryPc by remember { mutableStateOf(0) }
    var statusColor by remember { mutableStateOf(Color.Gray) }
    var watchBatteryLevel by remember { mutableStateOf("--%") }

    // The Polling Loop
    LaunchedEffect(isBound) {
        while (isActive) {
            if (isBound && service != null && service.mSdData != null) {
                // Pull data from the Java object
                heartRate = String.format("%.1f BPM", service.mSdData.mHR)

                batteryLevel = "${service.mSdData.phoneBatteryPc}%"
                batteryPc = service.mSdData.phoneBatteryPc

                watchBatteryLevel = "${service.mSdData.batteryPc}%"

                // Logic to determine status text and color
                val state = service.mSdData.alarmState
                alarmState = service.mSdData.alarmPhrase ?: "OK"

                // === FIX HERE: Add .toInt() to ensure we compare Int to Int ===
                statusColor = when (state.toInt()) {
                    2 -> Color(0xFFFF5252) // Alarm (Red)
                    1 -> Yellow // Warning (yellow)
                    else -> PaleOak // OK (Green)
                }
            }
            delay(1000) // Refresh every second
        }
    }

    // The Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp)) // 👈 Pushes everything down
        Text(
            text = "SeizeWatch",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PaleOak)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Current Status", color = CarbonBlack, fontSize = 16.sp)
                Text(
                    text = alarmState,
                    color = CarbonBlack,
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
                title = "Watch Battery",
                value = watchBatteryLevel,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 🔋 Battery Circle BELOW the data cards
        BatteryProgress(batteryLevel = batteryPc)

    }
}

@Composable
fun DataCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AirForceBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = PureWhite, fontSize = 14.sp)
            Text(text = value, color = PureWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}