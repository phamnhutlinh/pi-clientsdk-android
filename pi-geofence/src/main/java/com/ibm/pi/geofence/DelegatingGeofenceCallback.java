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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
class DelegatingGeofenceCallback implements PIGeofenceCallback {
    private final AtomicReference<PIGeofenceCallback> delegate = new AtomicReference<>(null);
    final PIGeofencingManager service;

    DelegatingGeofenceCallback(final PIGeofencingManager service, final PIGeofenceCallback delegate) {
        this.service = service;
        this.delegate.set(delegate);
    }

    @Override
    public void onGeofencesEnter(List<PIGeofence> geofences) {
        service.postGeofenceEvent(geofences, GeofenceNotificationType.IN);
        PIGeofenceCallback cb = delegate.get();
        if (cb != null) {
            cb.onGeofencesEnter(geofences);
        }
    }

    @Override
    public void onGeofencesExit(List<PIGeofence> geofences) {
        service.postGeofenceEvent(geofences, GeofenceNotificationType.OUT);
        PIGeofenceCallback cb = delegate.get();
        if (cb != null) {
            cb.onGeofencesExit(geofences);
        }
    }

    void onGeofencesMonitored(List<PIGeofence> geofences) {
    }

    void onGeofencesUnmonitored(List<PIGeofence> geofences) {
    }

    @Override
    public void onGeofencesSync(List<PIGeofence> geofencesList, List<String> deletedGeofenceCodes) {
        PIGeofenceCallback cb = delegate.get();
        if (cb != null) {
            cb.onGeofencesSync(geofencesList, deletedGeofenceCodes);
        }
    }

    void setDelegate(final PIGeofenceCallback delegate) {
        this.delegate.set(delegate);
    }
}
