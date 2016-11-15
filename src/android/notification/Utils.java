package de.appplant.cordova.plugin.notification;

import java.io.IOException;

import okhttp3.RequestBody;
import okio.Buffer;

public class Utils {

    public static String bodyToString(final RequestBody request) {
        try {
            final RequestBody copy = request;
            final Buffer buffer = new Buffer();
            copy.writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "did not work";
        }
    }

}
