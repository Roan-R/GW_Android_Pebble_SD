package uk.org.openseizuredetector

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import uk.org.openseizuredetector.ui.theme.OpenSeizureDetectorTheme

enum class SettingsPage {
    Main,
    General,
    Alarms,
    Logging,
    SeizureDetector,
    Pebble,
    Network
}

class PrefActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val TAG = "PrefActivity"
    private lateinit var mUtil: OsdUtil
    private val mHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mUtil = OsdUtil(this, mHandler)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        setContent {
            OpenSeizureDetectorTheme {
                var currentScreen by remember { mutableStateOf(SettingsPage.Main) }

                when (currentScreen) {
                    SettingsPage.Main -> MainSettingsScreen(onNavigate = { currentScreen = it }, onNavigateBack = { finish() })
                    SettingsPage.General -> GeneralSettingsScreen { currentScreen = SettingsPage.Main }
                    SettingsPage.Alarms -> AlarmsSettingsScreen { currentScreen = SettingsPage.Main }
                    SettingsPage.Logging -> LoggingSettingsScreen { currentScreen = SettingsPage.Main }
                    SettingsPage.SeizureDetector -> SeizureDetectorSettingsScreen { currentScreen = SettingsPage.Main }
                    SettingsPage.Pebble -> PebbleSettingsScreen { currentScreen = SettingsPage.Main }
                    SettingsPage.Network -> NetworkSettingsScreen { currentScreen = SettingsPage.Main }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.i(TAG, "onSharedPreferenceChanged: key=$key")
        // Restart the server to apply new settings, similar to the legacy PrefActivity.java logic
        if (mUtil.isServerRunning) {
            Log.i(TAG, "Restarting SdServer to apply changes...")
            mUtil.stopServer()
            mHandler.postDelayed({ mUtil.startServer() }, 1000)
        }
    }

    @Composable
    fun MainSettingsScreen(onNavigate: (SettingsPage) -> Unit, onNavigateBack: () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                item {
                    Preference(
                        title = stringResource(R.string.general_settings_title),
                        summary = stringResource(R.string.general_settings_summary),
                        onClick = { onNavigate(SettingsPage.General) }
                    )
                }
                item {
                    Preference(
                        title = stringResource(R.string.alarms_settings_title),
                        summary = stringResource(R.string.alarms_settings_summary),
                        onClick = { onNavigate(SettingsPage.Alarms) }
                    )
                }
                item {
                    Preference(
                        title = stringResource(R.string.logging_settings_title),
                        summary = stringResource(R.string.logging_settings_summary),
                        onClick = { onNavigate(SettingsPage.Logging) }
                    )
                }
                item {
                    Preference(
                        title = stringResource(R.string.seizure_detector_settings_title),
                        summary = stringResource(R.string.seizure_detector_settings_summary),
                        onClick = { onNavigate(SettingsPage.SeizureDetector) }
                    )
                }
                item {
                    Preference(
                        title = stringResource(R.string.pebble_datasource_title),
                        summary = stringResource(R.string.pebble_datasource_summary),
                        onClick = { onNavigate(SettingsPage.Pebble) }
                    )
                }
                item {
                    Preference(
                        title = stringResource(R.string.network_datasource_title),
                        summary = stringResource(R.string.network_datasource_summary),
                        onClick = { onNavigate(SettingsPage.Network) }
                    )
                }
            }
        }
    }

    @Composable
    fun GeneralSettingsScreen(onNavigateBack: () -> Unit) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val context = this
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.general_settings_title)) },
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                item {
                    ListPreference(
                        key = "DataSource",
                        title = stringResource(R.string.select_datasource_title),
                        summary = stringResource(R.string.select_datasource_summary),
                        entries = resources.getStringArray(R.array.datasource_list),
                        entryValues = resources.getStringArray(R.array.datasource_list_values),
                        defaultValue = "Phone",
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "UseNewUi",
                        title = stringResource(R.string.use_new_ui_title),
                        summary = stringResource(R.string.use_new_ui_summary),
                        defaultValue = true,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "AutoStart",
                        title = stringResource(R.string.auto_start_title),
                        summary = stringResource(R.string.auto_start_summary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "PreventBatteryOptWarning",
                        title = stringResource(R.string.prevent_bat_opt_title),
                        summary = stringResource(R.string.prevent_bat_opt_summary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    Preference(
                        title = stringResource(R.string.select_ble_device_title),
                        summary = stringResource(R.string.select_ble_device_desc),
                        onClick = {
                            val intent = Intent(context, BLEScanActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }
                item {
                    EditTextPreference(
                        key = "AppVersionName",
                        title = stringResource(R.string.app_version_title),
                        summary = stringResource(R.string.app_version_summary),
                        defaultValue = "",
                        sharedPreferences = sharedPreferences
                    )
                }
            }
        }
    }

    @Composable
    fun AlarmsSettingsScreen(onNavigateBack: () -> Unit) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.alarms_settings_title)) },
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                item { CategoryHeader(title = stringResource(R.string.AlarmFunctionalitySettingsTitle), isSubCategory = true) }
                item {
                    CheckBoxPreference(
                        key = "LatchAlarms",
                        title = stringResource(R.string.latch_alarms_title),
                        summary = stringResource(R.string.latch_alarms_summary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "LatchAlarmTimerPeriod",
                        title = stringResource(R.string.latch_timer_period_title),
                        summary = stringResource(R.string.latch_timer_period_summary),
                        defaultValue = "10",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item { CategoryHeader(title = stringResource(R.string.AudibleAlarmSettingsTitle), isSubCategory = true) }
                item {
                    CheckBoxPreference(
                        key = "AudibleAlarm",
                        title = stringResource(R.string.enable_audible_alarm_title),
                        summary = stringResource(R.string.enable_audible_alarm_summary),
                        defaultValue = true,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "AudibleWarning",
                        title = stringResource(R.string.enable_audible_warning_title),
                        summary = stringResource(R.string.enable_audible_warning_summary),
                        defaultValue = true,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "AudibleFaultWarning",
                        title = stringResource(R.string.enable_audible_fault_title),
                        summary = stringResource(R.string.enable_audible_fault_summary),
                        defaultValue = true,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "FaultTimerPeriod",
                        title = stringResource(R.string.fault_timer_period_title),
                        summary = stringResource(R.string.fault_timer_period_summary),
                        defaultValue = "30",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "UseMp3Alarm",
                        title = stringResource(R.string.use_mp3_alarm_title),
                        summary = stringResource(R.string.use_mp3_alarm_summary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item { CategoryHeader(title = stringResource(R.string.SMSAlarmSettingsTitle), isSubCategory = true) }
                item {
                    CheckBoxPreference(
                        key = "SMSAlarm",
                        title = stringResource(R.string.enable_sms_alarm_title),
                        summary = stringResource(R.string.enable_sms_alarm_summary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "SMSDelayPeriod",
                        title = stringResource(R.string.sms_delay_sec),
                        summary = stringResource(R.string.sms_delay_sec_desc),
                        defaultValue = "10",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "SMSNumbers",
                        title = stringResource(R.string.sms_numbers_title),
                        summary = stringResource(R.string.sms_numbers_summary),
                        defaultValue = "",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Phone
                    )
                }
                item {
                    EditTextPreference(
                        key = "SMSMsg",
                        title = stringResource(R.string.sms_message_title),
                        summary = stringResource(R.string.sms_message_summary),
                        defaultValue = stringResource(R.string.DefaultSMSMsgText),
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "SMSFalseAlarmMsg",
                        title = stringResource(R.string.sms_false_alarm_message_title),
                        summary = stringResource(R.string.sms_false_alarm_message_summary),
                        defaultValue = stringResource(R.string.DefaultSMSFalseAlarmMsgText),
                        sharedPreferences = sharedPreferences
                    )
                }
            }
        }
    }

    @Composable
    fun LoggingSettingsScreen(onNavigateBack: () -> Unit) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.logging_settings_title)) },
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                item {
                    EditTextPreference(
                        key = "EventDurationSec",
                        title = stringResource(R.string.eventDurationTitle),
                        summary = stringResource(R.string.eventDurationSummary),
                        defaultValue = "180",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "AutoPruneDb",
                        title = stringResource(R.string.AutoPruneDbTitle),
                        summary = stringResource(R.string.AutoPruneDbSummary),
                        defaultValue = true,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "DataRetentionPeriod",
                        title = stringResource(R.string.dataRetentionPeriodTitle),
                        summary = stringResource(R.string.dataRetentionPeriodSummary),
                        defaultValue = "7",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "LogDataRemoteMobile",
                        title = stringResource(R.string.log_data_remote_mobile_title),
                        summary = stringResource(R.string.log_data_remote_mobile_summary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "LogNDA",
                        title = stringResource(R.string.LogNDATitle),
                        summary = stringResource(R.string.LogNDASummary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
            }
        }
    }

    @Composable
    fun SeizureDetectorSettingsScreen(onNavigateBack: () -> Unit) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.seizure_detector_settings_title)) },
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                // --- Algorithm Selection ---
                item { CategoryHeader(title = stringResource(R.string.AlgorithmSelectionTitle)) }
                item {
                    CheckBoxPreference(
                        key = "OsdAlarmActive",
                        title = stringResource(R.string.OsdAlarmEnabledTitle),
                        summary = stringResource(R.string.OsdAlarmEnabledSummary),
                        defaultValue = true,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "FlapAlarmActive",
                        title = "Enable Flap Alarm",
                        summary = "Enable the experimental alarm to detect arm 'flapping' motion",
                        defaultValue = true,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "CnnAlarmActive",
                        title = stringResource(R.string.CnnAlarmEnabledTitle),
                        summary = stringResource(R.string.CnnAlarmEnabledSummary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "HRAlarmActive",
                        title = stringResource(R.string.HRAlarmEnabledTitle),
                        summary = stringResource(R.string.HRAlarmEnabledSummary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "HRAdaptiveAlarmActive",
                        title = stringResource(R.string.HRAdaptiveAlarmEnabledTitle),
                        summary = stringResource(R.string.HRAlarmEnabledSummary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "HRAverageAlarmActive",
                        title = stringResource(R.string.HRAverageAlarmEnabledTitle),
                        summary = stringResource(R.string.HRAverageAlarmEnabledSummary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "O2SatAlarmActive",
                        title = stringResource(R.string.O2Sat_enabled_title),
                        summary = stringResource(R.string.O2Sat_enabled_summary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "FallActive",
                        title = stringResource(R.string.fall_detect_active_title),
                        summary = "",
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }

                // --- OSD Shaking Settings ---
                item { CategoryHeader(title = stringResource(R.string.SeizureDetectorSettingsTitle)) }
                item {
                    EditTextPreference(
                        key = "WarnTime",
                        title = stringResource(R.string.WarnTimeTitle),
                        summary = stringResource(R.string.WarnTimeSummary),
                        defaultValue = "5",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "AlarmTime",
                        title = stringResource(R.string.AlarmTimeTitle),
                        summary = stringResource(R.string.AlarmTimeSummary),
                        defaultValue = "10",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "AlarmThresh",
                        title = stringResource(R.string.AlarmThreshTitle),
                        summary = stringResource(R.string.AlarmThreshSummary),
                        defaultValue = "100",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "AlarmRatioThresh",
                        title = stringResource(R.string.AlarmRatioThreshTitle),
                        summary = stringResource(R.string.AlarmRatioThreshSummary),
                        defaultValue = "57",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "AlarmFreqMin",
                        title = stringResource(R.string.AlarmFreqMinTitle),
                        summary = stringResource(R.string.AlarmFreqMinSummary),
                        defaultValue = "3",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "AlarmFreqMax",
                        title = stringResource(R.string.AlarmFreqMaxTitle),
                        summary = stringResource(R.string.AlarmFreqMaxSummary),
                        defaultValue = "8",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }

                // --- Flap Alarm Settings ---
                item { CategoryHeader(title = "Flap Alarm Settings") }
                item {
                    EditTextPreference(
                        key = "FlapAlarmThresh",
                        title = "Flap Alarm Threshold",
                        summary = "Flap Alarm Threshold",
                        defaultValue = "5000",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "FlapAlarmRatioThresh",
                        title = "Flap Alarm Ratio Threshold",
                        summary = "Flap Alarm Ratio Threshold",
                        defaultValue = "90",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "FlapAlarmFreqMin",
                        title = stringResource(R.string.AlarmFreqMinTitle),
                        summary = stringResource(R.string.AlarmFreqMinSummary),
                        defaultValue = "2",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "FlapAlarmFreqMax",
                        title = stringResource(R.string.AlarmFreqMaxTitle),
                        summary = stringResource(R.string.AlarmFreqMaxSummary),
                        defaultValue = "4",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }

                // --- Machine Learning Settings ---
                item { CategoryHeader(title = stringResource(R.string.ml_sd_settings_title)) }
                item {
                    EditTextPreference(
                        key = "CnnAlarmThreshold",
                        title = stringResource(R.string.ml_sd_threshold_title),
                        summary = stringResource(R.string.ml_sd_threshold_summary),
                        defaultValue = "5",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }

                // --- Heart Rate Settings ---
                item { CategoryHeader(title = stringResource(R.string.HeartRateAlarmSettingsTitle)) }
                item {
                    CheckBoxPreference(
                        key = "HrFrozenAlarm",
                        title = stringResource(R.string.HrFrozenTitle),
                        summary = stringResource(R.string.HrFrozenSummary),
                        defaultValue = true,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    CheckBoxPreference(
                        key = "HRNullAsAlarm",
                        title = stringResource(R.string.HRNullAlarmTitle),
                        summary = stringResource(R.string.HRNullAlarmSummary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "HRThreshMin",
                        title = stringResource(R.string.HRThreshMinTitle),
                        summary = stringResource(R.string.HRThreshMinSummary),
                        defaultValue = "40",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "HRThreshMax",
                        title = stringResource(R.string.HRThreshMaxTitle),
                        summary = stringResource(R.string.HRThreshMaxSummary),
                        defaultValue = "150",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }

                // --- Heart Rate Adaptive ---
                item { CategoryHeader(title = stringResource(R.string.HeartRateAdaptiveAlarmSettingsTitle)) }
                item {
                    EditTextPreference(
                        key = "HRAdaptiveAlarmWindowSecs",
                        title = stringResource(R.string.HRAdaptiveAlarmWindowTitle),
                        summary = stringResource(R.string.HRAdaptiveAlarmWindowSummary),
                        defaultValue = "30",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "HRAdaptiveAlarmThresh",
                        title = stringResource(R.string.HRAdaptiveThreshTitle),
                        summary = stringResource(R.string.HRAdaptiveThreshSummary),
                        defaultValue = "20",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }

                // --- Heart Rate Average ---
                item { CategoryHeader(title = stringResource(R.string.HeartRateAverageAlarmSettingsTitle)) }
                item {
                    EditTextPreference(
                        key = "HRAverageAlarmThreshMin",
                        title = stringResource(R.string.HRAverageThreshMinTitle),
                        summary = stringResource(R.string.HRAverageThreshMinSummary),
                        defaultValue = "40",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "HRAverageAlarmThreshMax",
                        title = stringResource(R.string.HRAverageThreshMaxTitle),
                        summary = stringResource(R.string.HRAverageThreshMaxSummary),
                        defaultValue = "120",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "HRAverageAlarmWindowSecs",
                        title = stringResource(R.string.HRAverageAlarmWindowTitle),
                        summary = stringResource(R.string.HRAverageAlarmWindowSummary),
                        defaultValue = "120",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }

                // --- O2 Saturation ---
                item { CategoryHeader(title = stringResource(R.string.O2SatSettingsTitle)) }
                item {
                    CheckBoxPreference(
                        key = "O2SatNullAsAlarm",
                        title = stringResource(R.string.O2SatNullAlarmTitle),
                        summary = stringResource(R.string.O2SatNullAlarmSummary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "O2SatThreshMin",
                        title = stringResource(R.string.O2SatThreshMinTitle),
                        summary = stringResource(R.string.O2SatThreshMinSummary),
                        defaultValue = "80",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }

                // --- Fall Detection ---
                item { CategoryHeader(title = stringResource(R.string.fall_detect_title)) }
                item {
                    EditTextPreference(
                        key = "FallThreshMin",
                        title = stringResource(R.string.fall_thresh_min_title),
                        summary = "",
                        defaultValue = "1500",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "FallThreshMax",
                        title = stringResource(R.string.fall_thresh_max_title),
                        summary = "",
                        defaultValue = "3500",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "FallWindow",
                        title = stringResource(R.string.fall_window_title),
                        summary = "",
                        defaultValue = "1500",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }

                // --- Fidget Detector ---
                item { CategoryHeader(title = stringResource(R.string.FidgetSettingsTitle)) }
                item {
                    CheckBoxPreference(
                        key = "FidgetDetectorEnabled",
                        title = stringResource(R.string.FidgetDetectorEnabledTitle),
                        summary = stringResource(R.string.FidgetDetectorEnabledSummary),
                        defaultValue = false,
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "FidgetDetectorThreshold",
                        title = stringResource(R.string.FidgetDetectorThresholdTitle),
                        summary = stringResource(R.string.FidgetDetectorThresholdSummary),
                        defaultValue = "0.6",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "FidgetDetectorPeriod",
                        title = stringResource(R.string.FidgetDetectorPeriodTitle),
                        summary = stringResource(R.string.FidgetDetectorPeriodSummary),
                        defaultValue = "20",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
            }
        }
    }

    @Composable
    fun PebbleSettingsScreen(onNavigateBack: () -> Unit) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.pebble_datasource_title)) },
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                item { CategoryHeader(title = stringResource(R.string.user_interface_settings_title), isSubCategory = true) }
                item {
                    EditTextPreference(
                        key = "PebbleUpdatePeriod",
                        title = stringResource(R.string.pebble_update_period_title),
                        summary = stringResource(R.string.pebble_update_period_summary),
                        defaultValue = "5",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "MutePeriod",
                        title = stringResource(R.string.mute_period_title),
                        summary = stringResource(R.string.mute_period_summary),
                        defaultValue = "300",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "ManAlarmPeriod",
                        title = stringResource(R.string.manual_alarm_period_title),
                        summary = stringResource(R.string.manual_alarm_period_summary),
                        defaultValue = "30",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    ListPreference(
                        key = "PebbleDisplaySpectrum",
                        title = stringResource(R.string.display_spectrum_mode_title),
                        summary = stringResource(R.string.display_spectrum_mode_summary),
                        entries = resources.getStringArray(R.array.pebble_display_spectrum_list),
                        entryValues = resources.getStringArray(R.array.pebble_display_spectrum_values),
                        defaultValue = "0",
                        sharedPreferences = sharedPreferences
                    )
                }
                item { CategoryHeader(title = stringResource(R.string.analysis_prefs_title), isSubCategory = true) }
                item {
                    ListPreference(
                        key = "PebbleSdMode",
                        title = stringResource(R.string.seizure_detect_mode_title),
                        summary = stringResource(R.string.seizure_detect_mode_summary),
                        entries = resources.getStringArray(R.array.pebble_sd_mode_list),
                        entryValues = resources.getStringArray(R.array.pebble_sd_mode_list_values),
                        defaultValue = "0",
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    ListPreference(
                        key = "SampleFreq",
                        title = stringResource(R.string.sample_freq_title),
                        summary = stringResource(R.string.sample_freq_summary),
                        entries = resources.getStringArray(R.array.pebble_sample_freq_list),
                        entryValues = resources.getStringArray(R.array.pebble_sample_freq_list_values),
                        defaultValue = "100",
                        sharedPreferences = sharedPreferences
                    )
                }
                item { CategoryHeader(title = stringResource(R.string.watch_comms_settings_title), isSubCategory = true) }
                item {
                    ListPreference(
                        key = "PebbleDebug",
                        title = stringResource(R.string.debug_mode_title),
                        summary = stringResource(R.string.debug_mode_summary),
                        entries = resources.getStringArray(R.array.pebble_debug_list),
                        entryValues = resources.getStringArray(R.array.pebble_debug_values),
                        defaultValue = "0",
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "AppRestartTimeout",
                        title = stringResource(R.string.app_restart_timeout_title),
                        summary = stringResource(R.string.app_restart_timeout_summary),
                        defaultValue = "10",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
            }
        }
    }

    @Composable
    fun NetworkSettingsScreen(onNavigateBack: () -> Unit) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.network_datasource_title)) },
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                item {
                    EditTextPreference(
                        key = "ServerIP",
                        title = stringResource(R.string.server_ip_title),
                        summary = stringResource(R.string.server_ip_summary),
                        defaultValue = "192.168.1.175",
                        sharedPreferences = sharedPreferences
                    )
                }
                item {
                    EditTextPreference(
                        key = "DataUpdatePeriod",
                        title = stringResource(R.string.network_update_period_title),
                        summary = stringResource(R.string.network_update_period_summary),
                        defaultValue = "2000",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "ConnectTimeoutPeriod",
                        title = stringResource(R.string.connection_timeout_title),
                        summary = stringResource(R.string.connection_timeout_summary),
                        defaultValue = "5000",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
                item {
                    EditTextPreference(
                        key = "ReadTimeoutPeriod",
                        title = stringResource(R.string.read_timeout_title),
                        summary = stringResource(R.string.read_timeout_summary),
                        defaultValue = "5000",
                        sharedPreferences = sharedPreferences,
                        keyboardType = KeyboardType.Number
                    )
                }
            }
        }
    }

    @Composable
    fun CategoryHeader(title: String, isSubCategory: Boolean = false) {
        Text(
            text = title,
            color = if (isSubCategory) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
            style = if (isSubCategory) MaterialTheme.typography.subtitle2 else MaterialTheme.typography.h6,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )
    }

    @Composable
    fun ListPreference(
        key: String,
        title: String,
        summary: String,
        entries: Array<String>,
        entryValues: Array<String>,
        defaultValue: String,
        sharedPreferences: android.content.SharedPreferences
    ) {
        var selectedValue by remember { mutableStateOf(sharedPreferences.getString(key, defaultValue) ?: defaultValue) }
        val selectedEntry = entries.getOrNull(entryValues.indexOf(selectedValue)) ?: summary
        var showDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle1)
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(text = selectedEntry, style = MaterialTheme.typography.body2)
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(title) },
                text = {
                    Column {
                        entries.forEachIndexed { index, entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedValue == entryValues[index],
                                        onClick = {
                                            selectedValue = entryValues[index]
                                            sharedPreferences
                                                .edit()
                                                .putString(key, selectedValue)
                                                .apply()
                                            showDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedValue == entryValues[index],
                                    onClick = null
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(text = entry, style = MaterialTheme.typography.body1)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun CheckBoxPreference(
        key: String,
        title: String,
        summary: String,
        defaultValue: Boolean,
        sharedPreferences: android.content.SharedPreferences
    ) {
        var isChecked by remember { mutableStateOf(sharedPreferences.getBoolean(key, defaultValue)) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isChecked = !isChecked
                    sharedPreferences.edit().putBoolean(key, isChecked).apply()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.subtitle1)
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(text = summary, style = MaterialTheme.typography.body2)
                }
            }
            Checkbox(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    sharedPreferences.edit().putBoolean(key, it).apply()
                }
            )
        }
    }

    @Composable
    fun EditTextPreference(
        key: String,
        title: String,
        summary: String,
        defaultValue: String,
        sharedPreferences: android.content.SharedPreferences,
        keyboardType: KeyboardType = KeyboardType.Text
    ) {
        var value by remember { mutableStateOf(sharedPreferences.getString(key, defaultValue) ?: defaultValue) }
        var showDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle1)
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(text = value.ifEmpty { summary }, style = MaterialTheme.typography.body2)
            }
        }

        if (showDialog) {
            var textValue by remember { mutableStateOf(value) }
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(title) },
                text = {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            value = textValue
                            sharedPreferences.edit().putString(key, value).apply()
                            showDialog = false
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun Preference(
        title: String,
        summary: String,
        onClick: () -> Unit = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle1)
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(text = summary, style = MaterialTheme.typography.body2)
            }
        }
    }
}
