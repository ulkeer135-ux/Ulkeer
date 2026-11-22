package com.amperimetre.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon açıldığında / uygulama güncellendiğinde servisi yeniden başlatır. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("enabled", true)) {
            ChargeService.start(c);
        }
    }
}
