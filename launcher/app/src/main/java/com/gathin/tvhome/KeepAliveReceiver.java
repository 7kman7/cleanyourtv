package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Periodically or on screen-on checks that HomeKeyService is healthy and resets any stale timeouts.
 */
public class KeepAliveReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        HomeKeyService.allowStockLauncherUntil = 0;
        if (HomeKeyService.instance == null) {
            ServiceRepair.restore(context);
        }
    }
}
