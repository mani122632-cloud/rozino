package com.rozino.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.rozino.app.alarm.RozinoAlarmPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // پلاگین‌های محلی (غیر npm) باید قبل از super.onCreate ثبت شوند
        registerPlugin(RozinoAlarmPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
