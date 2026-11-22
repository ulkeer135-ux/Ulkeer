package com.amperimetre.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Şarj akımını sürekli ölçen ön plan servisi.
 *
 * Ampere gibi uygulamalardan farkı: ölçüm, ekran açılıp kapandığında SIFIRLANMAZ.
 * Oturum yalnızca şarj kablosu takıldığında başlar, çıkarıldığında biter.
 * Şarj sırasında PARTIAL_WAKE_LOCK tutulur, böylece ekran kapalıyken de
 * örnekleme (1 sn aralıkla) ve mAh entegrasyonu devam eder.
 * Oturum verileri SharedPreferences'a yazılır; servis sistem tarafından
 * öldürülüp yeniden başlatılsa bile oturum kaldığı yerden sürer.
 */
public class ChargeService extends Service {

    public static final String CHANNEL_ID = "charge_monitor";
    public static final int NOTIF_ID = 1;
    public static final String ACTION_STOP = "com.amperimetre.app.STOP";
    public static final String ACTION_RESET = "com.amperimetre.app.RESET";

    /** Aktivitenin okuduğu canlı örnek. */
    public static volatile ChargeService instance;

    private static final long TICK_MS = 1000;
    private static final int SMOOTH_WINDOW = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BatteryManager bm;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences sp;
    private NotificationManager nm;
    private final ArrayDeque<Integer> window = new ArrayDeque<>();
    private int tickCount;

    // ---- Canlı değerler ----
    public volatile int rawCurrent;     // cihazın verdiği ham değer
    public volatile int currentMa;      // işaretli: + şarj, - deşarj
    public volatile int smoothMa;       // son 5 sn ortalaması
    public volatile int voltageMv;
    public volatile int tempDeciC;
    public volatile int level;
    public volatile int status;
    public volatile int plugged;
    public volatile int health;
    public volatile long chargeCounterUah;

    // ---- Oturum (kablo takılı olduğu süre) ----
    public volatile boolean sessionActive;
    public volatile long sessionStartWall;   // System.currentTimeMillis
    public volatile long sessionElapsedMs;
    public volatile double sessionMah;
    public volatile long sumMa;
    public volatile long samples;
    public volatile int minMa = Integer.MAX_VALUE;
    public volatile int maxMa = Integer.MIN_VALUE;
    public volatile int startLevel;
    public volatile double peakW;
    private long lastSampleElapsed;          // SystemClock.elapsedRealtime (derin uykuda da sayar)

    // ----------------------------------------------------------------------

    public static void start(Context c) {
        c.getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        Intent i = new Intent(c, ChargeService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sp = getSharedPreferences("prefs", MODE_PRIVATE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Amperimetre:charge");
        wakeLock.setReferenceCounted(false);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Şarj ölçümü",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        restoreSession();
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            sp.edit().putBoolean("enabled", false).apply();
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESET.equals(intent.getAction())) {
            resetSession(plugged != 0);
        }
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            sample();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private void sample() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = (lvl >= 0 && scale > 0) ? Math.round(lvl * 100f / scale) : -1;
            status = b.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            health = b.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            int v = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            if (v > 100000) v /= 1000;          // bazı cihazlar µV verir
            voltageMv = v;
            tempDeciC = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        }

        int raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        if (raw == Integer.MIN_VALUE) raw = 0;
        rawCurrent = raw;
        long cc = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        chargeCounterUah = (cc == Long.MIN_VALUE) ? 0 : cc;

        int ma = Math.abs(toMilliAmps(raw));
        boolean charging = plugged != 0;
        boolean actuallyCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        currentMa = (charging && actuallyCharging) ? ma : -ma;

        window.addLast(currentMa);
        while (window.size() > SMOOTH_WINDOW) window.removeFirst();
        long s = 0;
        for (int x : window) s += x;
        smoothMa = (int) (s / window.size());

        // ---- Oturum yönetimi: SADECE kablo durumuna bağlı, ekran olaylarına değil ----
        long now = SystemClock.elapsedRealtime();
        if (charging && !sessionActive) {
            resetSession(true);
        } else if (!charging && sessionActive) {
            sessionActive = false;          // son oturum ekranda kalır
            releaseWakeLock();
            persist();
        }

        if (sessionActive) {
            if (!wakeLock.isHeld()) wakeLock.acquire();
            long dt = now - lastSampleElapsed;
            if (dt > 0 && dt < 120_000) {    // çok uzun boşlukları (servis ölümü) entegre etme
                sessionMah += currentMa * (dt / 3_600_000.0);
                sessionElapsedMs += dt;
            }
            lastSampleElapsed = now;
            sumMa += currentMa;
            samples++;
            if (currentMa < minMa) minMa = currentMa;
            if (currentMa > maxMa) maxMa = currentMa;
            double w = currentMa * voltageMv / 1_000_000.0;
            if (w > peakW) peakW = w;
        } else {
            releaseWakeLock();
        }

        tickCount++;
        if (tickCount % 2 == 0) nm.notify(NOTIF_ID, buildNotification());
        if (tickCount % 10 == 0) persist();
    }

    /** Birim: 0=otomatik, 1=µA, 2=mA */
    private int toMilliAmps(int raw) {
        int mode = sp.getInt("unitMode", 0);
        if (mode == 1) return raw / 1000;
        if (mode == 2) return raw;
        // Otomatik: ±10000'den büyük bir değer görüldüyse cihaz µA raporluyor demektir.
        boolean micro = sp.getBoolean("autoMicro", false);
        if (!micro && Math.abs(raw) >= 10000) {
            micro = true;
            sp.edit().putBoolean("autoMicro", true).apply();
        }
        return micro ? raw / 1000 : raw;
    }

    private void resetSession(boolean active) {
        sessionActive = active;
        sessionStartWall = System.currentTimeMillis();
        sessionElapsedMs = 0;
        sessionMah = 0;
        sumMa = 0;
        samples = 0;
        minMa = Integer.MAX_VALUE;
        maxMa = Integer.MIN_VALUE;
        peakW = 0;
        startLevel = level;
        lastSampleElapsed = SystemClock.elapsedRealtime();
        persist();
    }

    private void persist() {
        sp.edit()
                .putBoolean("s_active", sessionActive)
                .putLong("s_start", sessionStartWall)
                .putLong("s_elapsed", sessionElapsedMs)
                .putFloat("s_mah", (float) sessionMah)
                .putLong("s_sum", sumMa)
                .putLong("s_n", samples)
                .putInt("s_min", minMa)
                .putInt("s_max", maxMa)
                .putInt("s_lvl0", startLevel)
                .putFloat("s_peakw", (float) peakW)
                .putLong("s_last", lastSampleElapsed)
                .apply();
    }

    private void restoreSession() {
        sessionActive = sp.getBoolean("s_active", false);
        sessionStartWall = sp.getLong("s_start", 0);
        sessionElapsedMs = sp.getLong("s_elapsed", 0);
        sessionMah = sp.getFloat("s_mah", 0);
        sumMa = sp.getLong("s_sum", 0);
        samples = sp.getLong("s_n", 0);
        minMa = sp.getInt("s_min", Integer.MAX_VALUE);
        maxMa = sp.getInt("s_max", Integer.MIN_VALUE);
        startLevel = sp.getInt("s_lvl0", 0);
        peakW = sp.getFloat("s_peakw", 0);
        lastSampleElapsed = sp.getLong("s_last", SystemClock.elapsedRealtime());
        // Telefon yeniden başlatıldıysa elapsedRealtime sıfırlanmıştır
        if (lastSampleElapsed > SystemClock.elapsedRealtime()) {
            lastSampleElapsed = SystemClock.elapsedRealtime();
        }
    }

    public int sessionAvgMa() {
        return samples > 0 ? (int) (sumMa / samples) : 0;
    }

    /** Tahmini tam dolma süresi (dk), hesaplanamıyorsa -1. */
    public int minutesToFull() {
        int avg = smoothMa;
        if (avg <= 0 || level <= 0 || level >= 100 || chargeCounterUah <= 0) return -1;
        double nowMah = chargeCounterUah / 1000.0;
        double fullMah = nowMah * 100.0 / level;
        if (fullMah < 500 || fullMah > 30000) return -1;   // anlamsız değer
        double remaining = fullMah - nowMah;
        return (int) Math.round(remaining / avg * 60.0);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ChargeService.class).setAction(ACTION_STOP);
        PendingIntent ps = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        double w = smoothMa * voltageMv / 1_000_000.0;
        String title = String.format(Locale.getDefault(), "%+d mA  •  %.1f W", smoothMa, w);
        String text;
        if (sessionActive) {
            text = String.format(Locale.getDefault(), "%%%d  •  %.1f°C  •  ort %d mA  •  +%.0f mAh",
                    level, tempDeciC / 10f, sessionAvgMa(), sessionMah);
        } else {
            text = String.format(Locale.getDefault(), "%%%d  •  %.1f°C  •  şarjda değil",
                    level, tempDeciC / 10f);
        }

        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        nb.setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, "Durdur", ps).build());
        if (Build.VERSION.SDK_INT >= 31) {
            nb.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return nb.build();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        persist();
        releaseWakeLock();
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
