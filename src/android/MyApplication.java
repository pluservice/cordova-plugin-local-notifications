package de.appplant.cordova.plugin;

import android.app.Application;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new LyfecycleHandler());
    }
}

