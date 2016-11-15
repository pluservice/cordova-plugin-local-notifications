package de.appplant.cordova.plugin;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class LyfecycleHandler implements Application.ActivityLifecycleCallbacks {
    private static int resumed = 0;
    private static int paused = 0;
    private static int started = 0;
    private static int stopped = 0;

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (isMainActivity(activity))
            ++started;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (isMainActivity(activity))
            ++resumed;
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (isMainActivity(activity))
            ++paused;
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (isMainActivity(activity))
            ++stopped;
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    private static boolean isMainActivity(Activity activity) {
        return activity.getClass().getCanonicalName().endsWith(".MainActivity");
    }

    public static boolean isApplicationVisible() {
        return started > stopped;
    }

    public static boolean isApplicationInForeground() {
        return resumed > paused;
    }
}
