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

import org.altbeacon.beacon.Beacon;

import java.util.ArrayList;

/**
 * This interface provides the users of the SDK callbacks regarding the beacon sensor.
 *
 * @author Ciaran Hannigan (cehannig@us.ibm.com)
 */
public interface PIBeaconSensorDelegate {

    /**
     * Provides a collection of beacons within range
     *
     * @param beacons collection of Class Beacon.
     */
    void beaconsInRange(ArrayList<Beacon> beacons);
}