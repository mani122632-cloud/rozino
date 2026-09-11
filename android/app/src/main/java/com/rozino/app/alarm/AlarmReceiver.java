package com.rozino.app.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * وقتی AlarmManager زمان مقرر می‌رسد، این BroadcastReceiver صدا زده
 * می‌شود — حتی اگر اپ کاملاً بسته باشد. کار آن فقط شروع سرویس Foreground
 * است که مسئول پخش صدا، لرزش و نمایش Full-Screen Alarm است؛ خود این
 * Receiver نباید کار طولانی انجام دهد (طبق محدودیت‌های اندروید).
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        int numericId = intent.getIntExtra(AlarmScheduler.EXTRA_NUMERIC_ID, 0);
        String title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE);
        String body = intent.getStringExtra(AlarmScheduler.EXTRA_BODY);
        String taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE);
        String time = intent.getStringExtra(AlarmScheduler.EXTRA_TIME);
        String description = intent.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION);

        // این آلارم یک‌بار مصرف است؛ رخداد بعدی (فردا/تکرار بعدی) توسط
        // ReminderEngine در جاوااسکریپت، دفعه‌ی بعد که اپ باز شود، دوباره
        // زمان‌بندی خواهد شد. بنابراین همین‌جا از Store حذفش می‌کنیم.
        if (id != null) {
            new AlarmStore(context).remove(id);
        }

        Intent serviceIntent = new Intent(context, AlarmRingService.class);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_ID, id);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_NUMERIC_ID, numericId);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_TITLE, title);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_BODY, body);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_TASK_TITLE, taskTitle);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_TIME, time);
        serviceIntent.putExtra(AlarmScheduler.EXTRA_DESCRIPTION, description);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
