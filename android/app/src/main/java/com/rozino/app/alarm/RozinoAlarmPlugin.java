package com.rozino.app.alarm;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * پل بین جاوااسکریپت (ReminderEngine در www/index.html) و آلارم واقعی
 * اندروید. امضای متدها عمداً شبیه CapacitorNotificationPort فعلی است
 * (schedule/cancel/cancelAll) تا NativeAlarmPort در سمت جاوااسکریپت با
 * کمترین تغییر جای آن را بگیرد.
 *
 * این پلاگین به‌جای @capacitor/local-notifications، مستقیماً از
 * AlarmManager.setExactAndAllowWhileIdle استفاده می‌کند تا هم زمان‌بندی
 * دقیق باشد و هم صدای واقعی/صفحه‌ی تمام‌صفحه (که در AlarmRingService و
 * AlarmActivity پیاده‌سازی شده) فعال شود.
 */
@CapacitorPlugin(name = "RozinoAlarm")
public class RozinoAlarmPlugin extends Plugin {

    @PluginMethod
    public void schedule(PluginCall call) {
        String id = call.getString("id");
        if (id == null || id.isEmpty()) {
            call.reject("id الزامی است");
            return;
        }
        long at = readEpochMillis(call, "at");
        if (at <= 0) {
            call.reject("زمان (at) نامعتبر است");
            return;
        }

        AlarmData data = new AlarmData();
        data.id = id;
        data.numericId = AlarmScheduler.stableId(id);
        data.title = call.getString("title", "یادآوری روزینو");
        data.body = call.getString("body", "");
        data.taskTitle = call.getString("taskTitle", data.title);
        data.time = call.getString("time", "");
        data.description = call.getString("description", "");
        data.atMillis = at;

        AlarmScheduler.schedule(getContext(), data, true);
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        String id = call.getString("id");
        if (id == null || id.isEmpty()) {
            call.reject("id الزامی است");
            return;
        }
        AlarmScheduler.cancel(getContext(), id, AlarmScheduler.stableId(id));
        call.resolve();
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        AlarmScheduler.cancelAll(getContext());
        call.resolve();
    }

    /** آیا مجوز Exact Alarm (اندروید ۱۲+) داده شده است؟ */
    @PluginMethod
    public void canScheduleExactAlarms(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("value", AlarmScheduler.canScheduleExact(getContext()));
        call.resolve(ret);
    }

    /** کاربر را به صفحه‌ی تنظیمات "Alarms & reminders" برای اعطای Exact Alarm می‌برد. */
    @PluginMethod
    public void requestExactAlarmPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e) {
                // در صورت نبود این صفحه‌ی تنظیمات روی برخی OEMها، بی‌صدا رد می‌شویم
            }
        }
        call.resolve();
    }

    /** آیا مجوز نمایش Full-Screen Intent (اندروید ۱۴+) داده شده است؟ قبل از ۱۴ همیشه true است. */
    @PluginMethod
    public void canUseFullScreenIntent(PluginCall call) {
        JSObject ret = new JSObject();
        boolean value = true;
        if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                try {
                    value = nm.canUseFullScreenIntent();
                } catch (Exception e) {
                    value = true;
                }
            }
        }
        ret.put("value", value);
        call.resolve(ret);
    }

    /** کاربر را به صفحه‌ی تنظیمات مجوز Full-Screen Intent (اندروید ۱۴+) می‌برد. */
    @PluginMethod
    public void requestFullScreenIntentPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Intent intent = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT");
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e) {
                // بی‌صدا رد می‌شویم؛ همه‌ی سازنده‌ها این صفحه را ندارند
            }
        }
        call.resolve();
    }

    /**
     * عدد epoch-milliseconds را از PluginCall می‌خواند. چون پل جاوااسکریپت→
     * جاوا برای اعداد بزرگ ممکن است مقدار را به‌صورت Long یا Double برگرداند،
     * هر دو حالت را پوشش می‌دهیم.
     */
    private long readEpochMillis(PluginCall call, String name) {
        Long asLong = call.getLong(name);
        if (asLong != null) return asLong;
        Double asDouble = call.getDouble(name);
        if (asDouble != null) return asDouble.longValue();
        Integer asInt = call.getInt(name);
        if (asInt != null) return asInt.longValue();
        return 0L;
    }
}
