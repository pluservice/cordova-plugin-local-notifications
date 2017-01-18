package de.appplant.cordova.plugin.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

abstract public class AbstractTerminaSostaReceiver extends BroadcastReceiver {

    public final static String TAG = "TerminaSostaReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("TAG", "test");
//        Bundle bundle  = intent.getExtras();
//        JSONObject options;
//
//        try {
//            String data = bundle.getString(Options.EXTRA);
//            options = new JSONObject(data);
//        } catch (JSONException e) {
//            e.printStackTrace();
//            return;
//        }
//
//        Notification notification =
//                new Builder(context, options).build();
//
//        onTerminaSosta(context, notification);

        try {
            Bundle bundle = intent.getExtras();
            JSONObject options;

            try {
                String data = bundle.getString(Options.EXTRA);
                options = new JSONObject(data);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            Intent newIntent = new Intent(context, TerminaSostaActivity.class);
            newIntent.putExtra(Options.EXTRA, options.toString());
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(newIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    abstract public void onTerminaSosta (Context context, Notification notification);

}
