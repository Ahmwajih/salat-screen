# Salat Screen

An open-source Android TV app for Mi Box and other Android TV devices. It works as a screensaver and as a small prayer companion.

- Prayer times and a live countdown to the next prayer, for any city
- Adhan at prayer time, with a full-screen prayer card that wakes the TV
- Quran player with Mishary Rashid Alafasy by default; add any reciter from the mp3quran.net catalogue
- Upcoming European football matches (Champions League, Europa League, top five leagues) with favorite teams
- Background photos of mosques and nature, with the photographer credited on screen
- Everything configurable on the TV: city, calculation method, 24-hour clock, accent color, leagues, adhan sound and volume, photo interval

No ads, no accounts, no analytics.

## Requirements

- Android TV device, Android 7.0 (API 24) or newer
- Internet connection (prayer times, photos, Quran audio and match data)
- To build: JDK 17 and the Android SDK (platform 34, build-tools 34.0.0)

## Build

```
git clone https://github.com/<your-user>/salat-screen.git
cd salat-screen
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install on a Mi Box / Android TV

1. Enable Developer options (Settings > Device Preferences > About > press Build seven times), then turn on ADB debugging.
2. Find the box's IP address (Settings > Network) and connect:
   ```
   adb connect <box-ip>:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Open the app once, set your city in Settings and press Save. Opening it once also lets it schedule the adhan after reboots.

### Optional permissions (run once from a computer)

```
# Let the app set itself as the system screensaver
adb shell pm grant com.ahmwajih.prayerdream android.permission.WRITE_SECURE_SETTINGS

# Show the prayer card over other apps when the adhan starts
adb shell appops set com.ahmwajih.prayerdream SYSTEM_ALERT_WINDOW allow
```

## Data sources and attribution

| Feature | Source |
|---|---|
| Prayer times | [Aladhan API](https://aladhan.com/prayer-times-api) |
| Quran audio and reciters | [mp3quran.net](https://mp3quran.net) |
| Football fixtures | [TheSportsDB](https://www.thesportsdb.com) (free test key `123`; use your own key for heavier use) |
| Background photos | [Wikimedia Commons](https://commons.wikimedia.org) featured pictures. Only Creative Commons and public-domain licences are used, and the author and licence are shown on screen. |
| Default adhan | Two public recordings from IslamCan, linked and downloaded at runtime, not bundled. Check their terms, or set your own URL in Settings. |

These services are third parties and may change or limit access. This project is not affiliated with any of them.

## Notes

- Prayer times come from the selected calculation method and may differ slightly from your local mosque. Check with your local authority if exact times matter.
- The Xiaomi firmware may restrict background work on some devices. If the adhan does not fire, open the app once after each reboot.

## Contributing

Issues and pull requests are welcome. Keep changes small and focused, and make sure `./gradlew assembleDebug` passes.

## License

[MIT](LICENSE)
