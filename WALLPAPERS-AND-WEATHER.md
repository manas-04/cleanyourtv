# Wallpapers and weather on the cinematic shelf

This branch is a fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) that adds three things
on top of the reference launcher's cinematic shelf (`cinematic_home`). Everything else is the upstream
launcher, kept in step with its `main`.

Built and tested on a TCL 43S5K (G13, Google TV 14, MediaTek MT9221, 1 GB RAM).

## What it changes

- **14 built-in wallpapers**, the pictures of the [`scenes` theme](themes/scenes/) (also proposed upstream on its own), packed into
  the APK and shown in order, a new one every `builtin_wallpaper_minutes` (counted in local time, so an hourly change lands on
  the hour). A theme's own `wallpaper.jpg` or `wallpapers/` folder still wins.
- **The cinematic shelf over a picture**: the focused app's colour becomes a light tint instead of a flood,
  the dock band is frosted glass (a 160×90 blur of the still picture, made once per wallpaper and drawn
  through a `BitmapShader`, no live blur), and the picture dims while the grid is browsed.
- **Weather opposite the shelf**: the temperature, the condition, feels like, today's high and low, the next
  sunrise or sunset and the next four hours, with line icons for day and night. It fades with the shelf and
  hides when the forecast is more than six hours old.

## Network and data

The weather is the only network access, and it is off until you set `weather` to true and give coordinates. When it is on, the launcher
asks [Open-Meteo](https://open-meteo.com) (free, no account, no key) about once an hour:

```
https://api.open-meteo.com/v1/forecast?latitude=<lat>&longitude=<lon>&current=…&hourly=…&daily=…&timezone=auto
```

Only the coordinates and the unit are sent, from the TV's own IP address. The last answer is kept on the TV so
Home shows the weather at once; nothing else is stored or sent anywhere. This is why the branch adds the
`INTERNET` permission, which the reference launcher does not have.

## Turning it on

All of it is off by default. In `launcher/app/src/main/res/values/config.xml`, or in a theme's `config.json`:

```json
{
  "cinematic_home": true,
  "builtin_wallpaper_minutes": 60,
  "weather": true,
  "weather_place": "London",
  "weather_latitude": 51.5074,
  "weather_longitude": -0.1278,
  "weather_fahrenheit": false
}
```

Build and install as upstream does: `TV_HOST=<tv ip> launcher/build-and-install.sh`. The release APK is about
1.3 MB because of the pictures, over upstream's 400 KB budget; for the pictures alone without the extra code,
use the `scenes` theme with upstream's launcher instead.
