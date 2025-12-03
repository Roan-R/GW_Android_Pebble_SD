package uk.org.openseizuredetector

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import uk.org.openseizuredetector.ui.theme.OpenSeizureDetectorTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class LogManagerComposeActivity : ComponentActivity() {

    private val TAG = "LogManagerCompose"

    // User Color Palette
    private val MyBackground = Color(0xFFD7FFF1)
    private val MyBrown = Color(0xFF664E4C)
    private val MyCharcoal = Color(0xFF172121)
    private val MyTeal = Color(0xFF6A8D92)
    private val MyBeige = Color(0xFFE2D0B6)
    private val MyWhite = Color.White

    private lateinit var mUtil: OsdUtil
    private lateinit var mConnection: SdServiceConnection
    private var mLm: LogManager? = null

    // Data States
    private val remoteEventsList = mutableStateListOf<HashMap<String, String>>()
    private val localEventsList = mutableStateListOf<HashMap<String, String>>()
    private val sysLogList = mutableStateListOf<HashMap<String, String>>()
    private val localEventCount = mutableStateOf(0L)
    private val ndaTimeRemaining = mutableStateOf(0.0)
    private val authStatusText = mutableStateOf("Not Authenticated")
    private val isLoggedIn = mutableStateOf(false)

    // UI State
    private val selectedView = mutableIntStateOf(0) // 0=Shared, 1=Local, 2=SysLog
    private val groupEvents = mutableStateOf(true)
    private val includeWarnings = mutableStateOf(false)
    private val includeNda = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mUtil = OsdUtil(applicationContext, Handler(Looper.getMainLooper()))
        mConnection = SdServiceConnection(applicationContext)

        setContent {
            OpenSeizureDetectorTheme {
                // Apply background color to the whole screen
                Surface(color = MyBackground, modifier = Modifier.fillMaxSize()) {
                    LogManagerScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mUtil.bindToServer(applicationContext, mConnection)
        waitForConnection()
    }

    override fun onStop() {
        super.onStop()
        mUtil.unbindFromServer(applicationContext, mConnection)
    }

    private fun waitForConnection() {
        if (mConnection.mBound) {
            initialiseServiceConnection()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ waitForConnection() }, 100)
        }
    }

    private fun initialiseServiceConnection() {
        mLm = mConnection.mSdServer.mLm
        fetchData()
    }

    private fun fetchData() {
        // Fetch Local Stats
        mLm?.getLocalEventsCount(true) { count ->
            localEventCount.value = count
        }
        // NDA Time (Accessing public field directly)
        mLm?.let {
            ndaTimeRemaining.value = it.mNDATimeRemaining
        }

        // Fetch Lists
        fetchRemoteEvents()
        
        mLm?.getEventsList(true) { events ->
            localEventsList.clear()
            localEventsList.addAll(events)
        }
        mUtil.getSysLogList { logs ->
            sysLogList.clear()
            sysLogList.addAll(logs)
        }

        // Check Auth Status
        if (LogManager.mWac.isLoggedIn) {
            authStatusText.value = getString(R.string.logged_in_with_token)
            isLoggedIn.value = true
        } else {
            authStatusText.value = getString(R.string.not_authenticated)
            isLoggedIn.value = false
        }
    }

    private fun fetchRemoteEvents() {
        LogManager.mWac?.getEvents { remoteEventsObj: JSONObject? ->
            if (remoteEventsObj == null) {
                Log.e(TAG, "Error Retrieving events")
            } else {
                try {
                    val eventsArray = remoteEventsObj.getJSONArray("events")
                    val newEvents = ArrayList<HashMap<String, String>>()
                    
                    for (i in 0 until eventsArray.length()) {
                        val eventObj = eventsArray.getJSONObject(i)
                        val eventHashMap = HashMap<String, String>()
                        
                        val id = if (!eventObj.isNull("id")) eventObj.getString("id") else null
                        val osdAlarmState = if (!eventObj.isNull("osdAlarmState")) eventObj.getInt("osdAlarmState") else -1
                        val dataTime = if (!eventObj.isNull("dataTime")) eventObj.getString("dataTime") else "null"
                        val typeStr = if (!eventObj.isNull("type")) eventObj.getString("type") else "null"
                        val subType = if (!eventObj.isNull("subType")) eventObj.getString("subType") else "null"
                        val desc = if (!eventObj.isNull("desc")) eventObj.getString("desc") else "null"

                        eventHashMap["id"] = id ?: ""
                        eventHashMap["osdAlarmState"] = osdAlarmState.toString()
                        eventHashMap["osdAlarmStateStr"] = mUtil.alarmStatusToString(osdAlarmState)
                        eventHashMap["dataTime"] = dataTime
                        eventHashMap["type"] = typeStr
                        eventHashMap["subType"] = subType
                        eventHashMap["desc"] = desc

                        val isWarning = osdAlarmState == 1
                        val isNDA = osdAlarmState == 6

                        // Filter logic
                        if ((!isWarning || includeWarnings.value) && (!isNDA || includeNda.value)) {
                            newEvents.add(eventHashMap)
                        }
                    }

                    // Sort by date descending
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    newEvents.sortWith { event1, event2 ->
                        try {
                            val d1Str = event1["dataTime"]?.substringBefore(".") ?: ""
                            val d2Str = event2["dataTime"]?.substringBefore(".") ?: ""
                            val d1 = sdf.parse(d1Str)
                            val d2 = sdf.parse(d2Str)
                            d2?.compareTo(d1) ?: 0
                        } catch (e: Exception) {
                            0
                        }
                    }

                    remoteEventsList.clear()
                    remoteEventsList.addAll(newEvents)

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing remote events: ${e.message}")
                }
            }
        }
    }

    @Composable
    fun LogManagerScreen() {
        // LaunchedEffect to handle filter changes for Remote Events
        LaunchedEffect(includeWarnings.value, includeNda.value) {
            fetchRemoteEvents()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // --- Header Section (Always Visible) ---
            Text(
                text = stringResource(R.string.local_database),
                style = MaterialTheme.typography.h5,
                color = MyCharcoal
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Events Stored on Phone: ", color = MyCharcoal, fontWeight = FontWeight.Bold)
                Text("${localEventCount.value}", color = MyCharcoal)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NDA Time Remaining: ", color = MyCharcoal, fontWeight = FontWeight.Bold)
                Text(String.format("%.1f hrs", ndaTimeRemaining.value), color = MyCharcoal)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- View Selection (Radio Buttons) ---
            // Changed to Column for vertical stacking
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButtonWithLabel(
                    selected = selectedView.intValue == 0,
                    onClick = { selectedView.intValue = 0 },
                    label = stringResource(R.string.shared_data)
                )
                
                RadioButtonWithLabel(
                    selected = selectedView.intValue == 1,
                    onClick = { selectedView.intValue = 1 },
                    label = stringResource(R.string.local_data)
                )
                
                RadioButtonWithLabel(
                    selected = selectedView.intValue == 2,
                    onClick = { selectedView.intValue = 2 },
                    label = stringResource(R.string.system_logs)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MyTeal, thickness = 2.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // --- Content Section ---
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedView.intValue) {
                    0 -> SharedDataView()
                    1 -> LocalDataView()
                    2 -> SystemLogsView()
                }
            }
        }
    }

    @Composable
    fun RadioButtonWithLabel(selected: Boolean, onClick: () -> Unit, label: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MyTeal, unselectedColor = MyCharcoal)
            )
            Text(text = label, color = MyCharcoal, style = MaterialTheme.typography.body2)
        }
    }

    @Composable
    fun SharedDataView() {
        Column {
            Text(
                text = stringResource(R.string.remote_database),
                style = MaterialTheme.typography.h6,
                color = MyCharcoal
            )
            Text(
                text = stringResource(R.string.check_seizures_message),
                style = MaterialTheme.typography.caption,
                color = MyCharcoal,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Auth Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = authStatusText.value,
                    color = MyCharcoal,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.body2
                )
                Button(
                    onClick = { startActivity(Intent(applicationContext, AuthenticateActivity2::class.java)) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MyTeal),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(if (isLoggedIn.value) stringResource(R.string.logout) else stringResource(R.string.login), color = MyWhite)
                }
                Button(
                    onClick = { fetchData() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MyTeal)
                ) {
                    Text(stringResource(R.string.refreshBtn), color = MyWhite)
                }
            }

            // Filters - Spaced equally using weight
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterCheckbox(
                    checked = groupEvents.value,
                    onCheckedChange = { groupEvents.value = it },
                    label = stringResource(R.string.group_remote_events),
                    modifier = Modifier.weight(1f)
                )
                FilterCheckbox(
                    checked = includeWarnings.value,
                    onCheckedChange = { includeWarnings.value = it },
                    label = stringResource(R.string.include_warnings),
                    modifier = Modifier.weight(1f)
                )
                FilterCheckbox(
                    checked = includeNda.value,
                    onCheckedChange = { includeNda.value = it },
                    label = stringResource(R.string.include_nda),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(remoteEventsList) { event ->
                    RemoteEventItem(event)
                }
            }
        }
    }

    @Composable
    fun FilterCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = MyTeal, uncheckedColor = MyCharcoal)
            )
            Text(text = label, style = MaterialTheme.typography.caption, color = MyCharcoal)
        }
    }

    @Composable
    fun RemoteEventItem(event: HashMap<String, String>) {
        val type = event["type"]
        val cardColor = when (type) {
            "Seizure" -> Color(0xFFFF6060) // Keep red for seizure highlight
            "null", "" -> Color(0xFFFFAAAA) // Keep light red for invalid
            else -> MyBeige // Use user's Beige for standard items
        }

        Card(
            backgroundColor = cardColor,
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(event["dataTime"] ?: "", fontWeight = FontWeight.Bold, color = MyCharcoal)
                    Text(event["id"] ?: "", style = MaterialTheme.typography.caption, color = MyCharcoal)
                }
                Row {
                    Text(event["type"] ?: "---", fontWeight = FontWeight.Bold, color = MyCharcoal)
                    Text(" : ", color = MyCharcoal)
                    Text(event["subType"] ?: "---", color = MyCharcoal)
                }
                Text(event["osdAlarmStateStr"] ?: "", color = MyCharcoal)
                if (event["desc"] != "null" && event["desc"]?.isNotEmpty() == true) {
                    Text(event["desc"] ?: "", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MyCharcoal)
                }
            }
        }
    }

    @Composable
    fun LocalDataView() {
        Column {
            Text(
                text = stringResource(R.string.EventsInLocalDb),
                style = MaterialTheme.typography.h6,
                color = MyCharcoal
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(localEventsList) { event ->
                    Card(
                        backgroundColor = MyBeige,
                        shape = RoundedCornerShape(8.dp),
                        elevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(event["dataTime"] ?: "", fontWeight = FontWeight.Bold, color = MyCharcoal)
                            Row {
                                Text("Status: ", color = MyCharcoal)
                                Text(event["status"] ?: "", color = MyCharcoal)
                            }
                            Row {
                                Text("Uploaded: ", color = MyCharcoal)
                                Text(event["uploaded"] ?: "", color = MyCharcoal)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SystemLogsView() {
        Column {
            Text(
                text = stringResource(R.string.system_logs),
                style = MaterialTheme.typography.h6,
                color = MyCharcoal
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sysLogList) { log ->
                    Card(
                        backgroundColor = MyBeige,
                        shape = RoundedCornerShape(8.dp),
                        elevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(log["dataTime"] ?: "", fontWeight = FontWeight.Bold, color = MyCharcoal, style = MaterialTheme.typography.caption)
                                Text(log["logLevel"] ?: "", fontWeight = FontWeight.Bold, color = MyCharcoal, style = MaterialTheme.typography.caption)
                            }
                            Text(log["dataJSON"] ?: "", color = MyCharcoal, style = MaterialTheme.typography.body2)
                        }
                    }
                }
            }
        }
    }
}
