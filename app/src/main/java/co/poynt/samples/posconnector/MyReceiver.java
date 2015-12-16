package co.poynt.samples.posconnector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyReceiver extends BroadcastReceiver {
    private static final String TAG = "MyReceiver";
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received Intent" + intent);
        Intent startServiceIntent = new Intent (context, MyService.class);
        startServiceIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.startService(startServiceIntent);

    }
}
