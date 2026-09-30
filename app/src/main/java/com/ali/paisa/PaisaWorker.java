package com.ali.paisa;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import org.json.JSONObject;

public class PaisaWorker extends Worker {
    static final int NOTIF_ID = 1001;
    static final String CHANNEL = "streak";
    // Raat 11 baje se subah 8 baje tak notification nahi aati. Badalna ho to yahan 23 aur 8 badlo.
    static final int QUIET_FROM = 23, QUIET_TO = 8;

    public PaisaWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        show(getApplicationContext(), false);
        return Result.success();
    }

    /** Returns "ok", "skip" or "permission". force = test button (quiet hours/target ignore). */
    static String show(Context ctx, boolean force) {
        String raw = ctx.getSharedPreferences("paisa", Context.MODE_PRIVATE).getString("state", null);
        String title = "Paisa";
        String text = null;
        try {
            if (raw != null) {
                JSONObject o = new JSONObject(raw);
                long target = o.optLong("target"), earned = o.optLong("earned");
                boolean achieved = o.optBoolean("achieved");
                int streak = o.optInt("streak");
                LocalDate stored = LocalDate.parse(o.optString("date"));
                LocalDate today = LocalDate.now();
                boolean same = stored.equals(today);
                if (target > 0 && !(same && achieved)) {
                    long remaining = same ? Math.max(0, target - earned) : target;
                    int st = same ? streak : ((stored.plusDays(1).equals(today) && achieved) ? streak : 0);
                    String amt = String.format(Locale.US, "%,d", remaining);
                    if (st > 0) {
                        title = "Streak khatre mein!";
                        text = "Rs " + amt + " aur kamao warna " + st + " din ki streak toot jayegi!";
                    } else {
                        title = "Aaj ka target baki hai";
                        text = "Rs " + amt + " aur kamao aur nayi streak shuru karo!";
                    }
                }
            }
        } catch (Exception ignored) { }

        if (!force) {
            int h = LocalTime.now().getHour();
            if (text == null || h >= QUIET_FROM || h < QUIET_TO) return "skip";
        } else if (text == null) {
            text = "Test notification kaam kar rahi hai.";
        }

        if (Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return "permission";
        }
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Target reminder", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_ID, n);
        return "ok";
    }
}
