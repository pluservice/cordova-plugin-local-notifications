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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import de.appplant.cordova.plugin.LyfecycleHandler;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The receiver activity is triggered when a notification is clicked by a user.
 * The activity calls the background callback and brings the launch intent
 * up to foreground.
 */
public class ClickActivity extends AbstractClickActivity implements Callback {

    private static final String ISO8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public final static String TAG = "ClickActivity";
    protected Map<String, Notification> notifications = new ArrayMap<String, Notification>();

    /**
     * Called when local notification was clicked by the user. Will
     * move the app to foreground.
     *
     * @param notification Wrapper around the local notification
     */
    @Override
    public void onClick(Notification notification) {
        launchApp();

        if (notification.isRepeating()) {
            notification.clear();
//        } else { GESTISCO IL CANCEL LATO JS!
//            notification.cancel();
        }
    }

    @Override
    public void onButtonClick(Notification notification, String action) {
        LOG.setLogLevel(LOG.VERBOSE);
        if (action == null) throw new NullPointerException("Action cannot be null");

        JSONObject data = null;
        try {
            data = new JSONObject(notification.getOptions().getDict().optString("data", "{}"));
        } catch (JSONException e) {
            LOG.e(TAG, "JSONObject parse notification data", e);
        }

        ParkingService.Env env = ParkingService.Env.Prod;
        if ("test".equalsIgnoreCase(data.optString(NotifictionDataKeys.AMBIENTE)))
            env = ParkingService.Env.Test;

        ParkingService service = new ParkingService(this, env);

        if (NotificationActions.PARKING_EXTEND.equalsIgnoreCase(action) && data != null) {
            try {

                final DateFormat dateFormat = new SimpleDateFormat(ISO8601_DATE_FORMAT); // ISO Date format
                String fineSostaOrigString = data.getString(NotifictionDataKeys.END_TIME);

                Date fineSostaOrig = parseDateString(dateFormat, fineSostaOrigString);
                int minutesToAdd = data.getInt(NotifictionDataKeys.EXTEND_MINUTES);
                Date nuovoFineSosta = new Date(fineSostaOrig.getTime() + (minutesToAdd * 60 * 1000));

                final String rifVendita = data.getString(NotifictionDataKeys.RIF_VENDITA);
                notifications.put(rifVendita, notification);

                service.extendParkingTime(
                        data.getString(NotifictionDataKeys.TESSERA),
                        data.getString(NotifictionDataKeys.GUID_ACCOUNT),
                        dateFormat.format(nuovoFineSosta),
                        data.getString(NotifictionDataKeys.VETTORE),
                        rifVendita,
                        this);

            } catch (JSONException ex) {
                LOG.e(TAG, "Unable to parse json", ex);
            } catch (ParseException ex) {
                LOG.e(TAG, "Unable to parse fine sosta date format", ex);
            } catch (Exception e) {
                LOG.e(TAG, "Exception:", e);
            }
        } else if (NotificationActions.PARKING_STOP.equalsIgnoreCase(action) && data != null) {
            try {

                final String rifVendita = data.getString(NotifictionDataKeys.RIF_VENDITA);
                notifications.put(rifVendita, notification);

                service.stopParking(
                        data.getString(NotifictionDataKeys.TESSERA),
                        data.getString(NotifictionDataKeys.GUID_ACCOUNT),
                        data.getString(NotifictionDataKeys.VETTORE),
                        data.getString(NotifictionDataKeys.RIF_VENDITA),
                        this);
            } catch (Exception e) {
                LOG.e(TAG, "Exception:", e);
            }
        }
    }

    protected Date parseDateString(DateFormat dateFormat, String jsonDate) throws ParseException {
        if (jsonDate.endsWith("Z"))
            jsonDate = jsonDate.substring(0, jsonDate.length() - 1) + "+0000";

        return dateFormat.parse(jsonDate);
    }

    /**
     * Build notification specified by options.
     *
     * @param builder Notification builder
     */
    public Notification buildNotification(Builder builder) {
        return builder.build();
    }

    private static Date parseMVCJSONDate(String dateString) {
        int start = dateString.indexOf("(") + 1;
        int end = dateString.indexOf("+");
        long millis = Long.parseLong(dateString.substring(start, end));
        return new Date(millis);
    }

    @Override
    public void onFailure(Call call, IOException e) {
        LOG.e(TAG, "onFailure", e);
        this.notifyGenericError();
    }

    @Override
    public void onResponse(final Call call, Response response) throws IOException {
        LOG.d(TAG, "onResponse" + " " + response.toString());

        final Request request = call.request();

        String requestParamUrl = request.url().queryParameter("url");
        String method = requestParamUrl.substring(requestParamUrl.lastIndexOf("/") + 1);

        try {
            if (response.isSuccessful()) {
                JSONObject requestBody = new JSONObject(Utils.bodyToString(request.body()));
                String rifVendita = requestBody.getString("RifVendita");

                ResponseBody body = response.body();
                Log.d(TAG, "Response content type: " + body.contentType().toString());

                JSONObject responseBody = new JSONObject(response.body().string());
                if (responseBody.optBoolean("Esito", false)) {
                    JSONObject responseData = responseBody.getJSONObject("Data");

                    if (ParkingService.PROLUNGA_SOSTA.equalsIgnoreCase(method)) {
                        onParkingExtendResponse(responseData, rifVendita);
                    } else if (ParkingService.TERMINA_SOSTA.equalsIgnoreCase(method)) {
                        onParkingStopResponse(responseData, rifVendita);
                    } else this.notifyGenericError();
                } else this.notifyGenericError();
            } else this.notifyGenericError();
        } catch (JSONException ex) {
            this.notifyGenericError();
        }
    }

    protected final void notifyGenericError() {
        int resourceIdentifier = getStringIdentifier("SOSTA_GENERIC_ERROR");
        makeToast(getString(resourceIdentifier));
    }

    private int getStringIdentifier(String resourceName) {
        return getResources().getIdentifier(resourceName, "string", getApplicationContext().getPackageName());
    }

    protected void makeToast(final String text) {
        final Activity activity = this;

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    protected void onParkingExtendResponse(final JSONObject responseData, final String rifVendita) throws JSONException {
        final Activity activity = this;

        JSONArray sosteAttive = responseData.getJSONArray("SosteAttive");
        for (int i = 0; i < sosteAttive.length(); i++) {
            JSONObject sosta = sosteAttive.getJSONObject(i);
            if (rifVendita.equalsIgnoreCase(sosta.getString("RifVendita"))) {
                String nuovaFine = sosta.getString("DataFine");
                Date nuovaFineDate = parseMVCJSONDate(nuovaFine);
                DateFormat dateFormat = new SimpleDateFormat(getString(getStringIdentifier("SOSTA_DATE_FORMAT")));
                final String formatted = dateFormat.format(nuovaFineDate);
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                makeToast(String.format(getString(getStringIdentifier("SOSTA_EXTENDED")), formatted));
                LOG.d(TAG, formatted);

                if (LyfecycleHandler.isApplicationInForeground()) tellJavascriptToResume();

                Notification notification = notifications.get(rifVendita);
                if (notification == null) notifyGenericError();
                else {
                    Options options = notification.getOptions();
                    JSONObject data = new JSONObject(options.getDict().optString("data", "{}"));

                    DateFormat dateFormat1 = new SimpleDateFormat(ISO8601_DATE_FORMAT);
                    data.put(NotifictionDataKeys.END_TIME, dateFormat1.format(nuovaFineDate));

                    options.setText(formatted);
                    options.setExtra(data);
//                    options.setSound(null); // rimuovo il suono

                    Notification newNotification = new Notification(this, options, notification.builder, null);
                    newNotification.schedule();
                }
            }
        }
    }

    protected void onParkingStopResponse(final JSONObject responseData, final String rifVendita) throws JSONException {

        // make notification go away
        Notification ongoingNotification = notifications.get(rifVendita);

        if (ongoingNotification == null) notifyGenericError();
        else {
            ongoingNotification.cancel();

            // cancello anche la notifica di fine sosta
            try {
                // ottengo l'alarmManager e il notificationManager
                Context ctx = getApplicationContext();
                AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

                // ricreo il pi che abbia come intent uno con action == rifVendita
                Intent intent1 = new Intent(ctx, de.appplant.cordova.plugin.localnotification.TriggerReceiver.class).setAction(rifVendita);
                PendingIntent pi1 = PendingIntent.getBroadcast(ctx, 0, intent1, 0);

                Intent intent2 = new Intent(ctx, de.appplant.cordova.plugin.notification.TriggerReceiver.class).setAction(rifVendita);
                PendingIntent pi2 = PendingIntent.getBroadcast(ctx, 0, intent2, 0);

                // infine cancello la notifica
                alarmManager.cancel(pi1);
                alarmManager.cancel(pi2);
                notificationManager.cancel(Integer.parseInt(rifVendita));
            } catch (Exception e) {
                LOG.e(TAG, "Unable to cancel other notification");
            }

            makeToast(getString(getStringIdentifier("SOSTA_STOPPED")));
            if (LyfecycleHandler.isApplicationInForeground()) tellJavascriptToResume();
        }
    }

    protected void tellJavascriptToResume() {
        try {
            String appSchema = getString(getStringIdentifier("appschema"));
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(appSchema + "://go?action=sosta_refresh"));
            startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to send reload message to app", ex);
        }
    }

    private static final class NotifictionDataKeys {
        public static final String EXTEND_MINUTES = "extendMinutes";
        public static final String END_TIME = "endTime";
        public static final String GUID_ACCOUNT = "guidAccount";
        public static final String RIF_VENDITA = "rifVendita";
        public static final String TESSERA = "tessera";
        public static final String VETTORE = "vettore";
        public static final String AMBIENTE = "ambiente";
    }
}
