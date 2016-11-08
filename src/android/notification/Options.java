/*
 * Copyright (c) 2013-2015 by appPlant UG. All rights reserved.
 *
 * @APPPLANT_LICENSE_HEADER_START@
 *
 * This file contains Original Code and/or Modifications of Original Code
 * as defined in and that are subject to the Apache License
 * Version 2.0 (the 'License'). You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at
 * http://opensource.org/licenses/Apache-2.0/ and read it before using this
 * file.
 *
 * The Original Code and all software distributed under the License are
 * distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, AND APPLE HEREBY DISCLAIMS ALL SUCH WARRANTIES,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.
 * Please see the License for the specific language governing rights and
 * limitations under the License.
 *
 * @APPPLANT_LICENSE_HEADER_END@
 */

package de.appplant.cordova.plugin.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Random;

/**
 * Wrapper around the JSON object passed through JS which contains all
 * possible option values. Class provides simple readers and more advanced
 * methods to convert independent values into platform specific values.
 */
public class Options {

    // Key name for bundled extras
    static final String EXTRA = "NOTIFICATION_OPTIONS";

    // Key name for button action extra
    static final String ACTION = "NOTIFICATION_ACTION";

    // Log tag
    static final String TAG = "NotificationOptions";

    // The original JSON object
    private JSONObject options = new JSONObject();

    // Repeat interval
    private long interval = 0;

    // Application context
    private final Context context;

    // Asset util instance
    private final AssetUtil assets;


    /**
     * Constructor
     *
     * @param context Application context
     */
    public Options(Context context) {
    	this.context = context;
        this.assets  = AssetUtil.getInstance(context);
    }

    /**
     * Parse given JSON properties.
     *
     * @param options JSON properties
     */
    public Options parse(JSONObject options) {
        this.options = options;

        parseInterval();
        parseAssets();

        return this;
    }

    /**
     * Parse repeat interval.
     */
    private void parseInterval() {
        String every = options.optString("every").toLowerCase();

        if (every.isEmpty()) {
            interval = 0;
        } else if (every.equals("second")) {
            interval = 1000;
        } else if (every.equals("minute")) {
            interval = AlarmManager.INTERVAL_FIFTEEN_MINUTES / 15;
        } else if (every.equals("hour")) {
            interval = AlarmManager.INTERVAL_HOUR;
        } else if (every.equals("day")) {
            interval = AlarmManager.INTERVAL_DAY;
        } else if (every.equals("week")) {
            interval = AlarmManager.INTERVAL_DAY * 7;
        } else if (every.equals("month")) {
            interval = AlarmManager.INTERVAL_DAY * 31;
        } else if (every.equals("quarter")) {
            interval = AlarmManager.INTERVAL_HOUR * 2190;
        } else if (every.equals("year")) {
            interval = AlarmManager.INTERVAL_DAY * 365;
        } else {
            try {
                interval = Integer.parseInt(every) * 60000;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Parse asset URIs.
     */
    private void parseAssets() {

        if (options.has("iconUri") && !options.optBoolean("updated"))
            return;

        Uri iconUri  = assets.parse(options.optString("icon", "res://icon"));
        Uri soundUri = assets.parseSound(options.optString("sound", null));

        try {
            options.put("iconUri", iconUri.toString());
            options.put("soundUri", soundUri.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Application context.
     */
    public Context getContext() {
        return context;
    }

    /**
     * Wrapped JSON object.
     */
    JSONObject getDict() {
        return options;
    }

    public void setExtra(JSONObject extra) throws JSONException {
        options.put("data", extra);
    }

    public void setSound(String sound) throws JSONException {
        options.put("soundUri", sound);
    }

    /**
     * Text for the local notification.
     */
    public String getText() {
        return options.optString("text", "");
    }

    public void setText(String txt) throws JSONException {
        options.put("text", txt);
    }

    /**
     * Repeat interval (day, week, month, year, aso.)
     */
    public long getRepeatInterval() {
        return interval;
    }

    /**
     * Badge number for the local notification.
     */
    public int getBadgeNumber() {
        return options.optInt("badge", 0);
    }

    /**
     * ongoing flag for local notifications.
     */
    public Boolean isOngoing() {
        return options.optBoolean("ongoing", false);
    }

    /**
     * autoClear flag for local notifications.
     */
    public Boolean isAutoClear() {
        return options.optBoolean("autoClear", false);
    }

    /**
     * ID for the local notification as a number.
     */
    public Integer getId() {
        return options.optInt("id", 0);
    }

    /**
     * ID for the local notification as a string.
     */
    public String getIdStr() {
        return getId().toString();
    }

    /**
     * Trigger date.
     */
    public Date getTriggerDate() {
        return new Date(getTriggerTime());
    }

    /**
     * Trigger date in milliseconds.
     */
    public long getTriggerTime() {
//        return Math.max(
//                System.currentTimeMillis(),
//                options.optLong("at", 0) * 1000
//        );
        return options.optLong("at", 0) * 1000;
    }

    /**
     * Title for the local notification.
     */
    public String getTitle() {
        String title = options.optString("title", "");

        if (title.isEmpty()) {
            title = context.getApplicationInfo().loadLabel(
                    context.getPackageManager()).toString();
        }

        return title;
    }

    /**
     * @return The notification color for LED
     */
    public int getLedColor() {
        String hex = options.optString("led", null);

        if (hex == null) {
            return 0;
        }

        int aRGB = Integer.parseInt(hex, 16);

        return aRGB + 0xFF000000;
    }

    /**
     * @return The time that the LED should be on (in milliseconds).
     */
    public int getLedOnTime() {
        String timeOn = options.optString("ledOnTime", null);

        if (timeOn == null) {
            return 1000;
        }

        try {
            return Integer.parseInt(timeOn);
        } catch (NumberFormatException e) {
           return 1000;
        }
    }

    /**
     * @return The time that the LED should be off (in milliseconds).
     */
    public int getLedOffTime() {
        String timeOff = options.optString("ledOffTime", null);

        if (timeOff == null) {
            return 1000;
        }

        try {
            return Integer.parseInt(timeOff);
        } catch (NumberFormatException e) {
           return 1000;
        }
    }

    /**
     * @return The notification background color for the small icon
     *      Returns null, if no color is given.
     */
    public int getColor() {
        String hex = options.optString("color", null);

        if (hex == null) {
            return NotificationCompat.COLOR_DEFAULT;
        }

        int aRGB = Integer.parseInt(hex, 16);

        return aRGB + 0xFF000000;
    }

    /**
     * Sound file path for the local notification.
     */
    public Uri getSoundUri() {
        Uri uri = null;

        try {
            uri = Uri.parse(options.optString("soundUri"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uri;
    }

    /**
     * Icon bitmap for the local notification.
     */
    public Bitmap getIconBitmap() {
        Bitmap bmp;

        try {
            Uri uri = Uri.parse(options.optString("iconUri"));
            bmp = assets.getIconFromUri(uri);
        } catch (Exception e) {
            e.printStackTrace();
            bmp = assets.getIconFromDrawable("icon");
        }

        return bmp;
    }

    /**
     * Icon resource ID for the local notification.
     */
    public int getIcon() {
        String icon = options.optString("icon", "");

        int resId = assets.getResIdForDrawable(icon);

        if (resId == 0) {
            resId = getSmallIcon();
        }

        if (resId == 0) {
            resId = android.R.drawable.ic_popup_reminder;
        }

        return resId;
    }

    /**
     * Small icon resource ID for the local notification.
     */
    public int getSmallIcon() {
        String icon = options.optString("smallIcon", "");

        return assets.getResIdForDrawable(icon);
    }

    /**
     * Action buttons for notification
     */
    public NotificationCompat.Action[] getActions() {

        // recupero l'array delle actions che mi arrivano dal javascript
        JSONArray actions = options.optJSONArray("actions");

        // se non ne ho specificate, oppure trovo un array vuoto, esco subito
        if (actions == null || actions.length() < 1) return new NotificationCompat.Action[0];

        // inizializzo l'array che tornerò
        NotificationCompat.Action[] notificationActions = new NotificationCompat.Action[actions.length()];

        // ciclo le actions e creo le corrispondenti `NotificationCompat.Action` da mettere nell'array
        for (int i = 0; i < actions.length(); i++) {
            try {
                // recupero l'iesimo `JSONObject`
                JSONObject action = actions.optJSONObject(i);

                // se è nullo vado avanti con l'iterazione successiva
                if (action == null) {
                    LOG.w(TAG, "Null action found. Skipping!");
                    continue;
                }

                String actionLabel = action.optString("label", null);
//                String actionDeepLink = action.optString("uri", null);
                String actionId = action.optString("id", null);

                int actionRequestCode = NotificationActions.requestCodeForAction(actionId);

                if (actionLabel == null || actionRequestCode < 0) {
                    LOG.w(TAG, "Invalid action found. Skipping!");
                    continue;
                }

                Class clickActivityClass = ClickActivity.class;

                if (clickActivityClass == null) {
                    LOG.e(TAG, "Click activity not found. Very Bad... Breaking!");
                    break;
                }

                Intent actionIntent = new Intent(context, clickActivityClass)
                        .putExtra(Options.EXTRA, options.toString())
                        .putExtra(Options.ACTION, actionId)
                        .setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context, new Random().nextInt(), actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                notificationActions[i] = new NotificationCompat.Action.Builder(0, actionLabel, pendingIntent).build();
            } catch (Exception e) {
                LOG.e(TAG, "Eccezione in getActions()", e);
            }
        }
        return notificationActions;
    }

    /**
     * JSON object as string.
     */
    public String toString() {
        return options.toString();
    }

    /**
     * Getter for MainActivity Class
     *
     * @return {java.lang.Class}
     */
    protected final Class getMainActivityClass() {
        Class mainActivityClass = null;

        try {
            mainActivityClass = Class.forName(context.getPackageName() + ".MainActivity");
        } catch (ClassNotFoundException e) {
            LOG.e(TAG, "MainActivity not found", e);
        }

        return mainActivityClass;
    }

}
