package uk.org.openseizuredetector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.org.openseizuredetector.ui.theme.OpenSeizureDetectorTheme
import java.util.Timer
import java.util.TimerTask
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class MainActivity3 : ComponentActivity() {

    private val TAG = "MainActivityCompose"
    private val okColour = ComposeColor(0xFF2EC4B6)
    private val warnColour = ComposeColor(0xFFFF9F1C)
    private val alarmColour = ComposeColor(0xFFE71D36)

    private lateinit var mUtil: OsdUtil
    private lateinit var mConnection: SdServiceConnection
    private val serverStatusHandler = Handler(Looper.getMainLooper())
    private var mUiTimer: Timer? = null

    // Simplified UI State
    private val mainStatusText = mutableStateOf("...")
    private val mainStatusColor = mutableStateOf(warnColour)
    private val mainStatusDetails = mutableStateOf("Connecting to service...")
    private val watchBatteryPercentage = mutableStateOf(0f)
    private val phoneBatteryPercentage = mutableStateOf(0f)
    private val heartRateHistory = mutableStateListOf<Float>()
    private val watchConnectionText = mutableStateOf("Watch: disconnected")
    private val watchConnectionColor = mutableStateOf(warnColour)

    private val acceptAlarmButtonText = mutableStateOf("Accept Alarm")
    private val acceptAlarmButtonEnabled = mutableStateOf(false)
    private val manualAlarmButtonEnabled = mutableStateOf(true)
    private val muteAlarmButtonEnabled = mutableStateOf(false)


    // Menu and Dialog states
    private val versionText = mutableStateOf("")
    private val showMenu = mutableStateOf(false)
    private val showAboutDialog = mutableStateOf(false)
    private val showDataSharingDialog = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate()");

        mUtil = OsdUtil(applicationContext, serverStatusHandler)
        mConnection = SdServiceConnection(applicationContext)
        mUtil.writeToSysLogFile("MainActivity.onCreate() - Compose")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val actionStr = intent.action
        if (actionStr == "showDataSharingDialog") {
            showDataSharingDialog.value = true
        }

        setContent {
            OpenSeizureDetectorTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val actionStr = intent.action
        if (actionStr == "showDataSharingDialog") {
            showDataSharingDialog.value = true
        }
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart()");
        mUtil.writeToSysLogFile("MainActivity.onStart() - Compose")
        versionText.value = getString(R.string.AppTitleText) + " " + mUtil.getAppVersionName()

        mUtil.bindToServer(applicationContext, mConnection)
        
        mUiTimer = Timer()
        mUiTimer?.schedule(object : TimerTask() {
            override fun run() {
                updateServerStatus()
            }
        }, 0, 1000) // Poll UI every second
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop() - unbinding from server")
        mUtil.writeToSysLogFile("MainActivity.onStop() - Compose")
        mUtil.unbindFromServer(applicationContext, mConnection)
        mUiTimer?.cancel()
    }

    private fun updateServerStatus() {
        serverStatusHandler.post {
            if (!mConnection.mBound) {
                mainStatusText.value = "STOPPED"
                mainStatusColor.value = warnColour
                mainStatusDetails.value = "Service not connected."
                return@post
            }

            val server = mConnection.mSdServer
            val data = server.mSdData

            // Update Battery and Connection Status
            watchBatteryPercentage.value = if (data.batteryPc > 0) data.batteryPc / 100f else 0f
            phoneBatteryPercentage.value = data.phoneBatteryPc / 100f

            if (data.mAdaptiveHrBuf != null) {
                val history = data.mAdaptiveHrBuf.vals
                if (history != null) {
                    heartRateHistory.clear()
                    heartRateHistory.addAll(history.map { it.toFloat() }.takeLast(120))
                }
            }

            if (data.watchAppRunning) {
                watchConnectionText.value = "Watch: Connected"
                watchConnectionColor.value = okColour
            } else {
                watchConnectionText.value = "Watch: Disconnected"
                watchConnectionColor.value = warnColour
            }

            // Determine Main Status
            when {
                data.alarmStanding || data.fallAlarmStanding -> {
                    mainStatusText.value = "ALARM"
                    mainStatusColor.value = alarmColour
                    mainStatusDetails.value = data.alarmCause
                }
                data.alarmState == 6L -> {
                    mainStatusText.value = "MUTED"
                    mainStatusColor.value = warnColour
                    mainStatusDetails.value = "Alarms are temporarily muted."
                }
                data.alarmState == 4L || data.alarmState == 7L || data.mHrFrozenFaultStanding -> {
                    mainStatusText.value = "FAULT"
                    mainStatusColor.value = warnColour
                    mainStatusDetails.value = if (data.alarmState == 7L) getString(R.string.NetFault) else getString(R.string.Fault)
                }
                 data.batteryPc in 1..19 -> {
                    mainStatusText.value = "WARNING"
                    mainStatusColor.value = warnColour
                    mainStatusDetails.value = "Watch battery is low."
                }
                !data.watchAppRunning -> {
                     mainStatusText.value = "WARNING"
                    mainStatusColor.value = warnColour
                    mainStatusDetails.value = "Watch is disconnected."
                }
                else -> {
                    mainStatusText.value = "OK"
                    mainStatusColor.value = okColour
                    mainStatusDetails.value = "System is monitoring."
                }
            }
            
            // Update button state
            if ((server.mSmsTimer != null) && (server.mSmsTimer.mTimeLeft > 0)) {
                acceptAlarmButtonText.value = "Cancel SMS (${server.mSmsTimer.mTimeLeft / 1000}s)"
                acceptAlarmButtonEnabled.value = true
            } else {
                acceptAlarmButtonText.value = "Accept Alarm"
                acceptAlarmButtonEnabled.value = server.isLatchAlarms() || data.mFallActive
            }
            muteAlarmButtonEnabled.value = server.isAudibleCancelled
        }
    }


    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(versionText.value) },
                    modifier = Modifier.statusBarsPadding(),
                    actions = {
                        IconButton(onClick = { showMenu.value = !showMenu.value }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu.value,
                            onDismissRequest = { showMenu.value = false }
                        ) {
                            DropdownMenuItem(onClick = { 
                                if (mConnection.mBound) mConnection.mSdServer.mSdDataSource.installWatchApp()
                                showMenu.value = false
                            }) { Text("Install Watch App") }

                            DropdownMenuItem(onClick = { 
                                if (mConnection.mBound) mConnection.mSdServer.acceptAlarm()
                                showMenu.value = false
                            }) { Text("Accept Alarm") }

                            DropdownMenuItem(onClick = { 
                                mUtil.stopServer()
                                serverStatusHandler.postDelayed({ mUtil.startServer() }, 1000)
                                showMenu.value = false
                            }) { Text("Restart Service") }

                            Divider()

                            DropdownMenuItem(onClick = { 
                                if (mConnection.mBound) mConnection.mSdServer.alarmBeep()
                                showMenu.value = false
                            }) { Text("Test Alarm Beep") }

                            DropdownMenuItem(onClick = { 
                                if (mConnection.mBound) mConnection.mSdServer.warningBeep()
                                showMenu.value = false
                            }) { Text("Test Warning Beep") }

                            DropdownMenuItem(onClick = { 
                                if (mConnection.mBound) mConnection.mSdServer.sendSMSAlarm()
                                showMenu.value = false
                            }) { Text("Test SMS Alarm") }

                            Divider()

                            DropdownMenuItem(onClick = { 
                                startActivity(Intent(context, AuthenticateActivity::class.java))
                                showMenu.value = false
                            }) { Text("Data Sharing Login") }

                            DropdownMenuItem(onClick = { 
                                showDataSharingDialog.value = true
                                showMenu.value = false
                            }) { Text("About Data Sharing") }

                            DropdownMenuItem(onClick = { 
                                startActivity(Intent(context, LogManagerControlActivity::class.java))
                                showMenu.value = false
                            }) { Text("Log Manager") }

                            DropdownMenuItem(onClick = { 
                                startActivity(Intent(context, ReportSeizureActivity::class.java))
                                showMenu.value = false
                             }) { Text("Report Seizure") }

                            Divider()

                            DropdownMenuItem(onClick = { 
                                startActivity(Intent(context, PrefActivity::class.java))
                                showMenu.value = false
                            }) { Text("Settings") }

                            DropdownMenuItem(onClick = { 
                                showAboutDialog.value = true
                                showMenu.value = false
                            }) { Text("About") }

                            DropdownMenuItem(onClick = { 
                                mUtil.unbindFromServer(applicationContext, mConnection)
                                mUtil.stopServer()
                                finish()
                            }) { Text("Exit") }
                        }
                    }
                )
            }
        ) {
            MainLayout(
                modifier = Modifier.padding(it)
            )
        }
        if (showAboutDialog.value) {
            AboutDialog { showAboutDialog.value = false }
        }
        if (showDataSharingDialog.value) {
            DataSharingDialog { showDataSharingDialog.value = false }
        }
    }

    @Composable
    fun AboutDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("OpenSeizureDetector V${mUtil.getAppVersionName()}") },
            text = { Text("The Open Source Seizure Detector.") },
            confirmButton = {
                Button(onClick = { 
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OsdUtil.PRIVACY_POLICY_URL)))
                    onDismiss() 
                }) {
                    Text("Privacy Policy")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) { Text("Close") }
            }
        )
    }

    @Composable
    fun DataSharingDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.data_sharing_dialog_title)) },
            text = { Text("Information about data sharing...")},
            confirmButton = {
                Button(onClick = { 
                    startActivity(Intent(this, AuthenticateActivity::class.java))
                    onDismiss() 
                }) {
                    Text("Login")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }


    @Composable
    fun MainLayout(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Status Display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                backgroundColor = mainStatusColor.value,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = mainStatusText.value,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor.White
                    )
                    Text(
                        text = mainStatusDetails.value,
                        fontSize = 16.sp,
                        color = ComposeColor.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            Text(
                text = watchConnectionText.value,
                fontSize = 16.sp,
                color = watchConnectionColor.value,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Battery and Connection Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Watch Battery
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = watchBatteryPercentage.value,
                            modifier = Modifier.size(120.dp),
                            strokeWidth = 10.dp
                        )
                        Text(text = "${(watchBatteryPercentage.value * 100).toInt()}%", fontSize = 22.sp)
                    }
                    Text("Watch", modifier = Modifier.padding(top = 8.dp))
                }

                // Phone Battery
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = phoneBatteryPercentage.value,
                            modifier = Modifier.size(120.dp),
                            strokeWidth = 10.dp
                        )
                        Text(text = "${(phoneBatteryPercentage.value * 100).toInt()}%", fontSize = 22.sp)
                    }
                    Text("Phone", modifier = Modifier.padding(top = 8.dp))
                }
            }

            HeartRateChart(data = heartRateHistory, modifier = Modifier.weight(1f))

            // Action Buttons
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { 
                            if (mConnection.mBound) {
                                if ((mConnection.mSdServer.mSmsTimer != null) && (mConnection.mSdServer.mSmsTimer.mTimeLeft > 0)) {
                                    mUtil.showToast(getString(R.string.SMSAlarmCancelledMsg))
                                    mConnection.mSdServer.stopSmsTimer()
                                } else {
                                    mConnection.mSdServer.acceptAlarm()
                                }
                            }
                        }, 
                        enabled = acceptAlarmButtonEnabled.value,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) {
                        Text(text = acceptAlarmButtonText.value)
                    }
                    Button(
                        onClick = { 
                            if (mConnection.mBound) {
                                mConnection.mSdServer.raiseManualAlarm()
                            }
                        }, 
                        enabled = manualAlarmButtonEnabled.value,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) {
                        Text("Manual Alarm")
                    }
                }
                Button(
                    onClick = {
                        if (mConnection.mBound) {
                            mConnection.mSdServer.cancelAudible()
                        }
                    },
                    enabled = true, // Always enabled
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Text(if (muteAlarmButtonEnabled.value) "Unmute Alarm" else "Mute Alarm")
                }
            }
        }
    }

    @Composable
    fun HeartRateChart(data: List<Float>, modifier: Modifier = Modifier) {
        if (data.isNotEmpty()) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Heart Rate: ${data.last().toInt()}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        LineChart(it).apply {
                            setDescription("")
                            legend.isEnabled = false
                            xAxis.position = XAxis.XAxisPosition.BOTTOM
                            xAxis.setDrawGridLines(false)
                            axisLeft.setDrawGridLines(false)
                            axisRight.isEnabled = false
                            setTouchEnabled(true)
                            isDragEnabled = true
                            setScaleEnabled(true)
                        }
                    },
                    update = { chart ->
                        val entries = data.mapIndexed { index, value ->
                            Entry(value, index)
                        }
                        val xVals = data.indices.map { it.toString() }
                        val dataSet = LineDataSet(entries, "Heart Rate").apply {
                            color = android.graphics.Color.RED
                            setDrawValues(false)
                            setDrawCircles(false)
                            lineWidth = 2f
                        }
                        chart.data = LineData(xVals, listOf(dataSet))
                        chart.invalidate()
                    }
                )
            }
        }
    }
}