package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the shelf's weather: what is shown at a given moment, the request and the codes. */
public class WeatherTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    /** Hourly from 00:00 today to 23:00 tomorrow; the sun rises at 06:10 and sets at 18:05. */
    private static Weather weather(long fetchedAt, int utcOffsetSeconds) {
        List<Weather.Hour> hours = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            LocalDateTime t = TODAY.atStartOfDay().plusHours(i);
            hours.add(new Weather.Hour(t, 20 + i % 5, 2, t.getHour() >= 6 && t.getHour() < 18));
        }
        List<LocalDate> days = Arrays.asList(TODAY, TODAY.plusDays(1));
        List<double[]> highLow = Arrays.asList(new double[]{29, 19}, new double[]{27, 18});
        List<LocalDateTime[]> sun = Arrays.asList(
                new LocalDateTime[]{TODAY.atTime(6, 10), TODAY.atTime(18, 5)},
                new LocalDateTime[]{TODAY.plusDays(1).atTime(6, 11), TODAY.plusDays(1).atTime(18, 4)});
        return new Weather("", fetchedAt, utcOffsetSeconds, 24, 26, 2, true, hours, days, highLow, sun);
    }

    @Test
    public void nextHoursStartAfterTheCurrentOne() {
        Weather w = weather(0, 0);
        List<Weather.Hour> next = w.nextHours(TODAY.atTime(14, 20), 4);
        assertEquals(4, next.size());
        assertEquals(TODAY.atTime(15, 0), next.get(0).time);
        assertEquals(TODAY.atTime(18, 0), next.get(3).time);
        assertEquals(TODAY.atTime(15, 0), w.nextHours(TODAY.atTime(14, 0), 1).get(0).time);
        assertEquals(2, w.nextHours(TODAY.plusDays(1).atTime(21, 30), 4).size());   // the end of the answer
    }

    @Test
    public void highAndLowAreForTheAskedDayOnly() {
        Weather w = weather(0, 0);
        assertEquals(29, w.highLow(TODAY)[0], 0);
        assertEquals(18, w.highLow(TODAY.plusDays(1))[1], 0);
        assertNull(w.highLow(TODAY.plusDays(2)));
    }

    @Test
    public void nextSunEventIsTheFirstStillAhead() {
        Weather w = weather(0, 0);
        Weather.SunEvent dawn = w.nextSunEvent(TODAY.atTime(5, 0));
        assertTrue(dawn.sunrise);
        assertEquals(TODAY.atTime(6, 10), dawn.time);
        Weather.SunEvent noon = w.nextSunEvent(TODAY.atTime(12, 0));
        assertFalse(noon.sunrise);
        assertEquals(TODAY.atTime(18, 5), noon.time);
        Weather.SunEvent night = w.nextSunEvent(TODAY.atTime(22, 0));
        assertTrue(night.sunrise);
        assertEquals(TODAY.plusDays(1).atTime(6, 11), night.time);
        assertNull(w.nextSunEvent(TODAY.plusDays(1).atTime(19, 0)));
    }

    @Test
    public void localNowIsThePlacesOwnTime() {
        long midnightUtc = TODAY.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(TODAY.atTime(5, 30), weather(0, 19_800).localNow(midnightUtc));
        assertEquals(TODAY.minusDays(1).atTime(19, 0), weather(0, -18_000).localNow(midnightUtc));
    }

    @Test
    public void anAnswerIsShownForSixHours() {
        long fetched = 1_792_540_800_000L;
        Weather w = weather(fetched, 0);
        assertTrue(w.freshAt(fetched));
        assertTrue(w.freshAt(fetched + Weather.KEEP_MS));
        assertFalse(w.freshAt(fetched + Weather.KEEP_MS + 1));
    }

    @Test
    public void requestUsesDotDecimalsInEveryLanguage() {
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            String celsius = Weather.url(51.5074, -0.1278, false);
            assertTrue(celsius, celsius.contains("latitude=51.5074&longitude=-0.1278&"));
            assertFalse(celsius, celsius.contains("temperature_unit"));
            assertTrue(Weather.url(51.5074, -0.1278, true).endsWith("&temperature_unit=fahrenheit"));
            assertEquals("51.5074,-0.1278,C", Weather.key(51.5074, -0.1278, false));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    public void weatherCodesMapToConditionsAndIcons() {
        assertEquals(R.string.weather_clear, Weather.conditionRes(0));
        assertEquals(R.string.weather_partly_cloudy, Weather.conditionRes(2));
        assertEquals(R.string.weather_cloudy, Weather.conditionRes(3));
        assertEquals(R.string.weather_fog, Weather.conditionRes(48));
        assertEquals(R.string.weather_drizzle, Weather.conditionRes(53));
        assertEquals(R.string.weather_rain, Weather.conditionRes(63));
        assertEquals(R.string.weather_snow, Weather.conditionRes(86));
        assertEquals(R.string.weather_showers, Weather.conditionRes(81));
        assertEquals(R.string.weather_thunderstorm, Weather.conditionRes(99));

        assertEquals(R.drawable.wx_sun, Weather.iconRes(1, true));
        assertEquals(R.drawable.wx_moon, Weather.iconRes(0, false));
        assertEquals(R.drawable.wx_cloud_moon, Weather.iconRes(2, false));
        assertEquals(R.drawable.wx_rain, Weather.iconRes(81, true));      // showers draw as rain
        assertEquals(R.drawable.wx_snow, Weather.iconRes(75, true));
        assertEquals(R.drawable.wx_storm, Weather.iconRes(95, false));
        assertEquals(R.drawable.wx_cloud, Weather.iconRes(3, true));
    }

    @Test
    public void degreesAreWholeNumbers() {
        assertEquals("21°", WeatherPanel.degrees(21.4));
        assertEquals("22°", WeatherPanel.degrees(21.5));
        assertEquals("0°", WeatherPanel.degrees(-0.4));    // never "-0°"
        assertEquals("-4°", WeatherPanel.degrees(-3.6));
    }
}
