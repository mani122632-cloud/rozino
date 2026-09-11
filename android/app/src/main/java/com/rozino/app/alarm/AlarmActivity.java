package com.rozino.app.alarm;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.rozino.app.R;

/**
 * صفحه‌ی تمام‌صفحه‌ی آلارم روزینو.
 *
 * این Activity توسط Full-Screen Intent نوتیفیکیشن (از {@link AlarmRingService})
 * باز می‌شود؛ اگر گوشی قفل باشد، روی صفحه‌ی قفل نمایش داده می‌شود (بدون نیاز به
 * باز کردن قفل). با ظاهر Premium/مینیمال هماهنگ با پالت Ivory/Charcoal/Gold وب‌اپ.
 */
public class AlarmActivity extends AppCompatActivity {

    private String id;
    private int numericId;
    private String title;
    private String body;
    private String taskTitle;
    private String time;
    private String description;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AlarmActivityTheme);
        super.onCreate(savedInstanceState);

        setShowOverLockScreenFlags();
        setContentView(R.layout.activity_alarm);

        readExtras(getIntent());
        bindViews();

        findViewById(R.id.btnDismiss).setOnClickListener(v -> onDismiss());
        findViewById(R.id.btnSnooze).setOnClickListener(v -> onSnooze());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readExtras(intent);
        bindViews();
    }

    /**
     * مجوز/پرچم‌های لازم برای نمایش Activity روی صفحه‌ی قفل و روشن کردن صفحه،
     * حتی اگر گوشی قفل و صفحه خاموش باشد. از دو API قدیمی و جدید هر دو
     * استفاده می‌شود تا روی رنج کامل minSdk 23+ کار کند.
     */
    private void setShowOverLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void readExtras(Intent intent) {
        id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        numericId = intent.getIntExtra(AlarmScheduler.EXTRA_NUMERIC_ID, -1);
        title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE);
        body = intent.getStringExtra(AlarmScheduler.EXTRA_BODY);
        taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE);
        time = intent.getStringExtra(AlarmScheduler.EXTRA_TIME);
        description = intent.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION);
    }

    private void bindViews() {
        TextView timeView = findViewById(R.id.alarmTime);
        TextView taskTitleView = findViewById(R.id.alarmTaskTitle);
        TextView descView = findViewById(R.id.alarmDescription);

        boolean hasTime = time != null && !time.isEmpty();
        timeView.setText(hasTime ? time : "");
        timeView.setVisibility(hasTime ? android.view.View.VISIBLE : android.view.View.GONE);

        String displayTitle = (taskTitle != null && !taskTitle.isEmpty())
            ? taskTitle
            : (title != null ? title : "یادآوری روزینو");
        taskTitleView.setText(displayTitle);

        if (description != null && !description.isEmpty()) {
            descView.setText(description);
            descView.setVisibility(android.view.View.VISIBLE);
        } else {
            descView.setVisibility(android.view.View.GONE);
        }
    }

    private void onDismiss() {
        // فقط صدا/نوتیفیکیشن را متوقف می‌کنیم. طبق معماری فعلی روزینو، وضعیت
        // "انجام‌شده" بودن تسک همچنان از داخل خود اپ (رابط کاربری وب) و به‌صورت
        // دستی توسط کاربر مدیریت می‌شود؛ چون اگر اپ کاملاً بسته باشد، هیچ پل
        // امنی برای نوشتن مستقیم در localStorage/WebView از این Activity وجود ندارد.
        stopAlarmService();
        finish();
    }

    private void onSnooze() {
        if (id != null && numericId != -1) {
            AlarmData data = new AlarmData(id, numericId, title, body, taskTitle, time, description, 0);
            AlarmScheduler.snooze(getApplicationContext(), data);
        }
        stopAlarmService();
        finish();
    }

    private void stopAlarmService() {
        Intent stopIntent = new Intent(this, AlarmRingService.class);
        stopIntent.setAction(AlarmRingService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(stopIntent);
        } else {
            startService(stopIntent);
        }
    }

    @Override
    public void onBackPressed() {
        // از بسته‌شدن تصادفی صفحه‌ی آلارم با دکمه‌ی Back جلوگیری می‌کنیم؛
        // کاربر باید صریحاً «خاموش کردن» یا «یادآوری بعدی» را بزند.
        // (صدای آلارم تا انتخاب صریح کاربر ادامه پیدا می‌کند)
    }
}
