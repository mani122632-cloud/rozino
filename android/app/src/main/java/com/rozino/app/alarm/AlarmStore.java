package com.rozino.app.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ذخیره‌سازی ساده و پایدار لیست آلارم‌های زمان‌بندی‌شده روی دستگاه.
 *
 * چرا لازم است؟ چون AlarmManager بعد از ری‌استارت گوشی همه‌ی آلارم‌های
 * ثبت‌شده را فراموش می‌کند. با نگه‌داشتن یک کپی از آن‌ها در SharedPreferences،
 * BootReceiver می‌تواند بعد از روشن شدن دوباره‌ی گوشی همه‌ی آلارم‌های آینده
 * را از نو در AlarmManager ثبت کند.
 *
 * این Store فقط یک کش محلی روی اندروید است و هیچ ارتباطی با ذخیره‌سازی
 * تسک‌ها در localStorage/WebView ندارد؛ داده‌ی تسک‌ها دست‌نخورده می‌ماند.
 */
public class AlarmStore {

    private static final String PREFS_NAME = "rozino_alarms_store";
    private static final String KEY_ALARMS = "alarms_json";

    private final SharedPreferences prefs;

    public AlarmStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void put(AlarmData data) {
        List<AlarmData> all = getAll();
        List<AlarmData> next = new ArrayList<>();
        for (AlarmData a : all) {
            if (!a.id.equals(data.id)) next.add(a);
        }
        next.add(data);
        save(next);
    }

    public synchronized void remove(String id) {
        List<AlarmData> all = getAll();
        List<AlarmData> next = new ArrayList<>();
        for (AlarmData a : all) {
            if (!a.id.equals(id)) next.add(a);
        }
        save(next);
    }

    public synchronized void clear() {
        prefs.edit().remove(KEY_ALARMS).apply();
    }

    public synchronized List<AlarmData> getAll() {
        List<AlarmData> result = new ArrayList<>();
        String raw = prefs.getString(KEY_ALARMS, null);
        if (raw == null || raw.isEmpty()) return result;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) result.add(AlarmData.fromJson(o));
            }
        } catch (JSONException e) {
            // داده‌ی خراب را نادیده می‌گیریم؛ در بدترین حالت لیست خالی برمی‌گردد
        }
        return result;
    }

    private void save(List<AlarmData> list) {
        JSONArray arr = new JSONArray();
        for (AlarmData a : list) arr.put(a.toJson());
        prefs.edit().putString(KEY_ALARMS, arr.toString()).apply();
    }
}
