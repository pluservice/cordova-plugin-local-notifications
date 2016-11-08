package de.appplant.cordova.plugin.notification;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.BuildConfig;

import net.pluservice.plusnetworking.CertificatePinningRule;
import net.pluservice.plusnetworking.PlusNetworking;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Callback;

public class ParkingService {

    protected PlusNetworking networking;
    protected String baseUrl;
    protected Context context;

    public static final String ACCOUNT = "Account";
    public static final String PROLUNGA_SOSTA = "ProlungaSosta";
    public static final String TERMINA_SOSTA = "FineSosta";

    protected static final String SOSTA_PROD_SERVICE_URL = "https://www.autobus.it/proxy.imomo/proxy.ashx?url=sosta.v1se";
    protected static final String SOSTA_TEST_SERVICE_URL = "https://www.autobus.it/proxy.imomo.test/proxy.ashx?url=sosta.v1se.test";

    ParkingService(Context ctx, Env env) {
        CertificatePinningRule[] rules = new CertificatePinningRule[] {
                new CertificatePinningRule("*.autobus.it",
                        "sha256/vSoE44cRLURGb9PJ7gyZP0gtc2afZ9d8Tp5O78fdreM=", // CN=*.autobus.it,O=PLUSERVICE - S.R.L.,L=Senigallia,ST=Ancona,C=IT
                        "sha256/IQBnNBEiFuhj+8x6X8XLgh01V9Ic5/V3IRQLNFFc7v4=", // CN=GlobalSign Organization Validation CA - SHA256 - G2,O=GlobalSign nv-sa,C=BE
                        "sha256/K87oWBWM9UZfyddvDfoxL+8lpNyoUB2ptGtn0fv6G2Q="  // CN=GlobalSign Root CA,OU=Root CA,O=GlobalSign nv-sa,C=BE
                )
        };

        this.context = ctx;
        String appClient = "myCicero;"+ BuildConfig.VERSION_NAME;
        this.networking = new PlusNetworking("", appClient, rules);

        switch (env) {
            case Test: baseUrl = SOSTA_TEST_SERVICE_URL; break;
            case Prod: baseUrl = SOSTA_PROD_SERVICE_URL; break;
            default: baseUrl = SOSTA_PROD_SERVICE_URL;
        }
    }

    @NonNull
    protected String urlForResource(@NonNull String... pathComponents) {
        String ret = baseUrl;
        for (String component : pathComponents) {
            ret = ret.concat("/").concat(component);
        }
        return ret;
    }

    public void extendParkingTime(String parkingCard, String guidAccount, String endDate, String provider, String parkingReference, Callback callback) throws JSONException {
        Map<String, String> parameters = new HashMap<String, String>(5);
        parameters.put("DataTermine", endDate);
        parameters.put("Guid", guidAccount);
        parameters.put("RifVendita", parkingReference);
        parameters.put("Tessera", parkingCard);
        parameters.put("Vettore", provider);

        networking.post(urlForResource(ACCOUNT, PROLUNGA_SOSTA), new JSONObject(parameters), callback);
    }

    public void stopParking(String parkingCard, String guidAccount, String provider, String parkingReference, Callback callback) throws JSONException {
        Map<String, String> parameters = new HashMap<String, String>(4);
        parameters.put("Guid", guidAccount);
        parameters.put("RifVendita", parkingReference);
        parameters.put("Tessera", parkingCard);
        parameters.put("Vettore", provider);

        networking.post(urlForResource(ACCOUNT, TERMINA_SOSTA), new JSONObject(parameters), callback);
    }

    public enum Env {
        Test,
        Prod
    }
}
