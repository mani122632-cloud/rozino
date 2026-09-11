package com.rozino.app.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.List;

/**
 * نقطه‌ی مرکزی ثبت/لغو آلارم‌های واقعی روی AlarmManager.
 *
 * این کلاس هم از سمت RozinoAlarmPlugin (وقتی جاوااسکریپت schedule/cancel
 * صدا می‌زند)، هم از سمت AlarmActivity (برای Snooze) و هم از سمت
 * BootReceiver (برای زمان‌بندی مجدد بعد از ری‌استارت) استفاده می‌شود؛
 * به همین دلیل منطق تکراری در یک جا نگه داشته شده.
 */
public class AlarmScheduler {

    public static final String EXTRA_ID = "rozino.alarm.id";
    public static final String EXTRA_NUMERIC_ID = "rozino.alarm.numericId";
    public static final String EXTRA_TITLE = "rozino.alarm.title";
    public static final String EXTRA_BODY = "rozino.alarm.body";
    public static final String EXTRA_TASK_TITLE = "rozino.alarm.taskTitle";
    public static final String EXTRA_TIME = "rozino.alarm.time";
    public static final String EXTRA_DESCRIPTION = "rozino.alarm.description";

    /** مدت پیش‌فرض Snooze به دقیقه؛ در آینده می‌تواند از تنظیمات کاربر خوانده شود. */
    public static final int DEFAULT_SNOOZE_MINUTES = 10;

    /**
     * تبدیل پایدار یک شناسه‌ی رشته‌ای دلخواه به یک عدد صحیح ۳۲بیتی.
     * این الگوریتم عیناً همان هش رشته‌ای است که در سمت جاوااسکریپت
     * (CapacitorNotificationPort._numId) استفاده می‌شود تا اگر لازم شد
     * روزی دو سیستم را با هم مقایسه کنیم، شناسه‌ها هم‌خوان باشند.
     */
    public static int stableId(String strId) {
        int h = 0;
        for (int i = 0; i < strId.length(); i++) {
            h = (h * 31 + strId.charAt(i));
        }
        int abs = Math.abs(h);
        return abs == 0 ? 1 : abs;
    }

    private static PendingIntent buildPendingIntent(Context context, AlarmData data) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_ID, data.id);
        intent.putExtra(EXTRA_NUMERIC_ID, data.numericId);
        intent.putExtra(EXTRA_TITLE, data.title);
        intent.putExtra(EXTRA_BODY, data.body);
        intent.putExtra(EXTRA_TASK_TITLE, data.taskTitle);
        intent.putExtra(EXTRA_TIME, data.time);
        intent.putExtra(EXTRA_DESCRIPTION, data.description);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, data.numericId, intent, flags);
    }

    /** آیا در این نسخه‌ی اندروید اجازه‌ی زمان‌بندی آلارم دقیق داریم؟ */
    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true; // قبل از Android 12 نیازی به این مجوز نیست
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    /**
     * ثبت یک آلارم واقعی. اگر دستگاه Android 12+ باشد و مجوز Exact Alarm
     * داده نشده باشد، به‌جای شکست کامل، از setAndAllowWhileIdle (آلارم
     * غیردقیق ولی همچنان کارکننده در Doze) استفاده می‌شود تا Reminder
     * کاربر حداقل با کمی تاخیر احتمالی فعال شود.
     */
    public static void schedule(Context context, AlarmData data, boolean persist) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(context, data);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, data.atMillis, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    // قبل از Android 12 مجوز جداگانه لازم نیست؛ می‌توان دقیق زمان‌بندی کرد
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, data.atMillis, pi);
                } else {
                    // مجوز Exact Alarm داده نشده: fallback به آلارم غیردقیق ولی Doze-safe
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, data.atMillis, pi);
                }
            } else {
                am.set(AlarmManager.RTC_WAKEUP, data.atMillis, pi);
            }
        } catch (SecurityException e) {
            // بعضی OEMها با وجود مجوز هم ممکن است SecurityException بدهند؛
            // در این حالت حداقل به آلارم غیردقیق برمی‌گردیم.
            am.set(AlarmManager.RTC_WAKEUP, data.atMillis, pi);
        }

        if (persist) {
            new AlarmStore(context).put(data);
        }
    }

    public static void cancel(Context context, String id, int numericId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, numericId, intent, flags);
        if (am != null && pi != null) {
            am.cancel(pi);
        }
        if (pi != null) pi.cancel();
        new AlarmStore(context).remove(id);
    }

    public static void cancelAll(Context context) {
        AlarmStore store = new AlarmStore(context);
        List<AlarmData> all = store.getAll();
        for (AlarmData a : all) {
            cancel(context, a.id, a.numericId);
        }
        store.clear();
    }

    /**
     * لغو آلارم فعلی (که تازه پخش می‌شد) و ثبت یک آلارم جدید برای
     * {@link #DEFAULT_SNOOZE_MINUTES} دقیقه‌ی بعد، با همان محتوا.
     * تسک اصلی در سمت جاوااسکریپت هیچ تغییری نمی‌کند — فقط یک آلارم
     * بومی جدید روی AlarmManager ثبت می‌شود.
     */
    public static void snooze(Context context, AlarmData data) {
        data.atMillis = System.currentTimeMillis() + DEFAULT_SNOOZE_MINUTES * 60_000L;
        schedule(context, data, true);
    }

    /** زمان‌بندی مجدد همه‌ی آلارم‌های آینده؛ برای استفاده در BootReceiver. */
    public static void rescheduleAllFromStore(Context context) {
        AlarmStore store = new AlarmStore(context);
        long now = System.currentTimeMillis();
        for (AlarmData a : store.getAll()) {
            if (a.atMillis > now) {
                schedule(context, a, false); // از قبل در store هست، نیازی به ذخیره‌ی دوباره نیست
            } else {
                store.remove(a.id);
            }
        }
    }
}
