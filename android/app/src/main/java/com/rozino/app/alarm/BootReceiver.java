package com.rozino.app.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * بعد از روشن شدن دوباره‌ی گوشی (یا آپدیت/جایگزینی اپ)، اندروید تمام
 * آلارم‌های ثبت‌شده در AlarmManager را فراموش می‌کند. این Receiver لیست
 * آلارم‌های پایدارشده در {@link AlarmStore} را می‌خواند و هر کدام که هنوز
 * در آینده است را دوباره زمان‌بندی می‌کند.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
            || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            AlarmScheduler.rescheduleAllFromStore(context.getApplicationContext());
        }
    }
}
