package de.appplant.cordova.plugin.notification;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import android.view.ContextThemeWrapper;

import net.pluservice.myCiceroTest.R;

import org.apache.cordova.LOG;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;

import de.appplant.cordova.plugin.LyfecycleHandler;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TerminaSostaActivity extends Activity implements Callback{

    public final static String TAG = "TerminaSostaActivity";
    protected Map<String, Notification> notifications = new ArrayMap<String, Notification>();
    private Context context;
    private Bundle bundle;

    @Override
    public void onCreate (Bundle state) {
        super.onCreate(state);

        Intent intent = getIntent();
        this.bundle = intent.getExtras();
        this.context = getApplicationContext();

        Intent closeIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(closeIntent);

        String title = getResources().getString(R.string.app_name);
        String message = "Vuoi terminare la sosta?";

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.alertDialog));

        alertDialogBuilder.setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Termina",new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        dialog.dismiss();
                        getNotificationAndTerminaSosta(context, bundle);
                        finish();
                    }
                })
                .setNegativeButton("Annulla", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                        finish();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();

        Button bp = alert.getButton(AlertDialog.BUTTON_POSITIVE);
        Button bn = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
        if(bp != null) bp.setTextColor(Color.RED);
        if(bn != null) bn.setTextColor(Color.GRAY);

    }

    private void getNotificationAndTerminaSosta (Context context, Bundle bundle){
        try {
            String data = bundle.getString(Options.EXTRA);
            JSONObject options = new JSONObject(data);

            Builder builder = new Builder(context, options);

            Notification notification = builder.build();

            terminaSosta(context, notification);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void terminaSosta (Context context, Notification notification){

        JSONObject data = null;
        try {
            data = new JSONObject(notification.getOptions().getDict().optString("data", "{}"));
        } catch (JSONException e) {
            LOG.e(TAG, "JSONObject parse notification data", e);
        }

        ParkingService.Env env = ParkingService.Env.Prod;
        if ("test".equalsIgnoreCase(data.optString(Notification.NotificationDataKeys.AMBIENTE)))
            env = ParkingService.Env.Test;

        ParkingService service = new ParkingService(context, env);

        if (data != null) {
            try {

                final String rifVendita = data.getString(Notification.NotificationDataKeys.RIF_VENDITA);
                notifications.put(rifVendita, notification);

                service.stopParking(
                        data.getString(Notification.NotificationDataKeys.TESSERA),
                        data.getString(Notification.NotificationDataKeys.GUID_ACCOUNT),
                        data.getString(Notification.NotificationDataKeys.VETTORE),
                        data.getString(Notification.NotificationDataKeys.RIF_VENDITA),
                        this);
            } catch (Exception e) {
                LOG.e(TAG, "Exception:", e);
            }
        }
    }

    @Override
    public void onFailure(Call call, IOException e) {
        LOG.e(TAG, "onFailure", e);
        //nel caso in cui il dispositivo ha wifi o connessione dati disattivata viene restituita una UnknownHostException perchè non è in grado di tradurre l'indirizzo internet in indirizzo IP
        //nel caso in cui il dispositivo è connesso e riesce ad ottenere l'indirizzo IP ma il server non risponde entro l'intervallo di tempo definito, viene restituita una SocketTimeoutException
        if(e instanceof UnknownHostException || e instanceof SocketTimeoutException){
            makeToast(getResources().getString(R.string.SOSTA_NETWORK_ERROR));
        }
        else this.notifyGenericError();
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

                    if (ParkingService.TERMINA_SOSTA.equalsIgnoreCase(method)) {
                        onParkingStopResponse(responseData, rifVendita);
                    } else this.notifyGenericError();
                } else this.notifyGenericError();
            } else this.notifyGenericError();
        } catch (JSONException ex) {
            this.notifyGenericError();
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

    protected void tellJavascriptToResume() {
        try {
            String appSchema = getString(getStringIdentifier("appschema"));
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(appSchema + "://go?action=sosta_refresh"));
            startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to send reload message to app", ex);
        }
    }
}
