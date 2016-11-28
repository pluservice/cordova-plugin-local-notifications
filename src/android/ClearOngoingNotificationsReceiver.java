package de.appplant.cordova.plugin.localnotification;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.ReceiverCallNotAllowedException;
import android.util.Log;

import org.apache.cordova.LOG;

import de.appplant.cordova.plugin.notification.*;

/**
 * Created by e.morbidelli on 25/11/2016.
 */

public class ClearOngoingNotificationsReceiver extends BroadcastReceiver {

    public static final String LOG_TAG = "CLEAR_ONGOING";

    @Override
    public void onReceive(Context context, Intent intent) {
//        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);
//        alarmManager.cancel(pi);

        // CAncello la notifcia ongoing
        try {
            String action = intent.getAction();
            int ongoingId = Integer.parseInt(action);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(ongoingId);
        }
        catch (Exception ex) {
            LOG.e(LOG_TAG, "Unable to clear ongoing notification");
        }




    }
}
