package com.rozino.app.alarm;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * ساختار داده‌ی یک آلارم روزینو.
 *
 * این کلاس هم برای رد و بدل کردن اطلاعات بین اجزای مختلف (Plugin -> Scheduler
 * -> Receiver -> Service -> Activity) استفاده می‌شود و هم برای ذخیره‌سازی
 * پایدار در {@link AlarmStore} (برای زمان‌بندی مجدد بعد از ری‌استارت گوشی).
 *
 * id: شناسه‌ی رشته‌ای اصلی که از سمت جاوااسکریپت می‌آید (instanceId + reminder نوع).
 * numericId: همان id تبدیل‌شده به عدد صحیح ۳۲بیتی پایدار (برای AlarmManager/Notification).
 */
public class AlarmData {

    public String id;
    public int numericId;
    public String title;
    public String body;
    public String taskTitle;
    public String time;
    public String description;
    public long atMillis;

    public AlarmData() {}

    public AlarmData(String id, int numericId, String title, String body, String taskTitle, String time, String description, long atMillis) {
        this.id = id;
        this.numericId = numericId;
        this.title = title;
        this.body = body;
        this.taskTitle = taskTitle;
        this.time = time;
        this.description = description;
        this.atMillis = atMillis;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("numericId", numericId);
            o.put("title", title);
            o.put("body", body);
            o.put("taskTitle", taskTitle);
            o.put("time", time);
            o.put("description", description);
            o.put("atMillis", atMillis);
        } catch (JSONException e) {
            // نادیده گرفتن؛ فیلدهای ناقص در بدترین حالت null/۰ خواهند بود
        }
        return o;
    }

    public static AlarmData fromJson(JSONObject o) {
        AlarmData d = new AlarmData();
        d.id = o.optString("id", null);
        d.numericId = o.optInt("numericId", 0);
        d.title = o.optString("title", null);
        d.body = o.optString("body", null);
        d.taskTitle = o.optString("taskTitle", null);
        d.time = o.optString("time", null);
        d.description = o.optString("description", null);
        d.atMillis = o.optLong("atMillis", 0L);
        return d;
    }
}
