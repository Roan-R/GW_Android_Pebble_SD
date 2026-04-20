package uk.org.openseizuredetector

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import uk.org.openseizuredetector.ui.theme.OpenSeizureDetectorTheme
import java.util.Timer
import java.util.TimerTask
import java.io.File
import java.io.FileOutputStream
import android.app.AlertDialog

class MainActivity3 : ComponentActivity() {

    private val tag = "MainActivityCompose"
    // New Color Palette
    private val background = ComposeColor(0xFFE0EDEA)
    private val brown = ComposeColor(0xFF664E4C)
    private val charcoal = ComposeColor(0xFF172121)
    private val teal = ComposeColor(0xFF6A8D92)
    private val muteBlue = ComposeColor(0xFF6DC5DB)
    private val beige = ComposeColor(0xFFE2D0B6)
    private val white = ComposeColor.White

    // Heart Rate Legend Colors
    private val normalHrBlue = ComposeColor(0xFF2d8bba)
    private val elevatedHrPurple = ComposeColor(0xFFcb6ce6)
    private val seizureHrRed = ComposeColor(0xFFff5757)
    
    private val okStatusBackground = ComposeColor(0xFFb3d3d8)
    private val okStatusText = ComposeColor(0xFF063d59)

    private val okColour = okStatusBackground
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

    private val manualAlarmButtonEnabled = mutableStateOf(true)
    private val muteAlarmButtonEnabled = mutableStateOf(false)
    
    // Menu and Dialog states
    private val versionText = mutableStateOf("")
    private val showMenu = mutableStateOf(false)
    private val showAboutDialog = mutableStateOf(false)
    private val showDataSharingDialog = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(tag, "onCreate()");

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
        Log.i(tag, "onStart()");
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
        Log.i(tag, "onStop() - unbinding from server")
        mUtil.writeToSysLogFile("MainActivity.onStop() - Compose")
        mUtil.unbindFromServer(applicationContext, mConnection)
        mUiTimer?.cancel()
    }

    private fun prepareTestData() {
        val assetManager = assets
        val dataDir = File(filesDir, "data")
        if (!dataDir.exists()) dataDir.mkdirs()

        try {
            // List all files in the "data" folder of your assets
            val files = assetManager.list("data") ?: return
            for (filename in files) {
                val out = File(dataDir, filename)
                assetManager.open("data/$filename").use { input ->
                    out.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Log.d(tag, "Test data copied to ${dataDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy test assets", e)
        }
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
                    mainStatusDetails.value = "System is monitoring"
                }
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
                    title = { },
                    modifier = Modifier.statusBarsPadding(),
                    backgroundColor = ComposeColor.Transparent,
                    elevation = 0.dp,
                    actions = {
                        IconButton(onClick = { showMenu.value = !showMenu.value }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = okStatusText)
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
                                        startActivity(Intent(context, AuthenticateActivity2::class.java))
                                        showMenu.value = false
                                    }) { Text("Data Sharing Login", color = charcoal) }

//                                    DropdownMenuItem(onClick = {
//                                        showDataSharingDialog.value = true
//                                        showMenu.value = false
//                                    }) { Text("About Data Sharing", color = charcoal) } //took 'about data sharing' tab out

                                    DropdownMenuItem(onClick = { 
                                        startActivity(Intent(context, LogManagerComposeActivity::class.java))
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

                                    Divider(color = teal)
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
            title = { Text("SeizeWatch V${mUtil.getAppVersionName()}") },
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
                    startActivity(Intent(this, AuthenticateActivity2::class.java))
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
            // Add the logo here
            Row(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.croppedlogo),
                    contentDescription = "Seize Watch Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .offset(y = (-60).dp) // Move the logo up
                        .offset(x = (10).dp)
                )
            }

            // Main Status Display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 10.dp),
                backgroundColor = mainStatusColor.value,
                shape = CircleShape
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 8.dp) // Adjusted padding
                ) {
                    val textColor = if (mainStatusColor.value == okColour) okStatusText else white
                    Text(
                        text = mainStatusText.value,
                        fontSize = 36.sp, // Smaller font
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = mainStatusDetails.value,
                        fontSize = 16.sp,
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            HeartRateChart(data = heartRateHistory, modifier = Modifier.weight(1f))

            // Heart Rate Legend
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                LegendItem(color = normalHrBlue, text = "Normal")
                Spacer(Modifier.width(24.dp))
                LegendItem(color = elevatedHrPurple, text = "Elevated")
                Spacer(Modifier.width(24.dp))
                LegendItem(color = seizureHrRed, text = "Seizure Warning")
            }

            // Action Buttons
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { 
                            if (mConnection.mBound) {
                                mConnection.mSdServer.raiseManualAlarm()
                            }
                        }, 
                        enabled = manualAlarmButtonEnabled.value,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = teal,
                            contentColor = white,
                            disabledBackgroundColor = beige,
                            disabledContentColor = charcoal
                        )
                    ) {
                        Text("MANUAL ALARM")
                    }
                    Button(
                        onClick = {
                            if (mConnection.mBound) {
                                mConnection.mSdServer.cancelAudible()
                            }
                        },
                        enabled = true, // Always enabled
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = muteBlue,
                            contentColor = white,
                            disabledBackgroundColor = beige,
                            disabledContentColor = charcoal
                        )
                    ) {
                        Text(if (muteAlarmButtonEnabled.value) "UNMUTE ALARM" else "MUTE ALARM")
                    }
                }
            }
            
            Text(
                text = watchConnectionText.value,
                fontSize = 16.sp,
                color = if (watchConnectionColor.value == okColour) charcoal else warnColour,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            // Battery and Connection Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Watch Battery
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = 1f,
                            modifier = Modifier.size(100.dp),
                            color = beige,
                            strokeWidth = 10.dp
                        )
                        CircularProgressIndicator(
                            progress = watchBatteryPercentage.value,
                            modifier = Modifier.size(100.dp),
                            color = teal,
                            strokeWidth = 10.dp
                        )
                        Text(text = "${(watchBatteryPercentage.value * 100).toInt()}%", fontSize = 22.sp, color = charcoal)
                    }
                    Text("Watch", modifier = Modifier.padding(top = 8.dp), color = charcoal)
                }

                Spacer(modifier = Modifier.width(48.dp))

                // Phone Battery
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = 1f,
                            modifier = Modifier.size(100.dp),
                            color = beige,
                            strokeWidth = 10.dp
                        )
                        CircularProgressIndicator(
                            progress = phoneBatteryPercentage.value,
                            modifier = Modifier.size(100.dp),
                            color = teal,
                            strokeWidth = 10.dp
                        )
                        Text(text = "${(phoneBatteryPercentage.value * 100).toInt()}%", fontSize = 22.sp, color = charcoal)
                    }
                    Text("Phone", modifier = Modifier.padding(top = 8.dp), color = charcoal)
                }
            }
        }
    }

    @Composable
    fun LegendItem(color: ComposeColor, text: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(16.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, fontSize = 16.sp, color = charcoal)
        }
    }

    @Composable
    fun HeartRateChart(data: List<Float>, modifier: Modifier = Modifier) {
        if (data.isNotEmpty()) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Heart Rate: ${data.last().toInt()}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    fontStyle = FontStyle.Normal,
                    color = okStatusText
                )
                Spacer(modifier = Modifier.height(16.dp))
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = {
                        LineChart(it).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            setDrawGridBackground(false)
                            setDescription("")
                            legend.isEnabled = false
                            xAxis.position = XAxis.XAxisPosition.BOTTOM
                            xAxis.setDrawGridLines(false)
                            axisLeft.setDrawGridLines(true)
                            axisLeft.gridColor = Color.LTGRAY
                            axisLeft.setDrawAxisLine(false)
                            axisRight.isEnabled = false
                            setTouchEnabled(true)
                            isDragEnabled = true
                            setScaleEnabled(true)
                            xAxis.textColor = Color.parseColor("#172121")
                            axisLeft.textColor = Color.parseColor("#172121")
                        }
                    },
                    update = { chart ->
                        if (data.size > 1) {
                            val xVals = ArrayList<String>()
                            val entries = ArrayList<Entry>()
                            
                            data.forEachIndexed { index, value ->
                                xVals.add(index.toString())
                                entries.add(Entry(value, index))
                            }

                            val lastHr = data.lastOrNull() ?: 0f
                            val lineColor = when {
                                lastHr <= 80 -> normalHrBlue.toArgb()
                                lastHr <= 100 -> elevatedHrPurple.toArgb()
                                else -> seizureHrRed.toArgb()
                            }

                            val dataSet = LineDataSet(entries, "HR History")
                            dataSet.setDrawCircles(true)
                            dataSet.setCircleColor(lineColor)
                            dataSet.setCircleSize(3f)
                            dataSet.setDrawValues(false)
                            
                            // Line Settings
                            dataSet.setColor(lineColor)
                            dataSet.setLineWidth(3f)
                            dataSet.setDrawCubic(true)
                            dataSet.setCubicIntensity(0.15f)

                            val lineData = LineData(xVals, listOf(dataSet))
                            chart.data = lineData
                            
                            chart.setVisibleXRangeMaximum(60f)
                            chart.moveViewToX((data.size - 1).toFloat())
                            chart.invalidate()
                        }
                    }
                )
                Text(text = "Time", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = charcoal)
            }
        }
    }
}
