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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.NotificationCompat;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.util.Random;

import static com.facebook.FacebookSdk.getApplicationContext;

/**
 * Builder class for local notifications. Build fully configured local
 * notification specified by JSON object passed from JS side.
 */
public class Builder {

    static final String TAG = "Builder";

    // Application context passed by constructor
    private final Context context;

    // Notification options passed by JS
    private final Options options;

    // Receiver to handle the trigger event
    private Class<?> triggerReceiver;

    // Receiver to handle the clear event
    private Class<?> clearReceiver = ClearReceiver.class;

    // Receiver to handle the prolunga sosta event
    private Class<?> prolungaSostaReceiver = ProlungaSostaReceiver.class;

    // Receiver to handle the prolunga sosta event
    private Class<?> terminaSostaReceiver = TerminaSostaReceiver.class;

    // Activity to handle the click event
    private Class<?> clickActivity = ClickActivity.class;
	
	final static String GROUP_KEY_SOSTE = "group_key_soste";

    /**
     * Constructor
     *
     * @param context
     *      Application context
     * @param options
     *      Notification options
     */
    public Builder(Context context, JSONObject options) {
        this.context = context;
        this.options = new Options(context).parse(options);
    }

    /**
     * Constructor
     *
     * @param options
     *      Notification options
     */
    public Builder(Options options) {
        this.context = options.getContext();
        this.options = options;
    }

    /**
     * Set trigger receiver.
     *
     * @param receiver
     *      Broadcast receiver
     */
    public Builder setTriggerReceiver(Class<?> receiver) {
        this.triggerReceiver = receiver;
        return this;
    }

    /**
     * Set clear receiver.
     *
     * @param receiver
     *      Broadcast receiver
     */
    public Builder setClearReceiver(Class<?> receiver) {
        this.clearReceiver = receiver;
        return this;
    }

//    public Builder setProlungaSostaReceiver(Class<?> receiver) {
//        this.prolungaSostaReceiver = receiver;
//        return this;
//    }

    /**
     * Set click activity.
     *
     * @param activity
     *      Activity
     */
    public Builder setClickActivity(Class<?> activity) {
        this.clickActivity = activity;
        return this;
    }

    /**
     * Creates the notification with all its options passed through JS.
     */
    public Notification build() {
        Uri sound     = options.getSoundUri();
        int smallIcon = options.getSmallIcon();
        int ledColor  = options.getLedColor();
        NotificationCompat.Builder builder;
        NotificationStyleDiscovery notificationStyle = new NotificationStyleDiscovery(context);

        if(options.isOngoing() == false) {
            builder = (android.support.v7.app.NotificationCompat.Builder) new NotificationCompat.Builder(context)
                    .setDefaults(0)
                    .setContentTitle(options.getTitle())
                    .setContentText(options.getText())
                    .setNumber(options.getBadgeNumber())
                    .setTicker(options.getText())
                    .setAutoCancel(options.isAutoClear())
                    .setOngoing(options.isOngoing())
                    .setColor(options.getColor());

            if (smallIcon == 0) {
                builder.setSmallIcon(options.getIcon());
            } else {
                builder.setSmallIcon(options.getSmallIcon());
                builder.setLargeIcon(options.getIconBitmap());
            }
        }

        else{
            builder = (android.support.v7.app.NotificationCompat.Builder) new NotificationCompat.Builder(context)
                    .setDefaults(0)
					.setContentTitle(options.getTitle())
                    .setContentText("- Fine Sosta: " + options.getText())
                    .setNumber(options.getBadgeNumber())
                    .setTicker(options.getText())
                    .setAutoCancel(options.isAutoClear())
                    .setOngoing(options.isOngoing())
					.setGroup(GROUP_KEY_SOSTE)
                    .setColor(options.getColor());

            // Le seguenti istruzioni andranno decommentante e inserite da un'altra parte se si decide di parametrizzare il tipo di notifica

//            NotificationCompat.Action actions[] = options.getActions();
//            for (NotificationCompat.Action action : actions) {
//                if (action != null) builder.addAction(action);
//                else LOG.w(TAG, "Skippo la action che era null");
//            }

            RemoteViews templateCollapsedNotification;
            RemoteViews templateExpandedNotification;

            if (Build.VERSION.SDK_INT <= 23) {
                // Templates for HoneyComb to M
                templateCollapsedNotification = new RemoteViews(context.getPackageName(), getResourceId("layout", "sosta_collapsed_notification_api_less_24"));
                //templateExpandedNotification = new RemoteViews(context.getPackageName(), getResourceId("layout", "sosta_expanded_notification_api_less_24"));

            } else {
                // Templates for N and above
                templateCollapsedNotification = new RemoteViews(context.getPackageName(), getResourceId("layout", "sosta_collapsed_notification"));
                //templateExpandedNotification = new RemoteViews(context.getPackageName(), getResourceId("layout", "sosta_expanded_notification"));
                //builder.setStyle(new NotificationCompat.DecoratedCustomViewStyle());
            }

            if(templateCollapsedNotification instanceof RemoteViews && templateCollapsedNotification != null) builder.setCustomContentView(templateCollapsedNotification);
            // Il template espanso risulta visibile solo da Jelly Bean in poi, quindi la seguente istruzione non ha effetti su versioni precedenti di Android
            //if(templateExpandedNotification instanceof RemoteViews && templateExpandedNotification != null) builder.setCustomBigContentView(templateExpandedNotification);

            templateCollapsedNotification.setTextViewText(getResourceId("id", "titleNotification"), options.getTitle());
            templateCollapsedNotification.setTextViewText(getResourceId("id", "textNotification"), options.getText());
//            templateExpandedNotification.setTextViewText(getResourceId("id", "titleNotification"), options.getTitle());
//            templateExpandedNotification.setTextViewText(getResourceId("id", "textNotification"), options.getText());

            templateCollapsedNotification.setTextColor(getResourceId("id", "titleNotification"), notificationStyle.getTitleColor());
            templateCollapsedNotification.setTextColor(getResourceId("id", "textNotification"), notificationStyle.getTextColor());
//            templateExpandedNotification.setTextColor(getResourceId("id", "titleNotification"), notificationStyle.getTitleColor());
//            templateExpandedNotification.setTextColor(getResourceId("id", "textNotification"), notificationStyle.getTextColor());

            applySostaReceiver(templateCollapsedNotification, prolungaSostaReceiver, getResourceId("id", "buttonProlunga"));
            applySostaReceiver(templateCollapsedNotification, terminaSostaReceiver, getResourceId("id", "buttonTermina"));
//            applySostaReceiver(templateExpandedNotification, prolungaSostaReceiver, getResourceId("id", "buttonProlunga"));
//            applySostaReceiver(templateExpandedNotification, terminaSostaReceiver, getResourceId("id", "buttonTermina"));

            if (smallIcon == 0) {
                builder.setSmallIcon(options.getIcon());
            } else {
                builder.setSmallIcon(options.getSmallIcon());
                //builder.setLargeIcon(options.getIconBitmap());
            }
        }

        if (ledColor != 0) {
            builder.setLights(ledColor, options.getLedOnTime(), options.getLedOffTime());
        }

        if (sound != null) {
            builder.setSound(sound);
        }

        applyDeleteReceiver(builder);
        applyContentReceiver(builder);

        return new Notification(context, options, builder, triggerReceiver);
    }

    /**
     * Set intent to handle the delete event. Will clean up some persisted
     * preferences.
     *
     * @param builder
     *      Local notification builder instance
     */
    private void applyDeleteReceiver(NotificationCompat.Builder builder) {

        if (clearReceiver == null)
            return;

        Intent intent = new Intent(context, clearReceiver)
                .setAction(options.getIdStr())
                .putExtra(Options.EXTRA, options.toString());

        PendingIntent deleteIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setDeleteIntent(deleteIntent);
    }

    private void applySostaReceiver(RemoteViews template, Class<?> receiver, int buttonId) {

//        boolean data = options.getDict().optBoolean("ongoing");
//        if(data != true) return;

        Intent clickIntent = new Intent(context, receiver)
                .putExtra(Options.EXTRA, options.toString());
        PendingIntent pendingClickIntent = PendingIntent.getBroadcast(context, new Random().nextInt(), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        template.setOnClickPendingIntent(buttonId, pendingClickIntent);
    }

    /**
     * Set intent to handle the click event. Will bring the app to
     * foreground.
     *
     * @param builder
     *      Local notification builder instance
     */
    private void applyContentReceiver(NotificationCompat.Builder builder) {

        if (clickActivity == null)
            return;

        Intent intent = new Intent(context, clickActivity)
                .putExtra(Options.EXTRA, options.toString())
                .setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        int reqCode = new Random().nextInt();

        PendingIntent contentIntent = PendingIntent.getActivity(
                context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(contentIntent);
    }

    private int getResourceId(String resType, String resName) {
        return context.getResources().getIdentifier(resName, resType, context.getPackageName());
    }

}
