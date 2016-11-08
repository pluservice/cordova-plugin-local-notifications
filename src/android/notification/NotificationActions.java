package de.appplant.cordova.plugin.notification;

/**
 * Created by s.andreani on 03/11/2016.
 */
public final class NotificationActions {
    public static final String PARKING_EXTEND = "PARKING_EXTEND"; // Prolunga sosta
    public static final int PARKING_EXTEND_REQUEST_ID = 0; // Prolunga sosta

    public static final String PARKING_STOP = "PARKING_STOP"; // Termina sosta
    public static final int PARKING_STOP_REQUEST_ID = 1; // Termina sosta

    public static int requestCodeForAction(String actionId) {
        if (PARKING_EXTEND.equalsIgnoreCase(actionId)) {
            return PARKING_EXTEND_REQUEST_ID;
        } else if (PARKING_STOP.equalsIgnoreCase(actionId)) {
            return PARKING_STOP_REQUEST_ID;
        }
        return -1;
    }
    public static boolean isValid(String actionId) {
        return requestCodeForAction(actionId) >= 0;
    }

}
