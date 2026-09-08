/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.networking;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class LibreLoginMessenger {

    public static final String CHANNEL = "librelogin:auth";
    public static final String CHANNEL_ID = "librelogin";

    public static byte[] serializeAuthMessage(UUID uuid) {
        return uuid.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static UUID deserializeAuthMessage(byte[] data) {
        return UUID.fromString(new String(data, StandardCharsets.UTF_8));
    }

    public static boolean isAuthChannel(String channel) {
        return CHANNEL.equals(channel);
    }

    public static boolean isAuthMessage(byte[] data) {
        if (data == null || data.length == 0) return false;
        try {
            UUID.fromString(new String(data, StandardCharsets.UTF_8));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String getChannelName() {
        return CHANNEL;
    }
}
