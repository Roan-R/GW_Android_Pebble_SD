/*
  Android_Pebble_sd - Android alarm client for openseizuredetector..

  See http://openseizuredetector.org for more information.

  Copyright Graham Jones, 2015, 2016

  This file is part of pebble_sd.

  Android_Pebble_sd is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Android_Pebble_sd is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with Android_pebble_sd.  If not, see <http://www.gnu.org/licenses/>.

*/
package uk.org.openseizuredetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import static java.lang.Math.sqrt;


/**
 * A data source that uses the accelerometer built into the phone to provide seizure detector data for testing purposes.
 * Note that this is unlikely to be useable as a viable seizure detector because the phone must be firmly attached to the part of the body that
 * will shake during a seizure.
 */
public class SdDataSourcePhone extends SdDataSource implements SensorEventListener {
    private String TAG = "SdDataSourcePhone";

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private int mMode = 0;   // 0=check data rate, 1=running
    private SensorEvent mStartEvent = null;
    private long mStartTs = 0;

    private boolean mUseNextSample = true;


    public SdDataSourcePhone(Context context, Handler handler,
                             SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "Phone";
        // Set default settings from XML files (mContext is set by super().
        PreferenceManager.setDefaultValues(mContext,
                R.xml.network_passive_datasource_prefs, true);
    }

    @Override
    public void updatePrefs() {
        Log.i(TAG, "updatePrefs()");
        super.updatePrefs(); // This loads mFallActive and other common settings
    }


    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        Log.i(TAG, "start()");
        mUtil.writeToSysLogFile("SdDataSourcePhone.start()");
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
        super.start();
    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.i(TAG, "stop()");
        mUtil.writeToSysLogFile("SdDataSourcePhone.stop()");
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }

        super.stop();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // we initially start in mMode=0, which calculates the sample frequency returned by the sensor, then enters mMode=1, which is normal operation.
            if (mMode == 0) {
                if (mStartEvent == null) {
                    Log.v(TAG, "onSensorChanged(): mMode=0 - Starting Sample Rate Check");
                    mStartEvent = event;
                    mStartTs = event.timestamp;
                    mSdData.mNsamp = 0;
                } else {
                    mSdData.mNsamp++;
                }
                if (mSdData.mNsamp >= 250) {
                    double dT = 1e-9 * (event.timestamp - mStartTs);
                    mSdData.mSampleFreq = (int) (mSdData.mNsamp / dT);
                    mSdData.haveSettings = true;
                    Log.i(TAG, "onSensorChanged(): Calculated sample rate as " + mSdData.mSampleFreq + " Hz");
                    mMode = 1;
                    mSdData.mNsamp = 0;
                    mStartTs = event.timestamp;
                }
            } else if (mMode == 1) {
                // The phone gives us ~50 Hz sample frequency so we do a factor of 2 downsampling to get ~25Hz.
                if (mUseNextSample) {
                    mUseNextSample = false;
                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[2];
                    
                    // Convert magnitude from m/s^2 to milli-g (1g = 1000 mg)
                    double mag = 1000.0 * sqrt(x * x + y * y + z * z) / 9.81;
                    
                    mSdData.rawData[mSdData.mNsamp] = mag;
                    mSdData.rawData3D[3 * mSdData.mNsamp] = 1000.0f * x / 9.81f;
                    mSdData.rawData3D[3 * mSdData.mNsamp + 1] = 1000.0f * y / 9.81f;
                    mSdData.rawData3D[3 * mSdData.mNsamp + 2] = 1000.0f * z / 9.81f;
                    
                    mSdData.mNsamp++;
                    
                    if (mSdData.mNsamp == mSdData.rawData.length) {
                        // Set HR and O2Sat values to fault value (-1) to avoid alarms.
                        mSdData.mHR = -1;
                        mSdData.mO2Sat = -1;
                        doAnalysis();
                        mSdData.mNsamp = 0;
                        mStartTs = event.timestamp;
                    }
                } else {
                    mUseNextSample = true;
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.v(TAG, "onAccuracyChanged()");
    }
}
