package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The shelf's weather: one request to Open-Meteo (open-meteo.com, free, no account) for the configured
 * coordinates, parsed into what the shelf shows, and kept between Home starts so the weather is there at once.
 * The only network access in the launcher; nothing but the coordinates and the unit is sent.
 */
final class Weather {
    /** Ask again after this long. */
    static final long REFRESH_MS = 60 * 60_000L;
    /** Older than this, an answer is no longer shown (the TV has been offline for a while). */
    static final long KEEP_MS = 6 * 60 * 60_000L;
    private static final String PREFS = "weather";
    private static final int MAX_BYTES = 256 * 1024;

    static final class Hour {
        final LocalDateTime time;
        final double temp;
        final int code;
        final boolean day;

        Hour(LocalDateTime time, double temp, int code, boolean day) {
            this.time = time;
            this.temp = temp;
            this.code = code;
            this.day = day;
        }
    }

    /** A sunrise or a sunset, in the place's local time. */
    static final class SunEvent {
        final boolean sunrise;
        final LocalDateTime time;

        SunEvent(boolean sunrise, LocalDateTime time) {
            this.sunrise = sunrise;
            this.time = time;
        }
    }

    final long fetchedAt;
    /** Offset of the place's local time from UTC, as Open-Meteo reports it (timezone=auto). */
    final int utcOffsetSeconds;
    final double temp, feelsLike;
    final int code;
    final boolean day;
    private final List<Hour> hours;
    private final List<LocalDate> days;
    private final List<double[]> highLow;
    private final List<LocalDateTime[]> sun;
    private final String json;

    Weather(String json, long fetchedAt, int utcOffsetSeconds, double temp, double feelsLike, int code, boolean day,
                    List<Hour> hours, List<LocalDate> days, List<double[]> highLow, List<LocalDateTime[]> sun) {
        this.json = json;
        this.fetchedAt = fetchedAt;
        this.utcOffsetSeconds = utcOffsetSeconds;
        this.temp = temp;
        this.feelsLike = feelsLike;
        this.code = code;
        this.day = day;
        this.hours = hours;
        this.days = days;
        this.highLow = highLow;
        this.sun = sun;
    }

    /** Identifies the request, so a cached answer for other coordinates or another unit is not shown. */
    static String key(double latitude, double longitude, boolean fahrenheit) {
        return String.format(Locale.ROOT, "%.4f,%.4f,%s", latitude, longitude, fahrenheit ? "F" : "C");
    }

    static String url(double latitude, double longitude, boolean fahrenheit) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                + "&hourly=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
                + "&timezone=auto&forecast_days=2%s", latitude, longitude, fahrenheit ? "&temperature_unit=fahrenheit" : "");
    }

    /** Blocking: call it off the main thread. */
    static Weather fetch(double latitude, double longitude, boolean fahrenheit) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(url(latitude, longitude, fahrenheit)).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        try {
            // Before the stream: getInputStream() throws on an error status instead of saying which.
            int status = c.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            return parse(read(c.getInputStream()), System.currentTimeMillis());
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IOException("answer too large");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static Weather parse(String text, long fetchedAt) throws JSONException {
        JSONObject o = new JSONObject(text);
        JSONObject now = o.getJSONObject("current");
        JSONObject h = o.getJSONObject("hourly");
        JSONArray ht = h.getJSONArray("time"), htemp = h.getJSONArray("temperature_2m"),
                hcode = h.getJSONArray("weather_code"), hday = h.getJSONArray("is_day");
        List<Hour> hours = new ArrayList<>();
        for (int i = 0; i < ht.length(); i++) {
            if (htemp.isNull(i) || hcode.isNull(i)) continue;
            hours.add(new Hour(LocalDateTime.parse(ht.getString(i)), htemp.getDouble(i), hcode.getInt(i), hday.optInt(i, 1) == 1));
        }
        JSONObject d = o.getJSONObject("daily");
        JSONArray dt = d.getJSONArray("time"), max = d.getJSONArray("temperature_2m_max"), min = d.getJSONArray("temperature_2m_min"),
                rise = d.getJSONArray("sunrise"), set = d.getJSONArray("sunset");
        List<LocalDate> days = new ArrayList<>();
        List<double[]> highLow = new ArrayList<>();
        List<LocalDateTime[]> sun = new ArrayList<>();
        for (int i = 0; i < dt.length(); i++) {
            days.add(LocalDate.parse(dt.getString(i)));
            highLow.add(new double[]{max.getDouble(i), min.getDouble(i)});
            sun.add(new LocalDateTime[]{LocalDateTime.parse(rise.getString(i)), LocalDateTime.parse(set.getString(i))});
        }
        return new Weather(text, fetchedAt, o.optInt("utc_offset_seconds", 0), now.getDouble("temperature_2m"),
                now.getDouble("apparent_temperature"), now.getInt("weather_code"), now.optInt("is_day", 1) == 1,
                Collections.unmodifiableList(hours), days, highLow, sun);
    }

    void save(Context ctx, String key) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("key", key).putString("json", json).putLong("at", fetchedAt).apply();
    }

    /** The last answer for this request, or null. */
    static Weather load(Context ctx, String key) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!key.equals(p.getString("key", null))) return null;
        try {
            String text = p.getString("json", null);
            return text == null ? null : parse(text, p.getLong("at", 0L));
        } catch (Exception e) {
            return null;
        }
    }

    /** Recent enough to show: an older answer means the TV has been offline for a while. */
    boolean freshAt(long nowMs) {
        return nowMs - fetchedAt <= KEEP_MS;
    }

    /** The time at the place at this moment, in its own local time like every time in the answer. */
    LocalDateTime localNow(long nowMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneOffset.ofTotalSeconds(utcOffsetSeconds));
    }

    /** The next n hours after the current one. */
    List<Hour> nextHours(LocalDateTime now, int n) {
        List<Hour> out = new ArrayList<>();
        for (Hour hour : hours) {
            if (hour.time.isAfter(now) && out.size() < n) out.add(hour);
        }
        return out;
    }

    /** The day's {high, low}, or null when the answer does not cover that day. */
    double[] highLow(LocalDate day) {
        int i = days.indexOf(day);
        return i >= 0 ? highLow.get(i) : null;
    }

    /** The next sunrise or sunset after now, or null when the answer has no more. */
    SunEvent nextSunEvent(LocalDateTime now) {
        for (LocalDateTime[] riseSet : sun) {
            if (riseSet[0].isAfter(now)) return new SunEvent(true, riseSet[0]);
            if (riseSet[1].isAfter(now)) return new SunEvent(false, riseSet[1]);
        }
        return null;
    }

    /** The condition's name, from the WMO weather code. */
    static int conditionRes(int code) {
        if (code == 0) return R.string.weather_clear;
        if (code == 1) return R.string.weather_mainly_clear;
        if (code == 2) return R.string.weather_partly_cloudy;
        if (code == 45 || code == 48) return R.string.weather_fog;
        if (code >= 51 && code <= 57) return R.string.weather_drizzle;
        if (code >= 61 && code <= 67) return R.string.weather_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.string.weather_snow;
        if (code >= 80 && code <= 82) return R.string.weather_showers;
        if (code >= 95) return R.string.weather_thunderstorm;
        return R.string.weather_cloudy;
    }

    /** The condition's icon, with the sun or the moon depending on the time of day. */
    static int iconRes(int code, boolean day) {
        if (code <= 1) return day ? R.drawable.wx_sun : R.drawable.wx_moon;
        if (code == 2) return day ? R.drawable.wx_cloud_sun : R.drawable.wx_cloud_moon;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.wx_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snow;
        if (code >= 95) return R.drawable.wx_storm;
        return R.drawable.wx_cloud;
    }
}
