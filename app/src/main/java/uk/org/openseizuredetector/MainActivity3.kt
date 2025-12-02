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
import androidx.core.view.WindowCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import android.graphics.Color

class MainActivity3 : ComponentActivity() {

    private val TAG = "MainActivityCompose"
    // New Color Palette
    private val background = ComposeColor(0xFFD7FFF1)
    private val brown = ComposeColor(0xFF664E4C)
    private val charcoal = ComposeColor(0xFF172121)
    private val teal = ComposeColor(0xFF6A8D92)
    private val beige = ComposeColor(0xFFE2D0B6)
    private val white = ComposeColor.White

    private val okColour = teal
    private val warnColour = brown
    private val alarmColour = charcoal

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
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
            backgroundColor = background,
            topBar = {
                TopAppBar(
                    title = { Text(versionText.value) },
                    modifier = Modifier.statusBarsPadding(),
                    backgroundColor = charcoal,
                    contentColor = white,
                    actions = {
                        IconButton(onClick = { showMenu.value = !showMenu.value }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = white)
                        }
                        DropdownMenu(
                            expanded = showMenu.value,
                            onDismissRequest = { showMenu.value = false },
                            modifier = Modifier.width(200.dp)
                        ) {
                             Surface(shape = MaterialTheme.shapes.medium, color = background) {
                                Column {
                                    DropdownMenuItem(onClick = { 
                                        if (mConnection.mBound) mConnection.mSdServer.mSdDataSource.installWatchApp()
                                        showMenu.value = false
                                    }) { Text("Install Watch App", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        if (mConnection.mBound) mConnection.mSdServer.acceptAlarm()
                                        showMenu.value = false
                                    }) { Text("Accept Alarm", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        mUtil.stopServer()
                                        serverStatusHandler.postDelayed({ mUtil.startServer() }, 1000)
                                        showMenu.value = false
                                    }) { Text("Restart Service", color = charcoal) }

                                    Divider(color = teal)

                                    DropdownMenuItem(onClick = { 
                                        if (mConnection.mBound) mConnection.mSdServer.alarmBeep()
                                        showMenu.value = false
                                    }) { Text("Test Alarm Beep", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        if (mConnection.mBound) mConnection.mSdServer.warningBeep()
                                        showMenu.value = false
                                    }) { Text("Test Warning Beep", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        if (mConnection.mBound) mConnection.mSdServer.sendSMSAlarm()
                                        showMenu.value = false
                                    }) { Text("Test SMS Alarm", color = charcoal) }

                                    Divider(color = teal)

                                    DropdownMenuItem(onClick = { 
                                        startActivity(Intent(context, AuthenticateActivity::class.java))
                                        showMenu.value = false
                                    }) { Text("Data Sharing Login", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        showDataSharingDialog.value = true
                                        showMenu.value = false
                                    }) { Text("About Data Sharing", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        startActivity(Intent(context, LogManagerControlActivity::class.java))
                                        showMenu.value = false
                                    }) { Text("Log Manager", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        startActivity(Intent(context, ReportSeizureActivity::class.java))
                                        showMenu.value = false
                                     }) { Text("Report Seizure", color = charcoal) }

                                    Divider(color = teal)

                                    DropdownMenuItem(onClick = { 
                                        startActivity(Intent(context, PrefActivity::class.java))
                                        showMenu.value = false
                                    }) { Text("Settings", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        showAboutDialog.value = true
                                        showMenu.value = false
                                    }) { Text("About", color = charcoal) }

                                    DropdownMenuItem(onClick = { 
                                        mUtil.unbindFromServer(applicationContext, mConnection)
                                        mUtil.stopServer()
                                        finish()
                                    }) { Text("Exit", color = charcoal) }
                                }
                            }
                        }
                    }
                )
            }
        ) {
            MainLayout(
                modifier = Modifier.padding(it).navigationBarsPadding()
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
                        color = white
                    )
                    Text(
                        text = mainStatusDetails.value,
                        fontSize = 16.sp,
                        color = white,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            Text(
                text = watchConnectionText.value,
                fontSize = 16.sp,
                color = if (watchConnectionColor.value == okColour) charcoal else warnColour,
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
                            progress = 1f,
                            modifier = Modifier.size(120.dp),
                            color = beige,
                            strokeWidth = 10.dp
                        )
                        CircularProgressIndicator(
                            progress = watchBatteryPercentage.value,
                            modifier = Modifier.size(120.dp),
                            color = teal,
                            strokeWidth = 10.dp
                        )
                        Text(text = "${(watchBatteryPercentage.value * 100).toInt()}%", fontSize = 22.sp, color = charcoal)
                    }
                    Text("Watch", modifier = Modifier.padding(top = 8.dp), color = charcoal)
                }

                // Phone Battery
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = 1f,
                            modifier = Modifier.size(120.dp),
                            color = beige,
                            strokeWidth = 10.dp
                        )
                        CircularProgressIndicator(
                            progress = phoneBatteryPercentage.value,
                            modifier = Modifier.size(120.dp),
                            color = teal,
                            strokeWidth = 10.dp
                        )
                        Text(text = "${(phoneBatteryPercentage.value * 100).toInt()}%", fontSize = 22.sp, color = charcoal)
                    }
                    Text("Phone", modifier = Modifier.padding(top = 8.dp), color = charcoal)
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
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = teal,
                            contentColor = white,
                            disabledBackgroundColor = beige,
                            disabledContentColor = charcoal
                        )
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
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = teal,
                            contentColor = white,
                            disabledBackgroundColor = beige,
                            disabledContentColor = charcoal
                        )
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = teal,
                        contentColor = white,
                        disabledBackgroundColor = beige,
                        disabledContentColor = charcoal
                    )
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
                    fontWeight = FontWeight.Bold,
                    color = charcoal
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
                            xAxis.textColor = Color.parseColor("#172121")
                            axisLeft.textColor = Color.parseColor("#172121")
                        }
                    },
                    update = { chart ->
                        val entries = data.mapIndexed { index, value ->
                            Entry(value, index)
                        }
                        val xVals = data.indices.map { it.toString() }
                        val dataSet = LineDataSet(entries, "Heart Rate").apply {
                            color = Color.parseColor("#6A8D92") // Teal
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