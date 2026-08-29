# SkyCast

SkyCast is a native weather app built for both iOS (SwiftUI) and Android (Jetpack
Compose). It shows current conditions and a five day forecast for places the user
chooses, along with aviation weather reports, moon phase data and aurora visibility,
and keeps working offline using local cache.

## Installation and running

### Install on Android without building

A ready to install APK is committed at **[`dist/SkyCast.apk`](dist/SkyCast.apk)**. No
toolchain, no API key, no build needed. It runs on Android 8.0 (API 26) or newer.

Over USB:

```bash
adb install -r dist/SkyCast.apk
```

Or copy the file to the phone and tap it, then allow installation from unknown sources
when prompted.

Everything below is only needed if you want to build from source.

### Prerequisites

**iOS**

- macOS with **Xcode 26 or newer**. The app targets iOS 26 (or the iOS 27 betas) and uses Liquid Glass, so it needs the iOS 26 SDK. Xcode 16 and earlier cannot build it.
- An **iOS 26 simulator runtime**, installed via Xcode ▸ Settings ▸ Components. An
  older simulator refuses to install the app with "Requires a Newer Version of iOS".
- XcodeGen is *not* required. `SkyCast.xcodeproj` is committed, so you only need
  XcodeGen if you edit `ios/project.yml`.

**Android**

- **JDK 21.** Newer JDKs are rejected by the Android Gradle Plugin.
- Android SDK with **Platform 36** and **Build-Tools 36**. Android Studio 2024.1+ is
  optional, since the SDK alone is enough to build from the command line.

Run `./scripts/doctor.sh` to check all of the above and print whatever is missing.

### Setup

```bash
git clone https://github.com/ihaveacrowuwu/mobile-dev.git
cd mobile-dev
./scripts/bootstrap.sh     # creates the config files, installs git hooks
```

`bootstrap.sh` is optional and safe to re-run. It only creates the two gitignored
config files described below and never overwrites an existing one.

### API key

**The API key is optional for building.** Without one, both apps still build, install
and launch without crashing. They show an "API key not configured" screen naming the
file to edit, instead of weather data. Add a key to see live weather.

Get a free key at [openweathermap.org/api_keys](https://home.openweathermap.org/api_keys).
A newly created key can take up to two hours to activate before it stops returning 401.

```bash
# iOS: ios/Config/Secrets.xcconfig
OPEN_WEATHER_API_KEY = your_key_here

# Android: android/local.properties
OPEN_WEATHER_API_KEY=your_key_here
```

Both files are gitignored, so a key never enters git. Note the differing
syntax: the iOS xcconfig uses spaces around the `=`, the Android properties file does
not. Rebuild after editing either file, since the key is baked in at build time.

If you added a key but still see the setup screen, check that you edited the existing
line rather than adding a second one. `bootstrap.sh` writes an empty value on Android
and the placeholder `your_openweather_api_key_here` on iOS, and both count as
unconfigured.

> The key is embedded in the compiled app and can be extracted from a shared `.apk`
> or `.app`. Use a free key you are willing to rotate, not one tied to a paid plan.

### Run iOS

```bash
cd ios
open SkyCast.xcodeproj
```

Pick any iPhone simulator in the toolbar and press Run. Nothing else is needed: the
scheme is shared, signing is disabled for the simulator, and there are no third party
packages to resolve.

To build from the command line instead:

```bash
cd ios
xcodebuild -project SkyCast.xcodeproj -scheme SkyCast \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

#### Running on a physical iPhone over USB

The phone must be on iOS 26 or newer.

1. Connect the iPhone by cable and unlock it. Tap **Trust** on the "Trust This
   Computer?" prompt, then enter the passcode.
2. On the phone, enable **Settings ▸ Privacy & Security ▸ Developer Mode**, then
   restart when asked. This appears only once Xcode has seen the device.
3. In Xcode, pick the iPhone from the device menu in the toolbar.
4. Open the **SkyCast** target ▸ **Signing & Capabilities** and choose your own team
   under **Team**. Signing is Automatic with no team committed, so Xcode provisions the
   app for you. A free Apple ID works.
5. Press Run.

Bundle identifiers are globally unique, so if `com.nauhaan.skycast` is rejected as
taken, override it in `ios/Config/Secrets.xcconfig`:

```
SKYCAST_BUNDLE_ID = com.yourname.skycast
```

On the first launch the phone shows "Untrusted Developer". Approve the certificate at
**Settings ▸ General ▸ VPN & Device Management**, then open the app again. An app
signed with a free Apple ID stops working after seven days and needs rebuilding.

### Run Android

```bash
cd android
./gradlew installDebug     # builds and installs on a connected device or emulator
```

Or open the `android/` folder in Android Studio and press Run.

Any emulator running API 26 or newer works. The helper scripts in `scripts/` assume
one named `SkyCast_API36`, which you can create in Android Studio's Device Manager and
then start with:

```bash
$ANDROID_HOME/emulator/emulator -avd SkyCast_API36 &
```

### Building the Android APK

To produce a single installable APK to share or sideload:

```bash
cd android
./gradlew assembleRelease
```

The APK is written to:

```
android/app/build/outputs/apk/release/app-release.apk
```

It is a full release build with R8 minification and resource shrinking enabled, around
1.8 MB, and it supports arm64-v8a, armeabi-v7a, x86 and x86_64. Install it on a
connected device with:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

To install it by hand, transfer the `.apk` to the phone and open it. Android will ask
you to allow installation from unknown sources for whichever app you opened it with.

To refresh the copy committed at `dist/SkyCast.apk`:

```bash
cp app/build/outputs/apk/release/app-release.apk ../dist/SkyCast.apk
```

> This release build is signed with the local **debug** keystore so that
> `assembleRelease` works on any machine without a keystore. That is fine for
> sideloading and testing, but the Play Store will reject it. Replace the
> `signingConfig` in `android/app/build.gradle.kts` with a real release keystore
> before any store distribution.

For a faster, unminified build instead, `./gradlew assembleDebug` writes
`app-debug.apk` to `app/build/outputs/apk/debug/`. It installs alongside the release
build rather than replacing it, since the debug build uses the `.debug` application ID
suffix.

## Testing and error handling

```bash
./scripts/test.sh          # unit tests, both platforms, no device needed
./scripts/test.sh --all    # adds the UI tests (needs an emulator and a simulator)
./scripts/lint.sh          # ktlint, detekt, Android Lint, SwiftFormat, SwiftLint
```

Android has 60 unit tests (JUnit, MockK, Turbine) plus Espresso instrumented tests. iOS
uses Swift Testing and XCUITest. Both platforms decode the same captured OpenWeather
responses, held byte for byte in `SkyCastTests/Fixtures/` and
`android/core/data/src/test/resources/fixtures/`, so a decoding regression fails on both.

Every network call resolves to an explicit state rather than letting an error reach the
UI:

| Condition | What the user sees |
| --- | --- |
| Offline, or cached data is stale | The last cached forecast, with a banner saying so |
| Request failed | An error state with a retry action |
| No API key configured | A setup screen naming the file to edit |
| No saved locations | An empty state with an "Add location" action |

## Features

- **Current weather:** Temperature, condition, "feels like", humidity, wind, pressure,
  visibility, sunrise, sunset, dew point and length of day for the selected location.
- **Five day forecast:** Daily high/low, with an hourly breakdown for each day and a
  scrollable hourly strip covering the next 24 hours.
- **METAR:** The nearest airport's aviation weather report, flight category, cloud
  layers, wind, and derived figures such as ceiling and density altitude, plus the raw
  coded report.
- **Moon phase:** Current phase, illumination, age, distance and moonrise/moonset for
  the selected location, plus the dates of the next four principal phases. Computed on
  the device, no network needed.
- **Aurora visibility:** Whether the aurora is likely to be visible from the selected
  location tonight, based on NOAA's geomagnetic (Kp) index and the location's geomagnetic
  latitude.
- **Multiple locations:** Add and remove saved locations by search, up to 10 at a time,
  and choose which one is the primary location shown on Home.
- **Unit settings:** Temperature (Celsius, Fahrenheit, Kelvin), wind speed (m/s, km/h,
  mph, knots, Beaufort scale), pressure (hPa/mbar, inHg, mmHg) and visibility (km,
  statute miles, nautical miles), all applied instantly.
- **Offline caching:** Weather data is cached locally and shown immediately on launch,
  with a banner shown when data is stale or the device is offline.
- **Light/Dark appearance:** Light, Dark or follow system, with Material You dynamic
  colour on Android.

## Screenshots

| Screen | iOS | Android |
| --- | --- | --- |
| Home | ![iOS Home](screenshots/ios/01-home.png) | ![Android Home](screenshots/android/01-home.png) |
| METAR | ![iOS METAR](screenshots/ios/02-metar.png) | ![Android METAR](screenshots/android/02-metar.png) |
| Locations | ![iOS Locations](screenshots/ios/03-locations.png) | ![Android Locations](screenshots/android/03-locations.png) |
| Add location | ![iOS Add](screenshots/ios/04-add-location.png) | ![Android Add](screenshots/android/04-add-location.png) |
| Settings | ![iOS Settings](screenshots/ios/05-settings.png) | ![Android Settings](screenshots/android/05-settings.png) |
| Location detail | ![iOS Detail](screenshots/ios/06-location-detail.png) | ![Android Detail](screenshots/android/06-location-detail.png) |
| Offline banner | ![iOS Offline](screenshots/ios/07-offline-banner.png) | ![Android Offline](screenshots/android/07-offline-banner.png) |
| Error state | ![iOS Error](screenshots/ios/08-error-state.png) | ![Android Error](screenshots/android/08-error-state.png) |
| Dark mode | ![iOS Dark](screenshots/ios/09-dark-mode.png) | ![Android Dark](screenshots/android/09-dark-mode.png) |
| Day detail | ![iOS Day detail](screenshots/ios/10-day-detail.png) | ![Android Day detail](screenshots/android/10-day-detail.png) |
| Moon | ![iOS Moon](screenshots/ios/11-moon.png) | ![Android Moon](screenshots/android/11-moon.png) |
| Aurora | ![iOS Aurora](screenshots/ios/12-aurora.png) | ![Android Aurora](screenshots/android/12-aurora.png) |

## Technologies used

**API**

- [OpenWeather API](https://openweathermap.org/api): current weather, five day forecast, geocoding

**iOS**

- Swift 6
- SwiftUI (Liquid Glass)
- SwiftData (local storage)
- URLSession (networking)
- Swift Testing, XCUITest
- XcodeGen, SwiftLint, SwiftFormat

**Android**

- Kotlin 2.2, coroutines
- Jetpack Compose, Material 3
- Room (local storage), DataStore (settings)
- Hilt (dependency injection)
- Retrofit, OkHttp, kotlinx.serialization (networking)
- JUnit, MockK, Turbine, Espresso
- ktlint, detekt

## Documentation

[LICENSING.md](LICENSING.md) covers the licence for this project and the terms of every
API and library it uses.

## Known issues and future improvements

### Known issues

- Only up to 10 saved locations are supported.
- The moon phase screen only shows today's phase and the dates of the next four
  principal phases. It does not let the user browse future days individually.
- No push notifications for severe weather.
- English only, no other languages are supported.

### Future improvements

- Home screen widgets showing current temperature.
- Background refresh so cached data stays up to date automatically.
- Severe weather notifications.
- The METAR and Moon screens do not have their own location picker. They always show
  data for whichever location is currently selected on Home. So, a location picker or search for these two pages.
- Date selection for moon phases
- Location permission support to show weather for the user's current location.
- Support for more than 10 saved locations.
