/**
 * Copyright (c) 2015 IBM Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.ibm.pi.beacon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.ibm.pi.core.Constants;
import com.ibm.pi.core.PIAPIAdapter;
import com.ibm.pi.core.PILogger;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.Region;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

/**
 * This class wraps the AltBeacon library's BeaconConsumer, and provides a simple interface to handle
 * the communication to the PIBeaconSensorService.
 *
 * @author Ciaran Hannigan (cehannig@us.ibm.com)
 */
public class PIBeaconSensor {
    private static final String TAG = PIBeaconSensor.class.getSimpleName();

    protected static final String ADAPTER_KEY = "com.ibm.pisdk.adapter";
    protected static final String BEACON_LAYOUT_KEY = "com.ibm.pisdk.beacon_layout";
    protected static final String SEND_INTERVAL_KEY = "com.ibm.pisdk.send_interval";
    protected static final String BACKGROUND_SCAN_PERIOD_KEY = "com.ibm.pisdk.background_scan_period";
    protected static final String BACKGROUND_BETWEEN_SCAN_PERIOD_KEY = "com.ibm.pisdk.background_between_scan_interval";
    protected static final String SENSOR_STATE_KEY = "com.ibm.pisdk.sensor_state";
    protected static final String UUID_KEY = "com.ibm.pisdk.uuid_key";
    protected static final String START_IN_BACKGROUND_KEY = "com.ibm.pisdk.start_in_background";

    public static final String INTENT_RECEIVER_BEACON_COLLECTION = "intent_receiver_beacon_collection";
    public static final String INTENT_RECEIVER_REGION_ENTER = "intent_receiver_region_enter";
    public static final String INTENT_RECEIVER_REGION_EXIT = "intent_receiver_region_exit";

    public static final String INTENT_ACTION_START = "com.ibm.pisdk.START";
    public static final String INTENT_ACTION_STOP = "com.ibm.pisdk.STOP";
    public static final String INTENT_EXTRA_BEACONS_IN_RANGE = "com.ibm.pisdk.beacons_in_range";
    public static final String INTENT_EXTRA_ENTER_REGION = "com.ibm.pisdk.enter_region";
    public static final String INTENT_EXTRA_EXIT_REGION = "com.ibm.pisdk.exit_region";

    private BluetoothAdapter mBluetoothAdapter;
    private final Context mContext;
    private final PIAPIAdapter mAdapter;
    private SharedPreferences mPrefs;
    private boolean startSensorInBackgroundMode = false;

    private String mState;
    protected static final String STARTED = "started";
    protected static final String STOPPED = "stopped";

    /**
     * This interface provides a callback for beacons within range of the device.
     */
    public interface BeaconsInRangeListener {
        /**
         * Provides a collection of beacons within range
         *
         * @param beacons collection of Class Beacon.
         */
        void beaconsInRange(ArrayList<Beacon> beacons);
    }

    private BeaconsInRangeListener mBeaconsInRangeListener;

    public void setBeaconsInRangeListener(BeaconsInRangeListener listener) {
        mBeaconsInRangeListener = listener;
    }

    /**
     * This interface provides region event callbacks.
     */
    public interface RegionEventListener {
        /**
         * The device has entered a region
         *
         * @param region
         */
        void didEnterRegion(Region region);

        /**
         * The device has exited a region
         *
         * @param region
         */
        void didExitRegion(Region region);
    }

    private RegionEventListener mRegionEventListener;

    public void setRegionEventListener(RegionEventListener listener) {
        mRegionEventListener = listener;
    }

    private static PIBeaconSensor sInstance;

    /**
     * Default singleton constructor
     *
     * @param context Activity context
     * @param adapter to handle sending of the beacon notification message
     * @see com.ibm.pi.core.PIAPIAdapter
     */
    public static PIBeaconSensor getInstance(Context context, PIAPIAdapter adapter) {
        if (sInstance == null) {
            // Always pass in the Application Context
            sInstance = new PIBeaconSensor(context.getApplicationContext(), adapter);
        }

        return sInstance;
    }

    private PIBeaconSensor(Context context, PIAPIAdapter adapter) {
        mContext = context;
        mPrefs = context.getSharedPreferences(Constants.PI_SHARED_PREFS, Context.MODE_PRIVATE);
        mState = mPrefs.getString(SENSOR_STATE_KEY, STOPPED);

        // If the adapter is being passed in as null, this is a request from the BOOT_COMPLETED broadcast receiver.
        // We will restore previous sensor state
        if (adapter == null) {
            startSensorInBackgroundMode = true;
            mAdapter = retrievePIAPIAdapter(context);
        } else {
            mAdapter = adapter;
            savePIAPIAdapter(context, adapter);
        }

        // set listeners to null
        mBeaconsInRangeListener = null;
        mRegionEventListener = null;

        try {

            // If BLE isn't supported on the device we cannot proceed.
            if (!checkSupportBLE()) {
                throw new Exception("Device does not support BLE");
            }

            // Make sure to have reference to the bluetooth adapter, otherwise - retrieve it from the system.
            initBluetoothAdapter();

            // Make sure that BLE is on.
            if (!isBLEOn()) {
                // If BLE is off, turned it on
                if(!enableBLE()) {
                    PILogger.e(TAG, "Failed to start Bluetooth on this device.");
                }
            }

        } catch (Exception e){
            PILogger.e(TAG, "Failed to create PIBeaconSensorService: " + e.getMessage());
        }

        if (STARTED.equals(mState)) {
            start();
        }
    }

    /**
     * Start sensing for beacons.
     */
    public void start() {
        mState = STARTED;
        mPrefs.edit().putString(SENSOR_STATE_KEY, mState).apply();

        // Register to receive messages.
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mMessageReceiver,
                new IntentFilter(INTENT_RECEIVER_BEACON_COLLECTION));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mMessageReceiver,
                new IntentFilter(INTENT_RECEIVER_REGION_ENTER));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mMessageReceiver,
                new IntentFilter(INTENT_RECEIVER_REGION_EXIT));

        Intent intent = new Intent(mContext, PIBeaconSensorService.class);
        intent.setAction(INTENT_ACTION_START);
        intent.putExtras(getBundle());
        mContext.startService(intent);
    }

    private Bundle getBundle() {
        Bundle extras = new Bundle();
        extras.putSerializable(ADAPTER_KEY, mAdapter);
        extras.putLong(SEND_INTERVAL_KEY, mPrefs.getLong(SEND_INTERVAL_KEY, 5000l));
        extras.putLong(BACKGROUND_BETWEEN_SCAN_PERIOD_KEY, mPrefs.getLong(BACKGROUND_BETWEEN_SCAN_PERIOD_KEY, 60000l));
        extras.putLong(BACKGROUND_SCAN_PERIOD_KEY, mPrefs.getLong(BACKGROUND_SCAN_PERIOD_KEY, 1100l));
        if (mPrefs.contains(BEACON_LAYOUT_KEY)) {
            extras.putString(BEACON_LAYOUT_KEY, mPrefs.getString(BEACON_LAYOUT_KEY, ""));
        }
        if (startSensorInBackgroundMode) {
            extras.putBoolean(START_IN_BACKGROUND_KEY, true);
            startSensorInBackgroundMode = false;
        }

        return extras;
    }

    /**
     * Stop sensing for beacons.
     */
    public void stop() {
        mState = STOPPED;
        mPrefs.edit().putString(SENSOR_STATE_KEY, mState).apply();

        // Unregister receiver.
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mMessageReceiver);

        Intent intent = new Intent(mContext, PIBeaconSensorService.class);
        intent.setAction(INTENT_ACTION_STOP);
        mContext.startService(intent);
    }

    /**
     * Sets the interval in which the device reports its location.
     *
     * @param sendInterval send interval in ms
     */
    public void setSendInterval(long sendInterval) {
        mPrefs.edit().putLong(PIBeaconSensor.SEND_INTERVAL_KEY, sendInterval).apply();

        Intent intent = new Intent(mContext, PIBeaconSensorService.class);
        intent.putExtra(SEND_INTERVAL_KEY, sendInterval);
        mContext.startService(intent);
    }

    /**
     * Sets the duration in milliseconds spent not scanning between each Bluetooth LE scan cycle when no ranging/monitoring clients are in the foreground.
     *
     * @param scanPeriod time in ms
     */
    public void setBackgroundScanPeriod(long scanPeriod) {
        mPrefs.edit().putLong(PIBeaconSensor.SEND_INTERVAL_KEY, scanPeriod).apply();

        Intent intent = new Intent(mContext, PIBeaconSensorService.class);
        intent.putExtra(BACKGROUND_SCAN_PERIOD_KEY, scanPeriod);
        mContext.startService(intent);
    }

    /**
     * Sets the duration in milliseconds of each Bluetooth LE scan cycle to look for beacons.
     *
     * @param betweenScanPeriod time in ms
     */
    public void setBackgroundBetweenScanPeriod(long betweenScanPeriod) {
        mPrefs.edit().putLong(PIBeaconSensor.SEND_INTERVAL_KEY, betweenScanPeriod).apply();

        Intent intent = new Intent(mContext, PIBeaconSensorService.class);
        intent.putExtra(BACKGROUND_BETWEEN_SCAN_PERIOD_KEY, betweenScanPeriod);
        mContext.startService(intent);
    }

    /**
     * Adds a new beacon advertisement layout.  By default, the AltBeacon library will only detect
     * beacons meeting the AltBeacon specification.  Please see AltBeacon's BeaconParser#setBeaconLayout
     * for a solid explanation of BLE advertisements.
     *
     * The beacon layout for iBeacons is "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
     *
     * MUST BE CALLED BEFORE start()!
     *
     * @param beaconLayout the layout of the BLE advertisement
     */
    public void addBeaconLayout(String beaconLayout) {
        mPrefs.edit().putString(BEACON_LAYOUT_KEY, beaconLayout).apply();

        Intent intent = new Intent(mContext, PIBeaconSensorService.class);
        intent.putExtra(BEACON_LAYOUT_KEY, beaconLayout);
        mContext.startService(intent);
    }

    public String getState() {
        return mState;
    }

    // Local broadcast receiver to handle callback
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                // beacons in range
                if (INTENT_RECEIVER_BEACON_COLLECTION.equals(intent.getAction())) {
                    if (mBeaconsInRangeListener != null) {
                        PILogger.d(TAG, "received callback for beacons in range from sensor service.");
                        // Get extra data included in the Intent
                        ArrayList<Beacon> beacons = intent.getParcelableArrayListExtra(INTENT_EXTRA_BEACONS_IN_RANGE);
                        mBeaconsInRangeListener.beaconsInRange(beacons);
                    }
                }
                // region entered
                else if (INTENT_RECEIVER_REGION_ENTER.equals(intent.getAction())) {
                    PILogger.d(TAG, "received callback for region entered from sensor service.");
                    if (mRegionEventListener != null) {
                        Region enterRegion = (Region) intent.getExtras().get(INTENT_EXTRA_ENTER_REGION);
                        mRegionEventListener.didEnterRegion(enterRegion);
                    }
                }
                // region exited
                else if (INTENT_RECEIVER_REGION_EXIT.equals(intent.getAction())) {
                    PILogger.d(TAG, "received callback for region exited from sensor service.");
                    if (mRegionEventListener != null) {
                        Region exitRegion = (Region) intent.getExtras().get(INTENT_EXTRA_EXIT_REGION);
                        mRegionEventListener.didExitRegion(exitRegion);
                    }
                }
                // incorrect action received
                else {
                    PILogger.e(TAG, "incorrect action received, action received: " + intent.getAction());
                }
        }
    };

    // confirm if the device supports BLE, if not it can't be used for detecting beacons
    private  boolean checkSupportBLE(){
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            PILogger.e(TAG, "BLE is not supported for this device");
            return false;
        }
        return true;
    }

    // get the bluetooth adapter
    private void initBluetoothAdapter() throws Exception{
        if(mBluetoothAdapter == null) {
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
            if(mBluetoothAdapter == null)
                throw new Exception("Failed to get bluetooth adapter");
        }
    }

    // check to see if BLE is on
    private boolean isBLEOn(){
        return mBluetoothAdapter.isEnabled();
    }

    // enable bluetooth in case it's off (admin permission)
    private boolean enableBLE(){
        boolean response = true;
        if (!mBluetoothAdapter.isEnabled()) {
            response = false;
            mBluetoothAdapter.enable();
        }
        return response;
    }


    // state related methods

    private static void savePIAPIAdapter(Context context, PIAPIAdapter adapter) {
        ObjectOutputStream adapterStream = null;
        try {
            adapterStream = new ObjectOutputStream(
                    context.openFileOutput("piapiadapter.data", Context.MODE_PRIVATE));
            adapterStream.writeObject(adapter);
            adapterStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (adapterStream != null) {
                try {
                    adapterStream.flush();
                    adapterStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static PIAPIAdapter retrievePIAPIAdapter(Context context) {
        PIAPIAdapter adapter = null;
        ObjectInputStream adapterStream = null;
        try {
            adapterStream = new ObjectInputStream(
                    context.openFileInput("piapiadapter.data"));
            adapter = (PIAPIAdapter) adapterStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (adapterStream != null) {
                try {
                    adapterStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return adapter;
    }
}
