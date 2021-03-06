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

package com.ibm.pi.core;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Base64;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.pi.core.doctypes.PIBeacon;
import com.ibm.pi.core.doctypes.PIDevice;
import com.ibm.pi.core.doctypes.PIFloor;
import com.ibm.pi.core.doctypes.PIOrg;
import com.ibm.pi.core.doctypes.PISensor;
import com.ibm.pi.core.doctypes.PISite;
import com.ibm.pi.core.doctypes.PIZone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * This class provides an interface with the Presence Insights APIs.
 *
 * All methods return the API result as a String, unless otherwise specified.  I'm looking at you
 * getFloorMap.
 *
 * @author Ciaran Hannigan (cehannig@us.ibm.com)
 */
public class PIAPIAdapter implements Serializable {
    private static final String TAG = PIAPIAdapter.class.getSimpleName();

    private static final String MANAGEMENT_SERVER_PATH = "/pi-config/v1";
    private static final String MANAGEMENT_SERVER_PATH_v2 = "/pi-config/v2";
    private static final String BEACON_CONNECTOR_PATH = "/conn-beacon/v1";

    private static final String JSON_ROWS = "rows";
    private static final String JSON_FEATURES = "features";

    private static final int READ_TIMEOUT_IN_MILLISECONDS = 7000; /* milliseconds */
    private static final int CONNECTION_TIMEOUT_IN_MILLISECONDS = 7000; /* milliseconds */

    private final String mServerURL;
    private final String mServerURL_v2;
    private final String mConnectorURL;
    private final String mTenantCode;
    private final String mOrgCode;

    private final String mBasicAuth;

    /**
     * Constructor
     *
     * @param context Activity context
     * @param username username for tenant
     * @param password password for tenant
     * @param hostname url
     * @param tenantCode unique identifier for the tenant
     * @param orgCode unique identifier for the organization
     */
    public PIAPIAdapter(Context context, String username, String password, String hostname, String tenantCode, String orgCode) {
        mBasicAuth = generateBasicAuth(username, password);
        mServerURL = hostname + MANAGEMENT_SERVER_PATH;
        mServerURL_v2 = hostname + MANAGEMENT_SERVER_PATH_v2;
        mConnectorURL = hostname + BEACON_CONNECTOR_PATH;
        mTenantCode = tenantCode;
        mOrgCode = orgCode;
    }

    /**
     * Retrieves all the orgs of a tenant.  The tenant supplied in the PIAPIAdapter constructor.
     *
     * @param completionHandler callback for APIs asynchronous calls. Result returns as ArrayList&lt;{@link PIOrg PIOrg}&gt;.
     */
    public void getOrgs(final PIAPICompletionHandler completionHandler) {
        String orgs = String.format("%s/tenants/%s/orgs", mServerURL, mTenantCode);
        try {
            URL url = new URL(orgs);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        JSONObject orgObj = result.getResultAsJson();
                        JSONArray orgsArray = (JSONArray)orgObj.get(JSON_ROWS);
                        ArrayList<PIOrg> orgs = new ArrayList<PIOrg>();
                        for (Object org : orgsArray) {
                            orgs.add(new PIOrg((JSONObject) org));
                        }
                        result.setResult(orgs);
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves an org within a tenant.
     *
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link PIOrg PIOrg}.
     */
    public void getOrg(final PIAPICompletionHandler completionHandler) {
        String org = String.format("%s/tenants/%s/orgs/%s", mServerURL, mTenantCode, mOrgCode);
        try {
            URL url = new URL(org);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        result.setResult(new PIOrg(result.getResultAsJson()));
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves all the sites of an organization.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as ArrayList&lt;{@link PISite PISite}&gt;.
     */
    public void getSites(final PIAPICompletionHandler completionHandler) {
        String sites = String.format("%s/tenants/%s/orgs/%s/sites", mServerURL, mTenantCode, mOrgCode);
        try {
            URL url = new URL(sites);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        JSONObject siteObj = result.getResultAsJson();
                        JSONArray sitesArray = (JSONArray)siteObj.get(JSON_ROWS);
                        ArrayList<PISite> sites = new ArrayList<PISite>();
                        for (Object site : sitesArray) {
                            sites.add(new PISite((JSONObject) site));
                        }
                        result.setResult(sites);
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a site of an organization.
     *
     * @param siteCode unique identifier for the site.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link PISite PISite}.
     */
    public void getSite(String siteCode, final PIAPICompletionHandler completionHandler) {
        String site = String.format("%s/tenants/%s/orgs/%s/sites/%s", mServerURL, mTenantCode, mOrgCode, siteCode);
        try {
            URL url = new URL(site);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        result.setResult(new PISite(result.getResultAsJson()));
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves all the floors of a site.
     *
     * @param siteCode unique identifier for the site.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as ArrayList&lt;{@link PIFloor PIFloor}&gt;.
     */
    public void getFloors(String siteCode, final PIAPICompletionHandler completionHandler) {
        String floors = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors", mServerURL_v2, mTenantCode, mOrgCode, siteCode);
        try {
            URL url = new URL(floors);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        JSONObject floorObj = result.getResultAsJson();
                        JSONArray floorsArray = (JSONArray)floorObj.get(JSON_FEATURES);
                        ArrayList<PIFloor> floors = new ArrayList<PIFloor>();
                        for (Object floor : floorsArray) {
                            floors.add(new PIFloor((JSONObject) floor));
                        }
                        result.setResult(floors);
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a floor of a site.
     *
     * @param siteCode unique identifier for the site.
     * @param floorCode unique identifier for the floor.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link PIFloor PIFloor}.
     */
    public void getFloor(String siteCode, String floorCode, final PIAPICompletionHandler completionHandler) {
        String floor = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors/%s", mServerURL_v2, mTenantCode, mOrgCode, siteCode, floorCode);
        try {
            URL url = new URL(floor);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        result.setResult(new PIFloor(result.getResultAsJson()));
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves all devices of an organization.
     *
     * @param completionHandler callback for APIs asynchronous calls. Result returns as ArrayList&lt;{@link PIDevice PIDevice}&gt;.
     */
    public void getDevices(final PIAPICompletionHandler completionHandler) {
        String devices = String.format("%s/tenants/%s/orgs/%s/devices", mServerURL, mTenantCode, mOrgCode);
        try {
            URL url = new URL(devices);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        JSONObject deviceObj = result.getResultAsJson();
                        JSONArray devicesArray = (JSONArray)deviceObj.get(JSON_ROWS);
                        ArrayList<PIDevice> devices = new ArrayList<PIDevice>();
                        for (Object org : devicesArray) {
                            devices.add(new PIDevice((JSONObject) org));
                        }
                        result.setResult(devices);
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a device within an organization.
     *
     * @param deviceCode unique identifier for the device.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link PIDevice PIDevice}.
     */
    public void getDevice(String deviceCode, final PIAPICompletionHandler completionHandler) {
        String device = String.format("%s/tenants/%s/orgs/%s/devices/%s", mServerURL, mTenantCode, mOrgCode, deviceCode);
        try {
            URL url = new URL(device);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        result.setResult(new PIDevice(result.getResultAsJson()));
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a device within an organization by its descriptor.
     *
     * @param deviceDescriptor unique identifier for the device.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link PIDevice PIDevice}.
     *
     * @deprecated use {@link #getDevice(String, PIAPICompletionHandler)}. When you register a device,
     * the method will return a PIDevice object that contains the device documents code.
     */
    @Deprecated
    public void getDeviceByDescriptor(String deviceDescriptor, final PIAPICompletionHandler completionHandler) {
        String device = String.format("%s/tenants/%s/orgs/%s/devices?rawDescriptor=%s", mServerURL, mTenantCode, mOrgCode, deviceDescriptor);
        try {
            URL url = new URL(device);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        JSONArray matchingDevices = (JSONArray)result.getResultAsJson().get(JSON_ROWS);
                        if (matchingDevices.size() > 0) {
                            result.setResult(new PIDevice((JSONObject) matchingDevices.get(0)));
                        }
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves all the zones on a floor.
     *
     * @param siteCode unique identifier for the site.
     * @param floorCode unique identifier for the floor.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as ArrayList&lt;{@link PIZone PIZone}&gt;.
     */
    public void getZones(String siteCode, String floorCode, final PIAPICompletionHandler completionHandler) {
        String zones = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors/%s/zones", mServerURL_v2, mTenantCode, mOrgCode, siteCode, floorCode);
        try {
            URL url = new URL(zones);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        JSONObject zoneObj = result.getResultAsJson();
                        JSONArray zonesArray = (JSONArray)zoneObj.get(JSON_FEATURES);
                        ArrayList<PIZone> zones = new ArrayList<PIZone>();
                        for (Object zone : zonesArray) {
                            zones.add(new PIZone((JSONObject) zone));
                        }
                        result.setResult(zones);
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a zone on a floor.
     *
     * @param siteCode unique identifier for the site.
     * @param floorCode unique identifier for the floor.
     * @param zoneCode unique identifier for the zone.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link PIZone PIZone}.
     */
    public void getZone(String siteCode, String floorCode, String zoneCode, final PIAPICompletionHandler completionHandler) {
        String zone = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors/%s/zones/%s", mServerURL_v2, mTenantCode, mOrgCode, siteCode, floorCode, zoneCode);
        try {
            URL url = new URL(zone);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        result.setResult(new PIZone(result.getResultAsJson()));
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves all the beacons on a floor
     *
     * @param siteCode unique identifier for the site.
     * @param floorCode unique identifier for the floor.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as ArrayList&lt;{@link PIBeacon PIBeacon}&gt;.
     */
    public void getBeacons(String siteCode, String floorCode, final PIAPICompletionHandler completionHandler) {
        String beacons = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors/%s/beacons", mServerURL_v2, mTenantCode, mOrgCode, siteCode, floorCode);
        try {
            URL url = new URL(beacons);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        JSONObject beaconObj = result.getResultAsJson();
                        JSONArray beaconsArray = (JSONArray)beaconObj.get(JSON_FEATURES);
                        ArrayList<PIBeacon> beacons = new ArrayList<PIBeacon>();
                        for (Object beacon : beaconsArray) {
                            beacons.add(new PIBeacon((JSONObject) beacon));
                        }
                        result.setResult(beacons);
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a beacon on a floor.
     *
     * @param siteCode unique identifier for the site.
     * @param floorCode unique identifier for the floor.
     * @param beaconCode unique identifier for the beacon.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link PIBeacon PIBeacon}.
     */
    public void getBeacon(String siteCode, String floorCode, String beaconCode, final PIAPICompletionHandler completionHandler) {
        String beacon = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors/%s/beacons/%s", mServerURL_v2, mTenantCode, mOrgCode, siteCode, floorCode, beaconCode);
        try {
            URL url = new URL(beacon);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        result.setResult(new PIBeacon(result.getResultAsJson()));
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves all the sensors on a floor.
     *
     * @param siteCode unique identifier for the site.
     * @param floorCode unique identifier for the floor.
     * @param completionHandler callback for APIs asynchronous calls.  Result returns as ArrayList&lt;{@link PISensor PISensor}&gt;.
     */
    public void getSensors(String siteCode, String floorCode, final PIAPICompletionHandler completionHandler) {
        String sensors = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors/%s/sensors", mServerURL_v2, mTenantCode, mOrgCode, siteCode, floorCode);
        try {
            URL url = new URL(sensors);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        JSONObject sensorObj = result.getResultAsJson();
                        JSONArray sensorsArray = (JSONArray)sensorObj.get(JSON_FEATURES);
                        ArrayList<PISensor> sensors = new ArrayList<PISensor>();
                        for (Object sensor : sensorsArray) {
                            sensors.add(new PISensor((JSONObject) sensor));
                        }
                        result.setResult(sensors);
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a sensor on a floor.
     *
     * @param siteCode unique identifier for the site.
     * @param floorCode unique identifier for the floor.
     * @param sensorCode unique identifier for the sensor.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link PISensor PISensor}.
     */
    public void getSensor(String siteCode, String floorCode, String sensorCode, final PIAPICompletionHandler completionHandler) {
        String sensor = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors/%s/sensors/%s", mServerURL_v2, mTenantCode, mOrgCode, siteCode, floorCode, sensorCode);
        try {
            URL url = new URL(sensor);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        result.setResult(new PISensor(result.getResultAsJson()));
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the map image of a floor.  Returned as a Bitmap.
     *
     * @param siteCode unique identifier for the site.
     * @param floorCode unique identifier for the floor.
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link android.graphics.Bitmap Bitmap}.
     */
    public void getFloorMap(String siteCode, String floorCode, PIAPICompletionHandler completionHandler) {
        String map = String.format("%s/tenants/%s/orgs/%s/sites/%s/floors/%s/map", mServerURL, mTenantCode, mOrgCode, siteCode, floorCode);
        try {
            URL url = new URL(map);
            GET_IMAGE(url, completionHandler);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a list of proximity UUIDs from an organization.  Used for monitoring and ranging beacons in PIBeaconSensor.
     *
     * @param completionHandler callback for APIs asynchronous calls. Result returns as {@link ArrayList ArrayList}.
     */
    public void getProximityUUIDs(final PIAPICompletionHandler completionHandler) {
        String proximityUUIDs = String.format("%s/tenants/%s/orgs/%s/views/proximityUUID", mServerURL, mTenantCode, mOrgCode);
        try {
            URL url = new URL(proximityUUIDs);
            GET(url, new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult result) {
                    if (result.getResponseCode() == 200) {
                        try {
                            ArrayList<String> uuids = new ArrayList<String>();
                            JSONArray uuidArray = JSONArray.parse(result.getResultAsString());
                            for (Object uuid : uuidArray) {
                                uuids.add((String) uuid);
                            }
                            result.setResult(uuids);
                        } catch (IOException e) {
                            result.setException(e);
                            e.printStackTrace();
                        }
                    }
                    completionHandler.onComplete(result);
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Registers a device within an organization. If it already exists, this will still go through and update
     * the document.
     *
     * @param device object with all the necessary information to register the device.
     * @param completionHandler callback for APIs asynchronous calls.
     */
    public void registerDevice(final PIDeviceInfo device, final PIAPICompletionHandler completionHandler) {
        handleDevice(device, completionHandler);
    }

    /**
     * Updates a device within an organization.
     *
     * @param device object with all the necessary information to update the device.
     * @param completionHandler callback for APIs asynchronous calls.
     */
    public void updateDevice(final PIDeviceInfo device, final PIAPICompletionHandler completionHandler) {
        handleDevice(device, completionHandler);
    }

    /**
     * Unregisters a device within an organization.
     *
     * @param device object with all the necessary information to unregister the device.
     * @param completionHandler callback for APIs asynchronous calls.
     */
    public void unregisterDevice(final PIDeviceInfo device, final PIAPICompletionHandler completionHandler) {
        device.setRegistered(false);
        handleDevice(device, completionHandler);
    }

    private void handleDevice(final PIDeviceInfo device, final PIAPICompletionHandler completionHandler) {
        final String postDevice = String.format("%s/tenants/%s/orgs/%s/devices", mServerURL, mTenantCode, mOrgCode);
        try {
            URL url = new URL(postDevice);
            POST(url, device.toJSON(), new PIAPICompletionHandler() {
                @Override
                public void onComplete(PIAPIResult postResult) {
                    if (postResult.getResponseCode() == HttpURLConnection.HTTP_CONFLICT) {
                        // call GET
                        try {
                            final URL deviceLocation = new URL(postResult.getHeader().get("Location").get(0));
                            GET(deviceLocation, new PIAPICompletionHandler() {
                                @Override
                                public void onComplete(PIAPIResult getResult) {
                                    if (getResult.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                        // build payload
                                        JSONObject payload = null;
                                        try {
                                            payload = JSONObject.parse((String) getResult.getResult());
                                            device.addToJson(payload);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        // call PUT
                                        PUT(deviceLocation, payload, new PIAPICompletionHandler() {
                                            @Override
                                            public void onComplete(PIAPIResult putResult) {
                                                if (isSuccessfulResponse(putResult.getResponseCode())) {
                                                    putResult.setResult(new PIDevice(putResult.getResultAsJson()));
                                                }
                                                completionHandler.onComplete(putResult);
                                            }
                                        });
                                    } else {
                                        completionHandler.onComplete(getResult);
                                    }
                                }
                            });
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    } else {
                        if (isSuccessfulResponse(postResult.getResponseCode())) {
                            postResult.setResult(new PIDevice(postResult.getResultAsJson()));
                        }
                        completionHandler.onComplete(postResult);
                    }
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a beacon notification message to the beacon connector to report the device's location.
     *
     * @param payload a combination of PIBeaconData and the device descriptor
     * @param completionHandler callback for APIs asynchronous calls.
     */
    public void sendBeaconNotificationMessage(JSONObject payload, PIAPICompletionHandler completionHandler) {
        String bnm = String.format("%s/tenants/%s/orgs/%s", mConnectorURL, mTenantCode, mOrgCode);
        try {
            URL url = new URL(bnm);
            POST(url, payload, completionHandler);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /*
        REST Helpers
     */
    private String generateBasicAuth(String user, String pass) {
        String toEncode = String.format("%s:%s", user, pass);
        return "Basic " + Base64.encodeToString(toEncode.getBytes(), 0, toEncode.length(), Base64.DEFAULT);
    }

    private PIAPIResult cannotReachServer(PIAPIResult result) {
        result.setResponseCode(0);
        result.setResult("Cannot reach the server.");
        return result;
    }

    private void GET(URL url, PIAPICompletionHandler completionHandler) {
        new ApiTask().execute(url, completionHandler, "GET");
    }
    private void GET_IMAGE(URL url, PIAPICompletionHandler completionHandler) {
        ApiTask getImageTask = new ApiTask();
        getImageTask.isImageApiCall = true;
        getImageTask.execute(url, completionHandler, "GET");
    }
    private void POST(URL url, JSONObject payload, PIAPICompletionHandler completionHandler) {
        new ApiTask().execute(url, completionHandler, "POST", payload);
    }
    private void PUT(URL url, JSONObject payload, PIAPICompletionHandler completionHandler) {
        new ApiTask().execute(url, completionHandler, "PUT", payload);
    }

    private boolean isSuccessfulResponse (int responseCode) {
        return responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_BAD_REQUEST;
    }

    private class ApiTask extends AsyncTask<Object, Void, PIAPIResult> {
        protected boolean isImageApiCall = false;
        private URL url;
        private PIAPICompletionHandler completionHandler;
        private JSONObject payload = null;
        private String requestMethod;
        private int responseCode = 0;
        private HttpURLConnection connection = null;
        private PIAPIResult result = new PIAPIResult();

        @Override
        protected PIAPIResult doInBackground(Object... params) {
            url = (URL) params[0];
            completionHandler = (PIAPICompletionHandler) params[1];
            requestMethod = (String) params[2];
            if (params.length > 3) {
                payload = (JSONObject) params[3];
            }

            PILogger.d(TAG, requestMethod + " " + url.toString());
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setReadTimeout(READ_TIMEOUT_IN_MILLISECONDS);
                connection.setConnectTimeout(CONNECTION_TIMEOUT_IN_MILLISECONDS);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", mBasicAuth);
                connection.setRequestMethod(requestMethod);

                if ("GET".equals(requestMethod)) {
                    connection.setDoInput(true);
                } else {
                    connection.setDoOutput(true);
                }
                connection.connect();

                // send payload
                if (payload != null) {
                    OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
                    out.write(payload.toString());
                    out.close();
                }

                responseCode = connection.getResponseCode();
            } catch (IOException e) {
                result.setException(e);
                e.printStackTrace();
            }

            // build result object
            if (responseCode != 0) {
                if (isImageApiCall && isSuccessfulResponse(responseCode)) {
                    try {
                        result.setResult(BitmapFactory.decodeStream(connection.getInputStream()));
                    } catch (IOException e) {
                        e.printStackTrace();
                        result.setException(e);
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader br;
                    String line;
                    try {
                        if (isSuccessfulResponse(responseCode)) {
                            br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        } else {
                            br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                        }
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                            sb.append("\n");
                        }
                        br.close();
                    } catch (IOException e) {
                        result.setException(e);
                        e.printStackTrace();
                    }
                    result.setResult(sb.toString());
                }
                result.setHeader(connection.getHeaderFields());
                result.setResponseCode(responseCode);

                PILogger.d(TAG, result.toString());
                return result;
            } else {
                cannotReachServer(result);
            }

            PILogger.e(TAG, result.toString());
            return result;
        }

        protected void onPostExecute(PIAPIResult result) {
            completionHandler.onComplete(result);
        }
    }
}
