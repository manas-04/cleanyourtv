package com.gathin.tvhome;

import android.app.Activity;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Fills the shelf's weather views (layout/activity_main.xml, @id/weather) from a {@link Weather}. */
final class WeatherPanel {
    private static final int HOURS = 4;

    final View panel;
    private final Activity activity;
    private final ImageView icon;
    private final TextView temp, condition, details, place;
    private final LinearLayout hours;
    private final float density;
    /** What the hour strip shows, so the minute tick rebuilds it only when the next hours move on. */
    private Weather hoursFor;
    private LocalDateTime hoursFrom;

    WeatherPanel(Activity activity, String placeName) {
        this.activity = activity;
        panel = activity.findViewById(R.id.weather);
        icon = activity.findViewById(R.id.weatherIcon);
        temp = activity.findViewById(R.id.weatherTemp);
        condition = activity.findViewById(R.id.weatherCondition);
        details = activity.findViewById(R.id.weatherDetails);
        hours = activity.findViewById(R.id.weatherHours);
        place = activity.findViewById(R.id.weatherPlace);
        density = activity.getResources().getDisplayMetrics().density;
        place.setText(placeName);
        place.setVisibility(placeName.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Shows the weather, or hides the panel when there is no answer recent enough to trust. */
    void bind(Weather w) {
        long nowMs = System.currentTimeMillis();
        if (w == null || !w.freshAt(nowMs)) {
            panel.setVisibility(View.GONE);
            return;
        }
        LocalDateTime now = w.localNow(nowMs);
        panel.setVisibility(View.VISIBLE);
        icon.setImageResource(Weather.iconRes(w.code, w.day));
        temp.setText(degrees(w.temp));
        condition.setText(activity.getString(R.string.weather_joined, activity.getString(Weather.conditionRes(w.code)),
                activity.getString(R.string.weather_feels_like, degrees(w.feelsLike))));

        StringBuilder line = new StringBuilder();
        double[] highLow = w.highLow(now.toLocalDate());
        if (highLow != null) line.append(activity.getString(R.string.weather_high_low, degrees(highLow[0]), degrees(highLow[1])));
        Weather.SunEvent sun = w.nextSunEvent(now);
        if (sun != null) {
            if (line.length() > 0) line.append("\u2002\u00B7\u2002");
            String time = sun.time.format(timeFormat("hma", "Hm"));
            line.append(activity.getString(sun.sunrise ? R.string.weather_sunrise : R.string.weather_sunset, time));
        }
        details.setText(line);
        details.setVisibility(line.length() > 0 ? View.VISIBLE : View.GONE);
        List<Weather.Hour> next = w.nextHours(now, HOURS);
        LocalDateTime from = next.isEmpty() ? null : next.get(0).time;
        if (w != hoursFor || !Objects.equals(from, hoursFrom)) {
            fillHours(next);
            hoursFor = w;
            hoursFrom = from;
        }
    }

    private void fillHours(List<Weather.Hour> next) {
        hours.removeAllViews();
        hours.setVisibility(next.isEmpty() ? View.GONE : View.VISIBLE);
        DateTimeFormatter hourFormat = timeFormat("ha", "Hm");
        for (int i = 0; i < next.size(); i++) {
            Weather.Hour h = next.get(i);
            if (i > 0) {
                View divider = new View(activity);
                divider.setBackgroundColor(0x1AFFFFFF);
                hours.addView(divider, new LinearLayout.LayoutParams((int) density, LinearLayout.LayoutParams.MATCH_PARENT));
            }
            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView time = text(h.time.format(hourFormat).toUpperCase(Locale.getDefault()), 8.5f, 0x8CFFFFFF, R.font.outfit_medium);
            time.setLetterSpacing(0.14f);
            ImageView glyph = new ImageView(activity);
            glyph.setImageResource(Weather.iconRes(h.code, h.day));
            LinearLayout.LayoutParams glyphLp = new LinearLayout.LayoutParams((int) (15 * density), (int) (15 * density));
            glyphLp.topMargin = (int) (4 * density);
            glyphLp.bottomMargin = (int) (3 * density);
            column.addView(time);
            column.addView(glyph, glyphLp);
            column.addView(text(degrees(h.temp), 12f, 0xFFFFFFFF, R.font.outfit_regular));
            hours.addView(column, new LinearLayout.LayoutParams((int) (50 * density), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private TextView text(String s, float sp, int color, int font) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(activity.getResources().getFont(font));
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    /** "6:09 am" or "18:09", following the TV's 12/24-hour setting and language. */
    private DateTimeFormatter timeFormat(String skeleton12, String skeleton24) {
        Locale locale = Locale.getDefault();
        String skeleton = DateFormat.is24HourFormat(activity) ? skeleton24 : skeleton12;
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale);
    }

    static String degrees(double value) {
        return Math.round(value) + "°";
    }
}
