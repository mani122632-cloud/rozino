package com.rozino.app.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.rozino.app.R;

/**
 * سرویس Foreground مسئول «صدای واقعی آلارم».
 *
 * این سرویس سه کار انجام می‌دهد:
 *  ۱) نگه‌داشتن یک WakeLock موقت تا سیستم پردازنده را در حین پخش آلارم نخواباند.
 *  ۲) پخش صدای آلارم پیش‌فرض دستگاه (RingtoneManager.TYPE_ALARM) به‌صورت
 *     حلقه‌ای، با AudioAttributes از نوع USAGE_ALARM (یعنی از کانال صدای
 *     "آلارم" گوشی پخش می‌شود، نه رینگ‌تون یا مدیا).
 *  ۳) نمایش یک Notification با Full-Screen Intent که AlarmActivity را
 *     حتی روی صفحه‌ی قفل باز می‌کند.
 *
 * برای جلوگیری از تخلیه‌ی غیرضروری باتری در صورت فراموش‌شدن آلارم توسط
 * کاربر، بعد از {@link #AUTO_STOP_TIMEOUT_MS} به‌صورت خودکار متوقف می‌شود.
 */
public class AlarmRingService extends Service {

    public static final String ACTION_STOP = "com.rozino.app.alarm.action.STOP";
    public static final String ACTION_SNOOZE = "com.rozino.app.alarm.action.SNOOZE";

    public static final String CHANNEL_ID = "rozino_alarm_channel";
    private static final int NOTIFICATION_ID = 991;
    private static final long AUTO_STOP_TIMEOUT_MS = 10 * 60 * 1000L; // ۱۰ دقیقه

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler autoStopHandler = new Handler(Looper.getMainLooper());
    private Runnable autoStopRunnable;

    private static volatile boolean ringing = false;

    public static boolean isRinging() {
        return ringing;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRinging();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_SNOOZE.equals(intent.getAction())) {
            handleSnooze(intent);
            stopSelf();
            return START_NOT_STICKY;
        }

        String id = intent != null ? intent.getStringExtra(AlarmScheduler.EXTRA_ID) : null;
        int numericId = intent != null ? intent.getIntExtra(AlarmScheduler.EXTRA_NUMERIC_ID, NOTIFICATION_ID) : NOTIFICATION_ID;
        String title = intent != null ? intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) : null;
        String body = intent != null ? intent.getStringExtra(AlarmScheduler.EXTRA_BODY) : null;
        String taskTitle = intent != null ? intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) : null;
        String time = intent != null ? intent.getStringExtra(AlarmScheduler.EXTRA_TIME) : null;
        String description = intent != null ? intent.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION) : null;

        ensureChannel();
        acquireWakeLock();

        Notification notification = buildFullScreenNotification(numericId, id, title, body, taskTitle, time, description);
        startForeground(NOTIFICATION_ID, notification);

        startSoundAndVibration();
        scheduleAutoStop();

        ringing = true;
        return START_STICKY;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing != null) return;

        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "یادآوری‌های آلارم روزینو",
            NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("آلارم‌های زمان‌دار کارها با صدای هشدار و صفحه‌ی تمام‌صفحه");
        channel.setBypassDnd(true);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[] { 0, 800, 400, 800, 400, 800 });
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        // خود کانال هم صدای آلارم را ست می‌کند تا اگر روی برخی دستگاه‌ها
        // MediaPlayer به هر دلیلی پخش نشد، خود نوتیفیکیشن صدا داشته باشد.
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        Uri alarmSound = getAlarmSoundUri();
        if (alarmSound != null) {
            channel.setSound(alarmSound, attrs);
        }
        nm.createNotificationChannel(channel);
    }

    private Uri getAlarmSoundUri() {
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        if (uri == null) {
            // بعضی دستگاه‌ها (به‌ویژه امولاتورها) هیچ رینگ‌تون آلارمی ندارند
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        return uri;
    }

    private Notification buildFullScreenNotification(
        int numericId,
        String id,
        String title,
        String body,
        String taskTitle,
        String time,
        String description
    ) {
        Intent fullScreenIntent = new Intent(this, AlarmActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_ID, id);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_NUMERIC_ID, numericId);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_TITLE, title);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_BODY, body);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_TASK_TITLE, taskTitle);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_TIME, time);
        fullScreenIntent.putExtra(AlarmScheduler.EXTRA_DESCRIPTION, description);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, numericId, fullScreenIntent, piFlags);

        // ضربه روی خود نوتیفیکیشن هم همان صفحه‌ی آلارم را باز کند
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, numericId + 1, fullScreenIntent, piFlags);

        String displayTitle = (taskTitle != null && !taskTitle.isEmpty()) ? taskTitle : (title != null ? title : "یادآوری روزینو");
        String displayBody = body != null ? body : "";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "خاموش کردن", buildActionPendingIntent(numericId, ACTION_STOP, id, title, body, taskTitle, time, description))
            .addAction(0, "یادآوری بعدی", buildActionPendingIntent(numericId, ACTION_SNOOZE, id, title, body, taskTitle, time, description));

        return builder.build();
    }

    // اکشن‌های دکمه‌ی نوتیفیکیشن باید همه‌ی داده‌ی لازم را همراه خودشان حمل
    // کنند، چون تا آن لحظه ممکن است AlarmStore دیگر این آلارم را نداشته
    // باشد (چون AlarmReceiver همان لحظه‌ی دریافت، آن را از Store حذف می‌کند).
    private PendingIntent buildActionPendingIntent(
        int numericId,
        String action,
        String id,
        String title,
        String body,
        String taskTitle,
        String time,
        String description
    ) {
        Intent intent = new Intent(this, AlarmRingService.class);
        intent.setAction(action);
        intent.putExtra(AlarmScheduler.EXTRA_NUMERIC_ID, numericId);
        intent.putExtra(AlarmScheduler.EXTRA_ID, id);
        intent.putExtra(AlarmScheduler.EXTRA_TITLE, title);
        intent.putExtra(AlarmScheduler.EXTRA_BODY, body);
        intent.putExtra(AlarmScheduler.EXTRA_TASK_TITLE, taskTitle);
        intent.putExtra(AlarmScheduler.EXTRA_TIME, time);
        intent.putExtra(AlarmScheduler.EXTRA_DESCRIPTION, description);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        // action (STOP/SNOOZE) هم باید در uniqueness کد لحاظ شود وگرنه دو
        // PendingIntent با extras متفاوت ولی همان requestCode جایگزین هم می‌شوند
        int requestCode = numericId + (ACTION_STOP.equals(action) ? 3000 : 4000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(this, requestCode, intent, flags);
        }
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        if (wakeLock != null && wakeLock.isHeld()) return;
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Rozino:AlarmWakeLock"
        );
        wakeLock.acquire(AUTO_STOP_TIMEOUT_MS + 5000L); // سقف ایمنی؛ خودش هم تایم‌اوت دارد
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void startSoundAndVibration() {
        try {
            mediaPlayer = new MediaPlayer();
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            mediaPlayer.setAudioAttributes(attrs);
            Uri sound = getAlarmSoundUri();
            if (sound != null) {
                mediaPlayer.setDataSource(this, sound);
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception e) {
            // اگر پخش صدا با خطا مواجه شد، حداقل لرزش و نوتیفیکیشن Full-Screen باقی می‌مانند
            mediaPlayer = null;
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = { 0, 800, 400, 800, 400 };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void scheduleAutoStop() {
        autoStopRunnable = () -> {
            stopRinging();
            stopSelf();
        };
        autoStopHandler.postDelayed(autoStopRunnable, AUTO_STOP_TIMEOUT_MS);
    }

    /**
     * وقتی کاربر مستقیماً از روی دکمه‌ی نوتیفیکیشن (بدون باز کردن صفحه‌ی
     * تمام‌صفحه) "یادآوری بعدی" را می‌زند، همه‌ی داده‌ی لازم از extras خود
     * همین Intent (که در buildActionPendingIntent همراهش شده) بازسازی و
     * یک آلارم جدید برای {@link AlarmScheduler#DEFAULT_SNOOZE_MINUTES}
     * دقیقه‌ی بعد ثبت می‌شود. Task اصلی در جاوااسکریپت دست‌نخورده می‌ماند.
     */
    private void handleSnooze(Intent intent) {
        stopRinging();
        AlarmData data = new AlarmData();
        data.id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        data.numericId = intent.getIntExtra(AlarmScheduler.EXTRA_NUMERIC_ID, -1);
        data.title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE);
        data.body = intent.getStringExtra(AlarmScheduler.EXTRA_BODY);
        data.taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE);
        data.time = intent.getStringExtra(AlarmScheduler.EXTRA_TIME);
        data.description = intent.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION);
        if (data.id != null && data.numericId != -1) {
            AlarmScheduler.snooze(getApplicationContext(), data);
        }
    }

    private void stopRinging() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        if (autoStopRunnable != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
        }
        releaseWakeLock();
        ringing = false;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    @Override
    public void onDestroy() {
        stopRinging();
        super.onDestroy();
    }
}
