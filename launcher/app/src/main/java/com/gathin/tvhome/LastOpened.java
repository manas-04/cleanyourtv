package com.gathin.tvhome;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * When an app was last opened, for the cinematic shelf's "Last opened" line. Android's usage statistics
 * when the launcher may read them (build-and-install.sh allows GET_USAGE_STATS with appops), so the
 * remote's own Netflix or YouTube button counts too; otherwise the launcher's own record of the apps it
 * opened. The later of the two wins.
 */
final class LastOpened {
    private static final String PREFS = "last_opened";
    private static final long WINDOW_MS = 30L * DateUtils.DAY_IN_MILLIS;
    private final Map<String, Long> usage = new HashMap<>();

    /** Notes that Home just opened this app. */
    static void record(Context ctx, String pkg) {
        prefs(ctx).edit().putLong(pkg, System.currentTimeMillis()).apply();
    }

    /** Reads the usage statistics once; call it on resume, not on every focus change. */
    void refresh(Context ctx) {
        usage.clear();
        try {
            UsageStatsManager usm = ctx.getSystemService(UsageStatsManager.class);
            if (usm == null) return;
            long now = System.currentTimeMillis();
            for (Map.Entry<String, UsageStats> e : usm.queryAndAggregateUsageStats(now - WINDOW_MS, now).entrySet()) {
                long t = e.getValue().getLastTimeUsed();
                if (t > 0) usage.put(e.getKey(), t);
            }
        } catch (Exception ignored) {
            // Not allowed: some builds throw, others return nothing. The own record still answers.
        }
    }

    /** Milliseconds since the epoch, or 0 when the app was never seen opening. */
    long get(Context ctx, String pkg) {
        if (pkg == null) return 0L;
        long own = prefs(ctx).getLong(pkg, 0L);
        Long used = usage.get(pkg);
        return Math.max(own, used != null ? used : 0L);
    }

    /**
     * "5 minutes ago" within the hour, then the day and the time: "today, 11:40 am", "yesterday, 9:40 pm",
     * "3 days ago, 9:40 pm", "Oct 12, 9:40 pm". The parts are Android's own wording in the TV's language.
     */
    static String describe(Context ctx, long when) {
        long now = System.currentTimeMillis();
        // Under a minute Android says "0 minutes ago"; one minute reads better.
        when = Math.min(when, now - DateUtils.MINUTE_IN_MILLIS);
        if (now - when < DateUtils.HOUR_IN_MILLIS) {
            return DateUtils.getRelativeTimeSpanString(when, now, DateUtils.MINUTE_IN_MILLIS).toString();
        }
        String day = DateUtils.getRelativeTimeSpanString(when, now, DateUtils.DAY_IN_MILLIS).toString();
        // It follows "Last opened" mid-sentence: "yesterday", not "Yesterday". Month names keep their capital.
        if (Locale.getDefault().getLanguage().equals("en") && (day.startsWith("Yesterday") || day.startsWith("Today"))) {
            day = Character.toLowerCase(day.charAt(0)) + day.substring(1);
        }
        return day + ", " + DateUtils.formatDateTime(ctx, when, DateUtils.FORMAT_SHOW_TIME);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
