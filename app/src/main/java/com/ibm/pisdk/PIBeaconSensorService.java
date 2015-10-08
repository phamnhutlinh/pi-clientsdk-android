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

package com.ibm.pisdk;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.PriorityQueue;

public class PIBeaconSensorService extends Service implements BeaconConsumer {
    private static final String TAG = PIBeaconSensorService.class.getSimpleName();

    private static final String INTENT_PARAMETER_ADAPTER = "adapter";
    private static final String INTENT_PARAMETER_COMMAND = "command";
    private static final String INTENT_PARAMETER_DEVICE_ID = "device_id";
    private static final String INTENT_PARAMETER_BEACON_LAYOUT = "beacon_layout";
    private static final String INTENT_PARAMETER_SEND_INTERVAL = "send_interval";

    private static final String INTENT_RECEIVER_BEACON_COLLECTION = "intent_receiver_beacon_collection";

    private PIAPIAdapter mPiApiAdapter;
    private BeaconManager mBeaconManager;
    private RegionManager mRegionManager;
    private String mDeviceId;

    private volatile long sendInterval = 5000;
    private long lastSendTime = 0;
    private long currentTime = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String command;
        Bundle extras = null;
        if (intent != null) {
            extras = intent.getExtras();
        }

        // lazily instantiate beacon manager
        if (mBeaconManager == null) {
            mBeaconManager = BeaconManager.getInstanceForApplication(this);
        }
        // and region manager
        if (mRegionManager == null) {
            mRegionManager = new RegionManager(mBeaconManager);
        }

        // check passed in intent for commands sent from Beacon Sensor wrapper class
        if (extras != null) {
            if (extras.get(INTENT_PARAMETER_ADAPTER) != null) {
                mPiApiAdapter = (PIAPIAdapter) extras.get(INTENT_PARAMETER_ADAPTER);
            }
            if (!extras.getString(INTENT_PARAMETER_DEVICE_ID, "").equals("")) {
                mDeviceId = extras.getString(INTENT_PARAMETER_DEVICE_ID);
            }
            if (extras.getLong(INTENT_PARAMETER_SEND_INTERVAL, -1) > 0) {
                sendInterval = extras.getLong(INTENT_PARAMETER_SEND_INTERVAL);
            }
            if (!extras.getString(INTENT_PARAMETER_BEACON_LAYOUT, "").equals("")) {
                mBeaconManager.getBeaconParsers().add(new BeaconParser()
                        .setBeaconLayout(intent.getStringExtra(INTENT_PARAMETER_BEACON_LAYOUT)));
            }
            if (!extras.getString(INTENT_PARAMETER_COMMAND, "").equals("")) {
                command = extras.getString(INTENT_PARAMETER_COMMAND);
                if (command.equals("START_SCANNING")){
                    mBeaconManager.bind(this);
                } else if (command.equals("STOP_SCANNING")){
                    mBeaconManager.unbind(this);
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public void onBeaconServiceConnect() {
        mBeaconManager.setMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                // not used
            }

            @Override
            public void didExitRegion(Region region) {
                mRegionManager.remove(region);
            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                // not used
            }
        });

        mBeaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    for (Beacon b : beacons) {
                        mRegionManager.add(b);
                    }
                    currentTime = System.currentTimeMillis();
                    if (currentTime - lastSendTime > sendInterval) {
                        lastSendTime = currentTime;
                        sendBeaconNotification(beacons);
                    }
                }
            }
        });

        mPiApiAdapter.getProximityUUIDs(new PIAPICompletionHandler() {
            @Override
            public void onComplete(PIAPIResult result) {
                if (result.getResponseCode() == 200) {
                    JSONArray uuids = null;
                    try {
                        uuids = JSONArray.parse((String) result.getResult());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    if (uuids != null) {
                        for (Object uuid : uuids.toArray()) {
                            // this is temporary
                            // with only one uuid per org assumption in RegionManager
                            // we will only range in the last uuid in the list
                            mRegionManager.add((String) uuid);
                        }
                    } else {
                        Log.e(TAG, "Call to Management server returned an empty array of proximity UUIDs");
                    }
                } else {
                    // default estimote uuid
                    mRegionManager.add("b9407f30-f5f8-466e-aff9-25556b57fe6d");
                }
            }
        });
    }

    private void startMonitoringAndRangingBeaconsInRegion(Region region) {
        try {
            mBeaconManager.startMonitoringBeaconsInRegion(region);
            mBeaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException re) {
            re.printStackTrace();
        }
    }

    private void sendBeaconNotification(Collection<Beacon> beacons) {
        JSONObject payload = buildBeaconPayload(beacons);
        log("sending beacon notification message");
        mPiApiAdapter.sendBeaconNotificationMessage(payload, new PIAPICompletionHandler() {
            @Override
            public void onComplete(PIAPIResult result) {
                if (result.getResponseCode() >= HttpStatus.SC_BAD_REQUEST) {
                    log("something went wrong with sending the bnm");
                    log((String) result.getResult());
                }
            }
        });

        // provide beacons to delegate
        Intent intent = new Intent(INTENT_RECEIVER_BEACON_COLLECTION);
        intent.putParcelableArrayListExtra("beacons", new ArrayList<Beacon>(beacons));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private JSONObject buildBeaconPayload(Collection<Beacon> beacons) {
        long detectedTime = System.currentTimeMillis();
        JSONObject payload = new JSONObject();
        JSONArray beaconArray = new JSONArray();

        for (Beacon b : beacons) {
            PIBeaconData data = new PIBeaconData(b);
            data.setDetectedTime(detectedTime);
            data.setDeviceDescriptor(mDeviceId);
            beaconArray.add(data.getBeaconAsJson());
        }
        payload.put("bnm", beaconArray);

        log(payload.toString());

        return payload;
    }

    @Override
    public void onDestroy() {
        mBeaconManager.unbind(this);
        super.onDestroy();
    }

    private void log(String msg) {
        Log.i(TAG, msg);
    }
}
