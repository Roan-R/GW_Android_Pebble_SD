package uk.org.openseizuredetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import uk.org.openseizuredetector.ui.theme.OpenSeizureDetectorTheme

enum class StartupItemState {
    PENDING, RUNNING, SUCCESS, FAILURE
}

data class StartupItem(val text: String, var state: StartupItemState, val isVisible: Boolean = true)

enum class StartupState {
    INITIALIZING,
    CHECKING_PERMISSIONS,
    CHECKING_BATTERY_OPTS,
    STARTING_SERVER,
    CONNECTING_TO_SERVER,
    CHECKING_WATCH_APP,
    WAITING_FOR_DATA,
    DONE
}

class LoadingActivity : ComponentActivity() {

    private val TAG = "LoadingActivity"

    private lateinit var mUtil: OsdUtil
    private lateinit var mConnection: SdServiceConnection
    private val handler = Handler(Looper.getMainLooper())
    private var currentState = StartupState.INITIALIZING

    private val startupItems = mutableStateListOf<StartupItem>()

    private val showBatteryOptimizationDialog = mutableStateOf(false)
    private var batteryDialogShown = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { 
        handler.post(startupRunnable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate()");
        mUtil = OsdUtil(applicationContext, handler)
        mConnection = SdServiceConnection(applicationContext)

        PreferenceManager.setDefaultValues(this, R.xml.alarm_prefs, true)
        PreferenceManager.setDefaultValues(this, R.xml.general_prefs, true)

        setContent {
            OpenSeizureDetectorTheme {
                LoadingUI(startupItems)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart() - beginning startup sequence.");
        currentState = StartupState.INITIALIZING
        handler.post(startupRunnable)
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop()");
        handler.removeCallbacks(startupRunnable)
        if (mConnection.mBound) {
            Log.i(TAG, "Unbinding from service.")
            mUtil.unbindFromServer(applicationContext, mConnection)
        }
    }

    private val startupRunnable: Runnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Executing state: $currentState")
            when (currentState) {
                StartupState.INITIALIZING -> {
                    startupItems.clear()
                    startupItems.add(StartupItem("Permissions", StartupItemState.RUNNING))
                    startupItems.add(StartupItem("Battery Optimizations", StartupItemState.PENDING))
                    startupItems.add(StartupItem("Background Service", StartupItemState.PENDING))
                    startupItems.add(StartupItem("Service Connection", StartupItemState.PENDING))
                    startupItems.add(StartupItem("Watch App", StartupItemState.PENDING))
                    startupItems.add(StartupItem("Watch Data", StartupItemState.PENDING))
                    currentState = StartupState.CHECKING_PERMISSIONS
                    handler.post(this)
                }

                StartupState.CHECKING_PERMISSIONS -> {
                    val missingPermissions = getRequiredPermissions().filter { ContextCompat.checkSelfPermission(this@LoadingActivity, it) != PackageManager.PERMISSION_GRANTED }
                    if (missingPermissions.isNotEmpty()) {
                        updateItemState("Permissions", StartupItemState.RUNNING, "Requesting Permissions...")
                        requestPermissionLauncher.launch(missingPermissions.toTypedArray())
                        return // Wait for user
                    }
                    updateItemState("Permissions", StartupItemState.SUCCESS)
                    currentState = StartupState.CHECKING_BATTERY_OPTS
                    handler.post(this)
                }

                StartupState.CHECKING_BATTERY_OPTS -> {
                     updateItemState("Battery Optimizations", StartupItemState.RUNNING)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                        if (!powerManager.isIgnoringBatteryOptimizations(packageName) && !batteryDialogShown) {
                            batteryDialogShown = true
                            showBatteryOptimizationDialog.value = true
                            return // Wait for user
                        }
                    }
                    updateItemState("Battery Optimizations", StartupItemState.SUCCESS)
                    currentState = StartupState.STARTING_SERVER
                    handler.post(this)
                }

                StartupState.STARTING_SERVER -> {
                    updateItemState("Background Service", StartupItemState.RUNNING)
                    if (!mUtil.isServerRunning()) {
                        mUtil.startServer()
                    }
                    updateItemState("Background Service", StartupItemState.SUCCESS)
                    currentState = StartupState.CONNECTING_TO_SERVER
                    handler.post(this)
                }

                StartupState.CONNECTING_TO_SERVER -> {
                    updateItemState("Service Connection", StartupItemState.RUNNING)
                    if (!mConnection.mBound) {
                        mUtil.bindToServer(applicationContext, mConnection)
                        handler.postDelayed(this, 250)
                        return
                    }
                    updateItemState("Service Connection", StartupItemState.SUCCESS)
                    currentState = StartupState.CHECKING_WATCH_APP
                    handler.post(this)
                }

                StartupState.CHECKING_WATCH_APP -> {
                    updateItemState("Watch App", StartupItemState.RUNNING)
                     if (mConnection.mSdServer?.mSdData?.watchAppRunning == false) {
                        handler.postDelayed(this, 500)
                        return
                    }
                    updateItemState("Watch App", StartupItemState.SUCCESS)
                    currentState = StartupState.WAITING_FOR_DATA
                    handler.post(this)
                }

                StartupState.WAITING_FOR_DATA -> {
                    updateItemState("Watch Data", StartupItemState.RUNNING)
                    if (mConnection.mSdServer?.mSdData?.dataTime == null) {
                        handler.postDelayed(this, 250)
                        return
                    }
                    updateItemState("Watch Data", StartupItemState.SUCCESS)
                    currentState = StartupState.DONE
                    handler.post(this)
                }

                StartupState.DONE -> {
                    startActivity(Intent(this@LoadingActivity, MainActivity3::class.java))
                    finish()
                }
            }
        }
    }
    
    private fun updateItemState(text: String, state: StartupItemState, message: String? = null) {
        val index = startupItems.indexOfFirst { it.text == text }
        if (index != -1) {
            startupItems[index] = startupItems[index].copy(state = state)
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.WAKE_LOCK,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("SendSms", false)) {
            permissions.add(Manifest.permission.SEND_SMS)
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        return permissions
    }

    @Composable
    fun LoadingUI(items: List<StartupItem>) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(id = R.drawable.star_of_life_48x48), contentDescription = "App Icon")
            Text(text = stringResource(id = R.string.app_name), style = MaterialTheme.typography.h5, modifier = Modifier.padding(top = 8.dp))
            Text(text = stringResource(id = R.string.StartingTitle), style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

            Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                items.forEach { item ->
                    if (item.isVisible) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            when (item.state) {
                                StartupItemState.PENDING -> Icon(painter = painterResource(id = android.R.drawable.ic_media_pause), contentDescription = "Pending", tint = Color.Gray)
                                StartupItemState.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                StartupItemState.SUCCESS -> Icon(painter = painterResource(id = android.R.drawable.ic_menu_myplaces), contentDescription = "Success", tint = Color(0xFF2EC4B6))
                                StartupItemState.FAILURE -> Icon(painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel), contentDescription = "Failure", tint = Color.Red)
                            }
                            Text(text = item.text, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openseizuredetector.org.uk/?page_id=1894"))) }) {
                Text("Help")
            }
            Button(onClick = { startActivity(Intent(this@LoadingActivity, PrefActivity::class.java)) }) {
                Text(stringResource(id = R.string.edit_settings))
            }
        }

        if (showBatteryOptimizationDialog.value) {
            AlertDialog(
                onDismissRequest = { 
                    showBatteryOptimizationDialog.value = false
                    Toast.makeText(this, "WARNING: Seizure detection may be unreliable without disabling battery optimisations.", Toast.LENGTH_LONG).show()
                    handler.post(startupRunnable)
                },
                title = { Text("Battery Optimizations") },
                text = { Text("To ensure OpenSeizureDetector runs reliably, you must disable battery optimizations. Please select 'All Apps', find OpenSeizureDetector and select 'Don\'t Optimize'.") },
                confirmButton = {
                    Button(onClick = { 
                        showBatteryOptimizationDialog.value = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                        } else {
                             handler.post(startupRunnable)
                        }
                    }) { Text("Open Settings") }
                },
                dismissButton = {
                    Button(onClick = {
                        showBatteryOptimizationDialog.value = false
                        Toast.makeText(this, "WARNING: Seizure detection may be unreliable without disabling battery optimisations.", Toast.LENGTH_LONG).show()
                        handler.post(startupRunnable)
                    }) { Text("Later") }
                }
            )
        }
    }
}