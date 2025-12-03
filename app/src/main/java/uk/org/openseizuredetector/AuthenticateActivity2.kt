package uk.org.openseizuredetector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class AuthenticateActivity2 : AppCompatActivity() {
    private val TAG = "AuthenticateActivity"
    private lateinit var mUtil: OsdUtil
    private var mUnameEt: EditText? = null
    private var mPasswdEt: EditText? = null
    private var mConnection: SdServiceConnection? = null
    val serverStatusHandler = Handler()
    private var mWac: WebApiConnection? = null
    private var mLm: LogManager? = null

    companion object {
        private const val TOKEN_ID = "webApiAuthToken"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authenticate)

        mUtil = OsdUtil(applicationContext, serverStatusHandler)
        if (!mUtil.isServerRunning) {
            mUtil.showToast(getString(R.string.error_server_not_running))
            finish()
            return
        }

        val cancelBtn = findViewById<Button>(R.id.cancelBtn)
        cancelBtn.setOnClickListener(onCancel)
        val loginBtn = findViewById<Button>(R.id.loginBtn)
        loginBtn.setOnClickListener(onLogin)
        val logoutCancelBtn = findViewById<Button>(R.id.logoutCancelBtn)
        logoutCancelBtn.setOnClickListener(onCancel)
        val logoutBtn = findViewById<Button>(R.id.logoutBtn)
        logoutBtn.setOnClickListener(onLogout)

        // Components required only for osdapi backend
        if (LogManager.USE_FIREBASE_BACKEND) {
        } else {
            mConnection = SdServiceConnection(applicationContext)
            val registerBtn = findViewById<Button>(R.id.RegisterBtn)
            registerBtn.setOnClickListener(onRegister)
            val resetPasswordBtn = findViewById<Button>(R.id.ResetPasswordBtn)
            resetPasswordBtn.setOnClickListener(onResetPassword)
            mUnameEt = findViewById(R.id.username)
            mPasswdEt = findViewById(R.id.password)
        }

        val aboutDataSharingBtn = findViewById<Button>(R.id.aboutDataSharingBtn)
        aboutDataSharingBtn.setOnClickListener {
            Log.v(TAG, "aboutDataSharingBtn.onClick()")
            val url = OsdUtil.DATA_SHARING_URL
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        }
        val privacyPolicyBtn = findViewById<Button>(R.id.privacyPolicyBtn)
        privacyPolicyBtn.setOnClickListener {
            Log.v(TAG, "privacyPolicyBtn.onClick()")
            val url = OsdUtil.PRIVACY_POLICY_URL
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        }
    }

    override fun onStart() {
        Log.d(TAG, "onStart()")
        super.onStart()
        if (LogManager.USE_FIREBASE_BACKEND) {
            updateUi()
        } else {
            mUtil.bindToServer(applicationContext, mConnection)
            waitForConnection()
        }
    }

    override fun onStop() {
        Log.d(TAG, "onStop()")
        super.onStop()
        if (LogManager.USE_FIREBASE_BACKEND) {
        } else {
            mUtil.unbindFromServer(applicationContext, mConnection)
        }
    }

    private fun waitForConnection() {
        // We want the UI to update as soon as it is displayed, but it takes a finite time for
        // the mConnection to bind to the service, so we delay half a second to give it chance
        // to connect before trying to update the UI for the first time (it happens again periodically using the uiTimer)
        if (mConnection?.mBound == true) {
            Log.v(TAG, "waitForConnection - Bound!")
            initialiseServiceConnection()
        } else {
            Handler().postDelayed({ waitForConnection() }, 100)
        }
    }

    private fun initialiseServiceConnection() {
        Log.v(TAG, "initialiseServiceConnection()")
        mLm = mConnection?.mSdServer?.mLm
        mWac = LogManager.mWac
        updateUi()
    }

    // Called after the Firebase Auth UI has completed
    private val signInLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { result ->
        Log.i(TAG, "FirebaseAuthUIActivityResult - $result")
        updateUi()
    }

private fun updateUi() {
    Log.v(TAG, "updateUi()")
    val loginLl = findViewById<LinearLayout>(R.id.login_ui)
    val osdApiLoginLl = findViewById<LinearLayout>(R.id.login_osdapi_ui)
    val logoutLl = findViewById<LinearLayout>(R.id.logout_ui)

    // Use a safe call (?.) and a 'let' block
    mWac?.let { wac ->
        if (wac.isLoggedIn) {
            Log.v(TAG, "Already Logged in - showing Log Out prompt")
            loginLl.visibility = View.GONE
            logoutLl.visibility = View.VISIBLE
            if (!LogManager.USE_FIREBASE_BACKEND) {
                osdApiLoginLl.visibility = View.GONE
            }
            wac.getUserProfile { profileObj ->
                try {
                    val userId = profileObj.getString("id")
                    val userName = profileObj.getString("username")
                    val tv2 = findViewById<TextView>(R.id.userIdTv)
                    tv2.text = userId
                    val tv3 = findViewById<TextView>(R.id.usernameTv)
                    tv3.text = userName
                } catch (e: JSONException) {
                    Log.e(TAG, "Error Parsing profileObj: " + e.message)
                    mUtil.showToast("Error Parsing profileObj - this should not happen!!!")
                } catch (e: NullPointerException) {
                    Log.e(TAG, "Error Retrieving User Information: " + e.message)
                    mUtil.showToast("Error Reading User Information - please try later.")
                }
            }
        } else {
            Log.v(TAG, "updateUi() - not logged in..")
            loginLl.visibility = View.VISIBLE
            logoutLl.visibility = View.GONE
            if (!LogManager.USE_FIREBASE_BACKEND) {
                osdApiLoginLl.visibility = View.VISIBLE
            }
        }
    } ?: run {
        // This block will run if mWac is null
        Log.i(TAG, "mWac is null - not updating UI")
    }
}

    var onCancel = View.OnClickListener {
        Log.v(TAG, "onCancel")
        finish()
    }

    var onLogin = View.OnClickListener {
        if (LogManager.USE_FIREBASE_BACKEND) {
            Log.v(TAG, "onLogin() - using Firebase Login")
            val signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(
                    Arrays.asList(
                        AuthUI.IdpConfig.GoogleBuilder().build(),
                        AuthUI.IdpConfig.EmailBuilder().build()
                    )
                )
                .build()
            signInLauncher.launch(signInIntent)
        } else {
            // Use Username and password authentication for OSDAPI.
            // FIXME - make this work with Google Authentication like we do for Firebase.
            val uname = mUnameEt?.text.toString()
            val passwd = mPasswdEt?.text.toString()
            Log.v(TAG, "onOK() - uname=$uname, passwd=$passwd")
            mWac?.authenticate(uname, passwd, object : WebApiConnection.StringCallback {
                override fun accept(retVal: String?) {
                    if (retVal != null) {
                        Log.d(TAG, "Authentication Success - token is $retVal")
                        mUtil.showToast("Login Successful")
                        saveAuthToken(retVal)
                        updateUi()
                    } else {
                        Log.e(TAG, "onOk: Authentication failure for $uname, $passwd")
                        mUtil.showToast("ERROR: Authentication Failed - Please Try Again")
                        mUtil.writeToSysLogFile("AuthActivity - Authorisation failed for $uname, $passwd")
                    }
                }
            })
        }
    }

    var onLogout = View.OnClickListener {
        Log.v(TAG, "onLogout")
        if (LogManager.USE_FIREBASE_BACKEND) {
            AuthUI.getInstance()
                .signOut(applicationContext)
                .addOnCompleteListener { // user is now signed out
                    updateUi()
                }
        } else {
            val wac = mWac
            if (wac != null) {
                wac.logout()
                saveAuthToken(null)
            } else {
                Log.e(TAG, "logout() - mWac is null - not doing anything")
            }
        }
        updateUi()
    }

    var onRegister = View.OnClickListener {
        Log.d(TAG, "onRegisterBtn")
        val url = "https://osdapi.ddns.net/static/register.html"
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }

    var onResetPassword = View.OnClickListener {
        Log.d(TAG, "onResetPasswordBtn")
        val url = "https://osdapi.ddns.net/static/request_password_reset.html"
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }

    private fun saveAuthToken(tokenStr: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefs.edit().putString(TOKEN_ID, tokenStr).apply()
        mWac?.setStoredToken(tokenStr)
    }

    fun getAuthToken(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return prefs.getString(TOKEN_ID, null)
    }
}