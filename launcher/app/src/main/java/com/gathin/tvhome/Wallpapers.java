package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The built-in wallpaper set in assets/wallpapers (the scenes theme's pictures, drawn by tools/scenes_wallpapers.py):
 * which one is due now, and decoding it at the size Home shows it.
 */
final class Wallpapers {
    static final String DIR = "wallpapers";

    /** Opens the picture again for each decoding pass. */
    interface Source {
        InputStream open() throws IOException;
    }

    private Wallpapers() {
    }

    /** The set's file names in order; empty when the APK carries none. */
    static List<String> list(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            String[] names = ctx.getAssets().list(DIR);
            if (names != null) {
                for (String n : names) {
                    String lower = n.toLowerCase(Locale.ROOT);
                    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) out.add(n);
                }
            }
        } catch (IOException ignored) {
        }
        Collections.sort(out);
        return out;
    }

    /**
     * The picture due at this moment: a new one every {@code minutes}, in order, the same one for the whole
     * period. Counted in local time ({@code offsetMs} from UTC), so an hourly change lands on the hour in a
     * +5:30 zone too.
     */
    static int due(long nowMs, int offsetMs, int minutes, int count) {
        long local = nowMs + offsetMs;
        return (int) ((local / (minutes * 60_000L)) % count);
    }

    static Source asset(Context ctx, String name) {
        return () -> ctx.getAssets().open(DIR + "/" + name);
    }

    /**
     * Decodes at the size CENTER_CROP shows, so a picture costs one screen-sized bitmap (RGB_565, about 1.8 MB
     * at 1280×720). A picture over 4K is refused rather than decoded (#166); null on any failure.
     */
    static Bitmap decode(Source source, int screenW, int screenH) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream in = source.open()) {
                BitmapFactory.decodeStream(in, null, opts);
            }
            int w = opts.outWidth, h = opts.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = WallpaperSize.sampleSize(w, h, screenW, screenH);
            // The limit applies to what is decoded, so a 12 MP photo still works once it is sampled down.
            if (!WallpaperSize.accepted(w / sample, h / sample)) return null;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(w / sample, h / sample, screenW, screenH);
            if (scale != null) {
                opts.inScaled = true;
                opts.inDensity = scale[0];
                opts.inTargetDensity = scale[1];
            }
            opts.inPreferredConfig = Bitmap.Config.RGB_565;   // no alpha needed, half the memory
            try (InputStream in = source.open()) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError e) {
            // A bad or huge picture must never take the home screen down; the plain colour stays.
            return null;
        }
    }
}
