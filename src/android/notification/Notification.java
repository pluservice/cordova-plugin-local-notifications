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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import org.apache.cordova.LOG;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.appplant.cordova.plugin.localnotification.ClearOngoingNotificationsReceiver;

/**
 * Wrapper class around OS notification class. Handles basic operations
 * like show, delete, cancel for a single local notification instance.
 */
public class Notification {

    // Used to differ notifications by their life cycle state
    public enum Type {
        ALL, SCHEDULED, TRIGGERED
    }

    // Default receiver to handle the trigger event
    private static Class<?> defaultReceiver = TriggerReceiver.class;

    // Key for private preferences
    static final String PREF_KEY = "LocalNotification";

    // Application context passed by constructor
    private final Context context;

    // Notification options passed by JS
    private final Options options;

    // Builder with full configuration
    public final NotificationCompat.Builder builder;

    // Receiver to handle the trigger event
    private Class<?> receiver = defaultReceiver;

    /**
     * Constructor
     *
     * @param context
     *      Application context
     * @param options
     *      Parsed notification options
     * @param builder
     *      Pre-configured notification builder
     */
    protected Notification (Context context, Options options,
                    NotificationCompat.Builder builder, Class<?> receiver) {

        this.context = context;
        this.options = options;
        this.builder = builder;

        this.receiver = receiver != null ? receiver : defaultReceiver;
    }

    /**
     * Get application context.
     */
    public Context getContext () {
        return context;
    }

    /**
     * Get notification options.
     */
    public Options getOptions () {
        return options;
    }

    /**
     * Get notification ID.
     */
    public int getId () {
        return options.getId();
    }

    /**
     * If it's a repeating notification.
     */
    public boolean isRepeating () {
        return getOptions().getRepeatInterval() > 0;
    }

    /**
     * If the notification was in the past.
     */
    public boolean wasInThePast () {
        return new Date().after(options.getTriggerDate());
    }

    /**
     * If the notification is scheduled.
     */
    public boolean isScheduled () {
        return isRepeating() || !wasInThePast();
    }

    /**
     * If the notification is triggered.
     */
    public boolean isTriggered () {
        return wasInThePast();
    }

    /**
     * If the notification is an update.
     *
     * @param keepFlag
     *      Set to false to remove the flag from the option map
     */
    protected boolean isUpdate (boolean keepFlag) {
        boolean updated = options.getDict().optBoolean("updated", false);

        if (!keepFlag) {
            options.getDict().remove("updated");
        }

        return updated;
    }

    /**
     * Notification type can be one of pending or scheduled.
     */
    public Type getType () {
        return isScheduled() ? Type.SCHEDULED : Type.TRIGGERED;
    }

    /**
     * Schedule the local notification.
     */
    public void schedule() {
        long triggerTime = options.getTriggerTime();

        persist();

        // Intent gets called when the Notification gets fired
        Intent intent = new Intent(context, receiver)
                .setAction(options.getIdStr())
                .putExtra(Options.EXTRA, options.toString());

        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        if (isRepeating()) {
          if (wasInThePast()) {
            triggerTime = System.currentTimeMillis();
          }

            getAlarmMgr().setRepeating(AlarmManager.RTC_WAKEUP,
                    triggerTime, options.getRepeatInterval(), pi);
        } else {
            getAlarmMgr().set(AlarmManager.RTC_WAKEUP, triggerTime, pi);
        }

        /**
         * Se ho creato una notifica onGoing devo creare un altro allarme che verrà triggerato al momento della
         * scadenza della sosta
         */

        if (options.isOngoing()) {
            try {

                // Questo intent viene lanciato subito per eliminare eventuali allarmi presenti per questa notifica
                Intent intent1 = new Intent(context, ClearOngoingNotificationsReceiver.class).setAction(options.getIdStr());
                PendingIntent pi1 = PendingIntent.getBroadcast(context, 0, intent1, 0);
                getAlarmMgr().cancel(pi1);

                // Adesso ricreo un nuovo intent che dovrà scattare quando la sosta termina
                Intent intent2 = new Intent(context, ClearOngoingNotificationsReceiver.class).setAction(options.getIdStr());

                PendingIntent pi2 = PendingIntent.getBroadcast(context, 0, intent2, 0);
;
                JSONObject data = null;
                try {
                    data = new JSONObject(options.getDict().optString("data", "{}"));
                } catch (JSONException e) {
                    LOG.e(ClearOngoingNotificationsReceiver.LOG_TAG,"JSONObject parse notification data", e);
                }
                final DateFormat dateFormat = new SimpleDateFormat(ISO8601_DATE_FORMAT); // ISO Date format
                long millis = parseDateString(dateFormat, data.getString("endTime")).getTime();

                getAlarmMgr().set(AlarmManager.RTC_WAKEUP, millis, pi2);

                // MOMO 2737 Debug #6
                // Prima rimuovo eventuali notifiche con action  = rifVendita che sono quelle non ongoing associate a questa
                String rifVendita = data.getString("rifVendita");
                intent = new Intent(context, receiver).setAction(rifVendita);
                pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                getAlarmMgr().cancel(pi);
                triggerTime = millis - (15 * 60 * 1000);

                // Qui avrò che:
                // millis = ora fine sosta
                // triggerTime = 15 min prima della fine sosta
                // A questo punto la devo rischedulare
                JSONObject newOptsData = new JSONObject();
                newOptsData.put("id", Integer.parseInt(rifVendita));
                newOptsData.put("title", data.getString("messaggioScadenzaImminente"));
                newOptsData.put("text", data.getString("parcheggio"));
                newOptsData.put("at", triggerTime);
                newOptsData.put("icon", options.getPureIcon());
                newOptsData.put("smallIcon",options.getPureSmallIcon());
                Options newOpts = new Options(context).parse(newOptsData);

                // Questo è l'allarme che triggera la notifica a 15min dalla fine della sosta
                intent = new Intent(context, receiver).setAction(rifVendita).putExtra(Options.EXTRA, newOpts.toString());
                pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                getAlarmMgr().set(AlarmManager.RTC_WAKEUP, triggerTime, pi);

                // Questo è l'allarme che elimina
                intent = new Intent(context, ClearReceiver.class).setAction(rifVendita).putExtra(Options.EXTRA, newOpts.toString());
                pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                getAlarmMgr().set(AlarmManager.RTC_WAKEUP, millis, pi);


            } catch (Exception e) {
                LOG.e(ClearOngoingNotificationsReceiver.LOG_TAG, "Unable to schedule ongoing notification clear intent");
            }
        }
    }
    private static final String ISO8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    protected Date parseDateString(DateFormat dateFormat, String jsonDate) throws ParseException {
        if (jsonDate.endsWith("Z"))
            jsonDate = jsonDate.substring(0, jsonDate.length() - 1) + "+0000";

        return dateFormat.parse(jsonDate);
    }

    /**
     * Clear the local notification without canceling repeating alarms.
     */
    public void clear () {

        if (!isRepeating() && wasInThePast())
            unpersist();

        if (!isRepeating())
            getNotMgr().cancel(getId());
    }

    /**
     * Cancel the local notification.
     *
     * Create an intent that looks similar, to the one that was registered
     * using schedule. Making sure the notification id in the action is the
     * same. Now we can search for such an intent using the 'getService'
     * method and cancel it.
     */
    public void cancel() {
        Intent intent = new Intent(context, receiver)
                .setAction(options.getIdStr());

        PendingIntent pi = PendingIntent.
                getBroadcast(context, 0, intent, 0);

        getAlarmMgr().cancel(pi);
        getNotMgr().cancel(options.getId());

        unpersist();
    }

    /**
     * Present the local notification to user.
     */
    public void show () {
        // TODO Show dialog when in foreground
        showNotification();
    }

    /**
     * Show as local notification when in background.
     */
    @SuppressWarnings("deprecation")
    private void showNotification () {
        int id = getOptions().getId();

        if (Build.VERSION.SDK_INT <= 15) {
            // Notification for HoneyComb to ICS
            getNotMgr().notify(id, builder.getNotification());
        } else {
            // Notification for Jellybean and above
            getNotMgr().notify(id, builder.build());
        }
    }

    /**
     * Count of triggers since schedule.
     */
    public int getTriggerCountSinceSchedule() {
        long now = System.currentTimeMillis();
        long triggerTime = options.getTriggerTime();

        if (!wasInThePast())
            return 0;

        if (!isRepeating())
            return 1;

        return (int) ((now - triggerTime) / options.getRepeatInterval());
    }

    /**
     * Encode options to JSON.
     */
    public String toString() {
        JSONObject dict = options.getDict();
        JSONObject json = new JSONObject();

        try {
            json = new JSONObject(dict.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        json.remove("firstAt");
        json.remove("updated");
        json.remove("soundUri");
        json.remove("iconUri");

        return json.toString();
    }

    /**
     * Persist the information of this notification to the Android Shared
     * Preferences. This will allow the application to restore the notification
     * upon device reboot, app restart, retrieve notifications, aso.
     */
    private void persist () {
        SharedPreferences.Editor editor = getPrefs().edit();

        editor.putString(options.getIdStr(), options.toString());

        if (Build.VERSION.SDK_INT < 9) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    /**
     * Remove the notification from the Android shared Preferences.
     */
    private void unpersist () {
        SharedPreferences.Editor editor = getPrefs().edit();

        editor.remove(options.getIdStr());

        if (Build.VERSION.SDK_INT < 9) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    /**
     * Shared private preferences for the application.
     */
    private SharedPreferences getPrefs () {
        return context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
    }

    /**
     * Notification manager for the application.
     */
    private NotificationManager getNotMgr () {
        return (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Alarm manager for the application.
     */
    private AlarmManager getAlarmMgr () {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * Set default receiver to handle the trigger event.
     *
     * @param receiver
     *      broadcast receiver
     */
    public static void setDefaultTriggerReceiver (Class<?> receiver) {
        defaultReceiver = receiver;
    }

    public static final class NotificationDataKeys {
        public static final String EXTEND_MINUTES = "extendMinutes";
        public static final String END_TIME = "endTime";
        public static final String GUID_ACCOUNT = "guidAccount";
        public static final String RIF_VENDITA = "rifVendita";
        public static final String TESSERA = "tessera";
        public static final String VETTORE = "vettore";
        public static final String AMBIENTE = "ambiente";
    }

}
