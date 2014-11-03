/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.push;

import android.content.Intent;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.GCMUtils;
import com.urbanairship.util.ManifestUtils;

import java.io.IOException;
import java.util.Set;

/**
 * Handles GCM registration.
 */
class GCMRegistrar {

    /**
     * Starts the registration process for GCM
     *
     * @return <code>true</code> if the registration process started, otherwise
     * <code>false</code>.
     */
    public static boolean register() throws IOException {
        Logger.verbose("Registering with GCM.");

        // Check if GCM is available
        if (!isGCMAvailable()) {
            return false;
        }

        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(UAirship.getApplicationContext());


        Set<String> senderIds = UAirship.shared().getAirshipConfigOptions().getGCMSenderIds();
        Set<String> registeredGcmSenderIds = PushManager.shared().getPreferences().getRegisteredGcmSenderIds();

        // Unregister if we have different registered sender ids
        if (registeredGcmSenderIds != null &&  !registeredGcmSenderIds.equals(senderIds)) {
            Logger.debug("Unregistering GCM Sender IDs:  " + registeredGcmSenderIds);
            gcm.unregister();
        }


        Logger.debug("Registering GCM Sender IDs:  " + senderIds);
        String registrationId = gcm.register(senderIds.toArray(new String[senderIds.size()]));

        if (registrationId != null) {
            Logger.info("GCM registration successful. Registration ID: " + registrationId);
            UAirship.shared().getPushManager().setGcmId(registrationId);
            UAirship.shared().getPushManager().getPreferences().setRegisteredGcmSenderIds(senderIds);
        }

        Intent intent = new Intent(PushService.ACTION_PUSH_REGISTRATION_FINISHED);
        PushService.startServiceWithWakeLock(UAirship.getApplicationContext(), intent);

        return true;
    }

    /**
     * Checks to see if GCM is available for the application
     *
     * @return <code>true</code> If GCM is available for the application,
     * otherwise <code>false</code>
     */
    private static boolean isGCMAvailable() {
        try {
            int playServicesStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(UAirship.getApplicationContext());
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                Logger.error("Google Play services is currently unavailable.");
                return false;
            }
        } catch (IllegalStateException e) {
            // Missing version tag
            Logger.error("Unable to register. " + e.getMessage());
            return false;
        }

        // Check for permissions - the emulator will not have them, nor with devices without the Google stack
        // such as the Kindle Fire.
        if (!ManifestUtils.isPermissionKnown(GCMUtils.PERMISSION_RECEIVE)) {
            Logger.error(GCMUtils.PERMISSION_RECEIVE + " is unknown to PackageManager. " +
                    "Note that an AVD emulator may not support GCM.");

            Logger.error("If you're running in an emulator, you need to install " +
                    "the appropriate image through the Android SDK and AVM manager. " +
                    "See http://developer.android.com/guide/google/gcm/ for further details.");

            return false;
        }

        // The sender ID is crucial, if we don't have this, GCM is not available
        if (UAirship.shared().getAirshipConfigOptions().gcmSender == null) {
            Logger.error("The GCM sender ID is not set. Unable to register.");
            return false;
        }

        return true;
    }
}
