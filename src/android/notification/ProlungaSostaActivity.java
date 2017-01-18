package de.appplant.cordova.plugin.notification;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;

import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import net.pluservice.myCiceroTest.R;

import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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

public class ProlungaSostaActivity extends Activity implements Callback{

    public final static String TAG = "ProlungaSostaActivity";
    private static final String ISO8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
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
        String message = "Vuoi prolungare la sosta di 30 minuti?";

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.alertDialog));

        alertDialogBuilder.setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Prolunga",new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        dialog.dismiss();
                        getNotificationAndProlungaSosta(context, bundle);
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
        if(bp != null) bp.setTextColor(Color.parseColor("#F5A623"));
        if(bn != null) bn.setTextColor(Color.GRAY);

    }

    private void getNotificationAndProlungaSosta (Context context, Bundle bundle){
        try {
            String data = bundle.getString(Options.EXTRA);
            JSONObject options = new JSONObject(data);

            Builder builder = new Builder(context, options);

            Notification notification = builder.build();

            prolungaSosta(context, notification);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void prolungaSosta (Context context, Notification notification){

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

                final DateFormat dateFormat = new SimpleDateFormat(ISO8601_DATE_FORMAT); // ISO Date format
                String fineSostaOrigString = data.getString(Notification.NotificationDataKeys.END_TIME);

                Date fineSostaOrig = parseDateString(dateFormat, fineSostaOrigString);
                int minutesToAdd = data.getInt(Notification.NotificationDataKeys.EXTEND_MINUTES);
                Date nuovoFineSosta = new Date(fineSostaOrig.getTime() + (minutesToAdd * 60 * 1000));

                final String rifVendita = data.getString(Notification.NotificationDataKeys.RIF_VENDITA);
                notifications.put(rifVendita, notification);

                service.extendParkingTime(
                        data.getString(Notification.NotificationDataKeys.TESSERA),
                        data.getString(Notification.NotificationDataKeys.GUID_ACCOUNT),
                        dateFormat.format(nuovoFineSosta),
                        data.getString(Notification.NotificationDataKeys.VETTORE),
                        rifVendita,
                        this);

            } catch (JSONException ex) {
                LOG.e(TAG, "Unable to parse json", ex);
            } catch (ParseException ex) {
                LOG.e(TAG, "Unable to parse fine sosta date format", ex);
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

                    if (ParkingService.PROLUNGA_SOSTA.equalsIgnoreCase(method)) {
                        onParkingExtendResponse(responseData, rifVendita);
                    } else this.notifyGenericError();
                } else this.notifyGenericError();
            } else this.notifyGenericError();
        } catch (JSONException ex) {
            this.notifyGenericError();
        }
    }

    protected void onParkingExtendResponse(final JSONObject responseData, final String rifVendita) throws JSONException {

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
                    data.put(Notification.NotificationDataKeys.END_TIME, dateFormat1.format(nuovaFineDate));

                    options.setText(formatted);
                    options.setExtra(data);
//                    options.setSound(null); // rimuovo il suono

                    Notification newNotification = new Notification(context, options, notification.builder, null);
                    newNotification.schedule();
                }
            }
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

    protected Date parseDateString(DateFormat dateFormat, String jsonDate) throws ParseException {
        if (jsonDate.endsWith("Z"))
            jsonDate = jsonDate.substring(0, jsonDate.length() - 1) + "+0000";

        return dateFormat.parse(jsonDate);
    }

    private static Date parseMVCJSONDate(String dateString) {
        int start = dateString.indexOf("(") + 1;
        int end = dateString.indexOf("+");
        long millis = Long.parseLong(dateString.substring(start, end));
        return new Date(millis);
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
