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

package com.ibm.pisdk;

import android.content.Context;

/**
 * Factory class for creating instances of {@link PIDeviceID}.
 */
public class PIDeviceIDFactory {
    /**
     * Instantiation is not permitted.
     */
    private PIDeviceIDFactory() {
    }

    /**
     * Create a new {@link PIDeviceID}.
     * @param context teh Android context used to retrieve device information.
     * @return a newly created {@link PIDeviceID}.
     */
    public static PIDeviceID newInstance(Context context) {
        return new PIDeviceID(context);
    }
}
