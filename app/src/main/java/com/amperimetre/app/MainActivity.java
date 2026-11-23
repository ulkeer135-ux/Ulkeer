package com.amperimetre.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = 0xFF0E1117;
    private static final int CARD = 0xFF1A1F2B;
    private static final int GREEN = 0xFF3DDC84;
    private static final int RED = 0xFFFF6B6B;
    private static final int MUTED = 0xFF9AA4B2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvCurrent, tvState, tvLive, tvSession, tvUnit;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("prefs", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = text("Amperimetre", 20, Color.WHITE, true);
        root.addView(title);

        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        tvCurrent = text("—", 64, GREEN, true);
        tvCurrent.setGravity(Gravity.CENTER);
        tvState = text("", 15, MUTED, false);
        tvState.setGravity(Gravity.CENTER);
        hero.addView(tvCurrent);
        hero.addView(tvState);
        root.addView(hero);

        LinearLayout live = card();
        live.addView(text("ANLIK", 12, MUTED, true));
        tvLive = text("", 16, Color.WHITE, false);
        tvLive.setLineSpacing(0, 1.3f);
        live.addView(tvLive);
        root.addView(live);

        LinearLayout ses = card();
        ses.addView(text("ŞARJ OTURUMU  (ekran açılıp kapanınca sıfırlanmaz)", 12, MUTED, true));
        tvSession = text("", 16, Color.WHITE, false);
        tvSession.setLineSpacing(0, 1.3f);
        ses.addView(tvSession);
        root.addView(ses);

        Button bReset = button("Oturumu sıfırla");
        bReset.setOnClickListener(v -> {
            Intent i = new Intent(this, ChargeService.class).setAction(ChargeService.ACTION_RESET);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        });
        root.addView(bReset);

        Button bUnit = button("");
        tvUnit = bUnit;
        bUnit.setOnClickListener(v -> {
            int m = (sp.getInt("unitMode", 0) + 1) % 3;
            sp.edit().putInt("unitMode", m).apply();
            updateUnitLabel();
        });
        root.addView(bUnit);
        updateUnitLabel();

        Button bOpt = button("Pil optimizasyonundan muaf tut");
        bOpt.setOnClickListener(v -> requestIgnoreBatteryOpt());
        root.addView(bOpt);

        Button bStop = button("Servisi durdur ve çık");
        bStop.setOnClickListener(v -> {
            Intent i = new Intent(this, ChargeService.class).setAction(ChargeService.ACTION_STOP);
            startService(i);
            finishAndRemoveTask();
        });
        root.addView(bStop);

        TextView note = text("Ölçüm bir ön plan servisinde çalışır; şarj sırasında CPU uyanık tutulur "
                + "ve değerler bildirimde de güncellenir. Oturum sadece kablo çıkarılınca biter.",
                12, MUTED, false);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        ChargeService.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ChargeService.instance == null) ChargeService.start(this);
        handler.post(refresh);
        if (sp.getBoolean("keepScreen", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 500);
        }
    };

    private void render() {
        ChargeService s = ChargeService.instance;
        if (s == null) {
            tvCurrent.setText("…");
            tvState.setText("Servis başlatılıyor");
            return;
        }
        int ma = s.smoothMa;
        tvCurrent.setText(String.format(Locale.getDefault(), "%+d mA", ma));
        tvCurrent.setTextColor(ma >= 0 ? GREEN : RED);
        tvState.setText(s.stateLabel() + "  •  " + plugText(s.plugged));

        double w = ma * s.voltageMv / 1_000_000.0;
        StringBuilder live = new StringBuilder();
        live.append(String.format(Locale.getDefault(), "Akım (anlık):   %+d mA\n", s.currentMa));
        live.append(String.format(Locale.getDefault(), "Güç:   %.2f W\n", w));
        live.append(String.format(Locale.getDefault(), "Voltaj:   %.3f V\n", s.voltageMv / 1000.0));
        live.append(String.format(Locale.getDefault(), "Sıcaklık:   %.1f °C\n", s.tempDeciC / 10.0));
        live.append(String.format(Locale.getDefault(), "Seviye:   %%%d\n", s.level));
        live.append("Sağlık:   ").append(healthText(s.health)).append('\n');
        live.append("Ham değer:   ").append(s.rawCurrent);
        tvLive.setText(live.toString());

        StringBuilder ses = new StringBuilder();
        if (s.samples == 0) {
            ses.append("Şarj kablosu takıldığında ölçüm başlar.");
        } else {
            ses.append(s.sessionActive ? "Durum:   ölçülüyor\n" : "Durum:   bitti (son oturum)\n");
            ses.append("Süre:   ").append(formatDuration(s.sessionElapsedMs)).append('\n');
            ses.append(String.format(Locale.getDefault(), "Ortalama:   %d mA\n", s.sessionAvgMa()));
            ses.append(String.format(Locale.getDefault(), "Min / Maks:   %d / %d mA\n", s.minMa, s.maxMa));
            ses.append(String.format(Locale.getDefault(), "En yüksek güç:   %.1f W\n", s.peakW));
            ses.append(String.format(Locale.getDefault(), "Dolan:   %.0f mAh\n", s.sessionMah));
            ses.append(String.format(Locale.getDefault(), "Seviye:   %%%d → %%%d", s.startLevel, s.level));
            int mins = s.sessionActive ? s.minutesToFull() : -1;
            if (mins >= 0) {
                ses.append("\nTahmini dolma:   ").append(formatDuration(mins * 60_000L));
            }
        }
        tvSession.setText(ses.toString());
    }

    private void updateUnitLabel() {
        int m = sp.getInt("unitMode", 0);
        String[] names = {"Otomatik", "µA (÷1000)", "mA (olduğu gibi)"};
        tvUnit.setText("Ham değer birimi: " + names[m]);
    }

    private void requestIgnoreBatteryOpt() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        try {
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ---------------- yardımcılar ----------------

    private static String statusText(int st) {
        switch (st) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Şarj oluyor";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Deşarj";
            case BatteryManager.BATTERY_STATUS_FULL: return "Dolu";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Şarj olmuyor";
            default: return "Bilinmiyor";
        }
    }

    private static String plugText(int pl) {
        switch (pl) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "Adaptör (AC)";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Kablosuz";
            case 0: return "Kablo yok";
            default: return "Bağlı";
        }
    }

    private static String healthText(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "İyi";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Aşırı ısınma";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Ölü";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Aşırı voltaj";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Soğuk";
            default: return "Bilinmiyor";
        }
    }

    private static String formatDuration(long ms) {
        long sec = ms / 1000;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d sa %02d dk", h, m);
        return String.format(Locale.getDefault(), "%d dk %02d sn", m, s);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        l.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(16));
        l.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        l.setLayoutParams(lp);
        return l;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF2A3142);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setVisibility(View.VISIBLE);
        return b;
    }
}
