/**
 * Copyright (c) 2015-2016 IBM Corporation. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.pi.geofence;

import java.io.Serializable;
import java.util.List;

/**
 * This callback interface provides methods to notify when entering or exxiting a geofence.
 */
public interface PIGeofenceCallback extends Serializable {
    /**
     * Called when entering one or more geofences.
     * @param geofences the list of triggering geofences.
     */
    void onGeofencesEnter(List<PIGeofence> geofences);

    /**
     * Called when exiting one or more geofences.
     * @param geofences the list of triggering geofences.
     */
    void onGeofencesExit(List<PIGeofence> geofences);

    /**
     * Called when a new set of geofences are registered for monitoring.
     * @param geofences the list of monitored geofences.
     */
    void onGeofencesMonitored(List<PIGeofence> geofences);

    /**
     * Called when a set of geofences are unregistered from monitoring.
     * @param geofences the list of unmonitored geofences.
     */
    void onGeofencesUnmonitored(List<PIGeofence> geofences);
}