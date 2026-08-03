# SkyCast

> A weather app built **twice, fully natively**, SwiftUI for iOS, Jetpack Compose for
> Android, from a single shared architectural specification.

[![Android](https://github.com/ihaveacrowuwu/mobile-dev/actions/workflows/android.yml/badge.svg)](https://github.com/ihaveacrowuwu/mobile-dev/actions/workflows/android.yml)
[![iOS](https://github.com/ihaveacrowuwu/mobile-dev/actions/workflows/ios.yml/badge.svg)](https://github.com/ihaveacrowuwu/mobile-dev/actions/workflows/ios.yml)

| | iOS | Android |
| --- | --- | --- |
| **Language** | Swift 6 | Kotlin 2.2 |
| **UI** | SwiftUI + **Liquid Glass** | Jetpack Compose + **Material 3 Expressive** |
| **Minimum OS** | iOS 26.0 | Android 8.0 (API 26) |
| **Persistence** | SwiftData + `UserDefaults` | Room + DataStore |
| **Networking** | `URLSession` + `async`/`await` | Retrofit + OkHttp + coroutines |
| **DI** | `AppContainer` (hand-rolled) | Hilt |

---

## Contents

- [About](#about)
- [Why native instead of React Native](#why-native-instead-of-react-native)
- [Features](#features)
- [Screenshots](#screenshots)
- [Installation and running](#installation-and-running)
- [Technologies used](#technologies-used)
- [Architecture](#architecture)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Known issues and future improvements](#known-issues-and-future-improvements)
- [Documentation](#documentation)
- [Licence and attribution](#licence-and-attribution)

---

## About

SkyCast shows current conditions and a five-day forecast for places the user chooses, and
it keeps working when the network does not. Weather is fetched from the
[OpenWeather API](https://openweathermap.org/api), cached locally, and served from that
cache on the next launch, so the app opens with real data instantly and remains usable in
aeroplane mode.

This is the submission for **UFCF7H-15-3 Mobile Applications**, Practical Skills
Assessment (75% of the module).

**Status:** both platforms build, lint clean and pass their full test suites, Android 22
unit + 5 instrumented, iOS 30 unit + 6 UI. The architecture, data layer, navigation,
persistence, theming and CI are complete and verified. Some feature screens are still
placeholders,
see [Known issues and future improvements](#known-issues-and-future-improvements) for
exactly what is and is not built.

---

## Why native instead of React Native

The assessment brief specifies React Native. This project deliberately does something
harder: it implements the same application **twice, natively**, once per platform.

The reasoning, in short:

- **MO1** asks students to *analyse and evaluate mobile platform technologies*. Building
  the same app on both platforms produces a genuine, evidence-based comparison rather than
  a description of an abstraction layer that hides both.
- **MO2** asks about *user expectations*. Native lets each platform follow its own
  conventions, Material 3 with dynamic colour on Android, Human Interface Guidelines on
  iOS, instead of one compromise UI on both.
- Every requirement in the brief is still met; only the technology differs.

Every React Native concept in the brief has a direct native equivalent, and each one is
implemented:

| Brief requires | iOS | Android |
| --- | --- | --- |
| Multi-screen navigation (stack/tabs) | `TabView` + per-tab `NavigationStack` | Navigation Compose + `NavigationBar` |
| State management (Context/Redux/Zustand) | `@Observable` view models | `ViewModel` + `StateFlow` |
| Persistence (AsyncStorage/SQLite) | SwiftData + `UserDefaults` | Room + DataStore |
| Loading / network error handling | `DataState` + `AppError` | `DataState` + `AppError` |
| Public API | OpenWeather | OpenWeather |

---

## Features

| Feature | What it does | Status |
| --- | --- | --- |
| **Today** | Current conditions for the primary location: temperature, condition, "feels like". Pull to refresh. | ✅ Built |
| **Offline-first caching** | Reads the local cache first, then refreshes in the background. Cached data is **never** discarded because a request failed. | ✅ Built |
| **Stale-data banner** | When offline or after a failed refresh, a dismissible banner appears **over** the cached content instead of an error screen replacing it. | ✅ Built |
| **Settings, units** | Celsius/Fahrenheit and m/s · km/h · mph. Applies instantly and works offline, because values are cached in Celsius and converted at render time. | ✅ Built |
| **Settings, appearance** | Light / Dark / Follow system. Android additionally supports Material You dynamic colour. | ✅ Built |
| **Settings, clear cache** | Removes cached forecasts while **keeping** saved locations. Confirmed before acting. | ✅ Built |
| **Tab navigation** | Four tabs, each with an independent navigation stack, so per-tab state survives switching away and back. | ✅ Built |
| **Push navigation** | Detail screens push on top of the current tab, with correct system-back behaviour. | ✅ Built |
| **About & licences** | In-app attribution for OpenWeather and dependency licences (MO4). | ✅ Built |
| **Accessibility** | VoiceOver/TalkBack labels, combined announcements for grouped readings, Dynamic Type / font scaling, 44–48 pt touch targets. | ✅ Built |
| **Graceful missing-key state** | With no API key the app still builds and runs, showing setup instructions rather than crashing or failing to compile. | ✅ Built |
| **Forecast list** | Five-day list, tappable through to a 3-hourly breakdown. Repository and cache already implemented. | 🚧 Placeholder |
| **Locations management** | Add by search, reorder, swipe to delete, set primary. Repository, geocoding and cascade deletes already implemented. | 🚧 Placeholder |
| **Location detail** | Full conditions: humidity, wind, pressure, visibility, sunrise/sunset. | 🚧 Placeholder |

🚧 = the destination is reachable, navigable and back-navigable; only its content is
outstanding. The data layer beneath each one is complete and unit-tested.

---

## Screenshots

> **Screenshots are pending.** Capture them with
> [`scripts/screenshots.sh`](scripts/screenshots.sh), which enforces the exact file names
> these links expect:
>
> ```bash
> ./scripts/screenshots.sh android 01-today
> ./scripts/screenshots.sh ios 01-today
> ```
>
> See [`docs/screenshots/README.md`](docs/screenshots/README.md) for the full checklist.
> The brief docks up to **15 marks** if the README has no screenshots, so this section must
> be filled in before submission.

| Screen | iOS | Android |
| --- | --- | --- |
| **Today** | ![iOS Today](docs/screenshots/ios/01-today.png) | ![Android Today](docs/screenshots/android/01-today.png) |
| **Forecast** | ![iOS Forecast](docs/screenshots/ios/02-forecast.png) | ![Android Forecast](docs/screenshots/android/02-forecast.png) |
| **Locations** | ![iOS Locations](docs/screenshots/ios/03-locations.png) | ![Android Locations](docs/screenshots/android/03-locations.png) |
| **Add location** | ![iOS Add](docs/screenshots/ios/04-add-location.png) | ![Android Add](docs/screenshots/android/04-add-location.png) |
| **Settings** | ![iOS Settings](docs/screenshots/ios/05-settings.png) | ![Android Settings](docs/screenshots/android/05-settings.png) |
| **Location detail** | ![iOS Detail](docs/screenshots/ios/06-location-detail.png) | ![Android Detail](docs/screenshots/android/06-location-detail.png) |
| **Offline banner** | ![iOS Offline](docs/screenshots/ios/07-offline-banner.png) | ![Android Offline](docs/screenshots/android/07-offline-banner.png) |
| **Error state** | ![iOS Error](docs/screenshots/ios/08-error-state.png) | ![Android Error](docs/screenshots/android/08-error-state.png) |
| **Dark mode** | ![iOS Dark](docs/screenshots/ios/09-dark-mode.png) | ![Android Dark](docs/screenshots/android/09-dark-mode.png) |

---

## Installation and running

### Prerequisites

| Tool | Version | Needed for |
| --- | --- | --- |
| macOS | 14+ | Both (iOS requires macOS) |
| Xcode | 16+ | iOS |
| Android Studio | 2024.1+ | Android (optional, the SDK alone is enough) |
| JDK | **21** | Android, AGP rejects newer JDKs |
| Android SDK | Platform 36, Build-Tools 36 | Android |
| XcodeGen | 2.46+ | iOS project generation |

`./scripts/doctor.sh` checks every one of these and prints what is missing.

### 1. Clone and bootstrap

```bash
git clone https://github.com/ihaveacrowuwu/mobile-dev.git
cd mobile-dev
./scripts/bootstrap.sh     # creates config files, generates the Xcode project, installs git hooks
./scripts/doctor.sh        # reports anything still missing
```

### 2. Add an OpenWeather API key

Get a free key at [openweathermap.org/api_keys](https://home.openweathermap.org/api_keys).
A new key can take up to two hours to activate.

```bash
# Android: edit android/local.properties
OPEN_WEATHER_API_KEY=your_key_here

# iOS: edit ios/Config/Secrets.xcconfig
OPEN_WEATHER_API_KEY = your_key_here
```

Both files are gitignored. **The app builds and runs without a key**, it shows an
"API key not configured" screen instead of weather data, which is also why CI needs no
secret.

### 3. Run Android

```bash
cd android
./gradlew installDebug          # build and install on a connected device or emulator
```

Or open the `android/` directory in Android Studio and press Run.

To start the emulator this project sets up:

```bash
$ANDROID_HOME/emulator/emulator -avd SkyCast_API36 &
```

### 4. Run iOS

```bash
cd ios
xcodegen generate               # only needed after editing project.yml
open SkyCast.xcodeproj
```

Then choose an iPhone simulator and press Run. From the command line:

```bash
xcodebuild -scheme SkyCast -destination 'platform=iOS Simulator,name=iPhone 17' build | xcbeautify
```

### 5. Verify everything

```bash
./scripts/lint.sh               # every linter, both platforms
./scripts/test.sh               # unit tests, both platforms
./scripts/test.sh --all         # plus UI tests (needs a device/simulator)
```

---

## Technologies used

### Shared

| Technology | Purpose |
| --- | --- |
| [OpenWeather API](https://openweathermap.org/api) | Current weather, 5-day/3-hour forecast, geocoding |
| GitHub Actions | CI: build, lint, unit tests, R8 release verification |

### iOS

| Technology | Purpose |
| --- | --- |
| Swift 6 (strict concurrency) | Language; data-race safety enforced at compile time |
| SwiftUI + **Liquid Glass** (iOS 26) | Declarative UI; `glassEffect`, `GlassEffectContainer`, glass button styles |
| SwiftData | Local relational cache and saved locations |
| Observation (`@Observable`) | View-model state observed directly by SwiftUI |
| `URLSession` + `async`/`await` | Networking |
| Network framework (`NWPathMonitor`) | Connectivity detection |
| Swift Testing | Unit tests (`@Test` / `#expect`) |
| XCUITest | UI tests |
| [XcodeGen](https://github.com/yonaskolb/XcodeGen) | Generates `.xcodeproj` from `project.yml` |
| [SwiftLint](https://github.com/realm/SwiftLint) / [SwiftFormat](https://github.com/nicklockwood/SwiftFormat) | Static analysis and formatting |

**No third-party runtime dependencies on iOS**, everything shipped in the app is a
first-party Apple framework.

### Android

| Technology | Purpose |
| --- | --- |
| Kotlin 2.2 + coroutines / Flow | Language and concurrency |
| Jetpack Compose + Material 3 **Expressive** | Declarative UI, dynamic colour, expressive motion/shape/type |
| Navigation Compose | Type-safe `@Serializable` routes |
| Room | Local SQLite cache and saved locations |
| DataStore (Preferences) | Settings |
| Hilt (Dagger) | Dependency injection |
| Retrofit + OkHttp + kotlinx.serialization | Networking and JSON |
| Coil | Image loading |
| JUnit 4, MockK, Turbine | Unit tests |
| Compose UI Test + Espresso | Instrumented tests |
| [ktlint](https://github.com/pinterest/ktlint) / [detekt](https://detekt.dev) | Formatting and static analysis |

Every dependency and its licence is listed in [`docs/licensing.md`](docs/licensing.md) (MO4).

---

## Architecture

Layered MVVM with a repository boundary, implemented **identically on both platforms**.

```
   Presentation     SwiftUI View / Composable
                    @Observable VM / ViewModel
                    UiState (one type per screen)
                          │
                          ▼
   Domain           Models · Repository protocols · AppError
                    ── zero framework imports ──
                          ▲
                          │ implements
   Data             RepositoryImpl (cache + network orchestration)
                    Remote (DTOs) · Local (entities) · Mappers
```

**The rule that makes it work:** `domain` imports nothing platform-specific, no SwiftUI,
no SwiftData, no Room, no Retrofit. You could delete the entire `data` layer and `domain`
would still compile. That is what makes every view model unit-testable with a hand-written
fake and no device.

### Offline-first read algorithm

```
1. Emit the cache immediately          → content renders with no spinner
2. Fresh enough? stop                  → no wasted request, no wasted quota
3. Offline?  keep cache, note it       → banner, not an error screen
4. Fetch → write cache → emit          → fresh data
5. Fetch failed?  keep cache + error   → banner over content
   No cache AND failed?                → full-screen error with Retry
```

### Rubric mapping

| Criterion | Marks | Where it is evidenced |
| --- | --: | --- |
| UI/UX design & layout | 20 | Design tokens (`Spacing`, `Theme`), dark mode, Dynamic Type, accessibility labels, 44/48 pt touch targets |
| Navigation | 15 | Four tabs with independent stacks + push destinations; type-safe routes; `NavigationFlowTest` / `NavigationFlowUITests` |
| State management | 15 | One `UiState` per screen with derived display rules; unidirectional flow; `StateFlow` / `@Observable` |
| Persistence | 15 | Room / SwiftData caches + DataStore / `UserDefaults` settings; TTLs; cascade deletes; works fully offline |
| Functionality | 15 | Loading, empty, error, offline and success states all handled; retry paths; graceful missing-key state |
| Code quality & documentation | 10 | Layered folders mirrored across platforms; doc comments; ktlint + detekt + SwiftLint + Android Lint clean with warnings-as-errors |
| Testing & debugging | 5 | 22 Android unit + 5 instrumented; 30 iOS unit (Swift Testing) + 6 XCUITests. **All passing.** Error and offline paths covered explicitly |
| Presentation & reflection | 5 | This README, [`docs/reflection.md`](docs/reflection.md) |

---

## Testing

```bash
cd android && ./gradlew testDebugUnitTest          # 22 unit tests, no device
cd android && ./gradlew connectedDebugAndroidTest  # navigation + settings, needs a device
cd ios && xcodebuild test -scheme SkyCast -destination 'platform=iOS Simulator,name=iPhone 17' | xcbeautify
```

Or both platforms at once, no `sudo xcode-select` required, the scripts resolve Xcode via
`DEVELOPER_DIR` themselves:

```bash
./scripts/test.sh          # unit tests, both platforms
./scripts/test.sh --all    # plus UI tests
```

The suites deliberately concentrate on **error and offline paths**, not just happy paths,
that is what the "Testing & Debugging" criterion asks for, and it is what protects the
persistence behaviour from regressing.

---

## Project structure

```
mobile-dev/
├── android/                    Gradle project, open this directory in Android Studio
│   ├── gradle/libs.versions.toml   Single source of truth for all versions
│   └── app/src/main/java/com/nauhaan/skycast/
│       ├── core/               Common utilities, design system
│       ├── domain/             Models, repository interfaces, use cases
│       ├── data/               Remote, local, mappers, repository impls
│       ├── di/                 Hilt modules
│       └── ui/                 Navigation and one folder per feature
├── ios/                        XcodeGen project, open SkyCast.xcodeproj
│   ├── project.yml             Single source of truth for the Xcode project
│   ├── Config/                 .xcconfig build settings + secrets template
│   └── SkyCast/                Core · Domain · Data · Features (mirrors Android)
├── docs/
│   ├── licensing.md            Dependency licences (MO4)
│   ├── reflection.md           Graded reflection (MO4)
│   └── screenshots/            Graded assets
└── scripts/                    bootstrap · doctor · lint · test · screenshots
```

---

## Known issues and future improvements

### Known issues

1. **Three screens are placeholders.** Forecast, Locations and the detail screens are
   reachable and navigable but show a description of their planned content. The
   repositories, caches, mappers and geocoding beneath them are fully implemented and
   unit-tested, so these are presentation-only gaps.
2. **`Tab` accessibility identifiers do not reach the tab bar.** SwiftUI generates the
   tab-bar button from a `Tab`'s label, and `.accessibilityIdentifier` on either the `Tab`
   or its content is not propagated to that button (verified by dumping the accessibility
   tree). `SkyCastUITests` therefore queries by label scoped to `app.tabBars`, which is
   unambiguous. Android needs the opposite, `testTag`, because Compose offers no
   equivalent scoping.
3. **iOS requires iOS 26.** Liquid Glass is an iOS 26 API surface, so the deployment
   target is 26.0 and devices below that are excluded. This is a deliberate trade-off for
   the design language; lowering the target means giving up Liquid Glass.
4. **Compose Material3 is an alpha (`1.5.0-alpha14`).** Material 3 Expressive is
   Kotlin-`internal` in 1.4.0, the newest stable release, so there is no stable route to
   it. Pinned to an exact alpha for reproducibility. Also why the Compose BOM sits at
   2026.01.01 rather than the newest, see the recorded decision.
5. **AGP is pinned to 8.x.** Moving to AGP 9 would force detekt, the ktlint Gradle plugin
   and Gradle itself onto versions whose mutual compatibility is unverified here. Hilt is
   therefore pinned to 2.58, the newest release that still works with AGP 8. Recorded in
   `android/gradle/libs.versions.toml`.
6. **No release signing configuration.** `assembleRelease` uses the debug signing config so
   it is runnable locally and in CI. A real keystore would be required for Play Store
   distribution.
7. **English only.** Strings are externalised, but no translations exist and
   `androidResources.localeFilters` ships only `en`.

### Future improvements

- Finish the three placeholder screens.
- **Widgets**, WidgetKit and Glance widgets showing the primary location's current
  temperature. Both platforms' data layers already support this.
- **Background refresh**, `BGAppRefreshTask` on iOS, `WorkManager` on Android, so the
  cache is warm before the user opens the app.
- **Severe weather notifications** for saved locations.
- **Location permission (opt-in)** to offer "weather here" alongside named places.
- **Keychain / EncryptedSharedPreferences** if anything sensitive is ever stored.
- **Snapshot testing** to catch unintended visual regressions in the design system.
- **Localisation**, Dhivehi and Arabic, including RTL layout verification.

---

## Documentation

| Document | Contents |
| --- | --- |
| [`docs/licensing.md`](docs/licensing.md) | Every dependency and its licence (MO4) |
| [`docs/reflection.md`](docs/reflection.md) | Reflection on process, platforms and trends (MO4) |

---

## Licence and attribution

This project is released under the [MIT Licence](LICENSE).

Weather data is provided by [OpenWeather](https://openweathermap.org) and is subject to
their [terms of service](https://openweathermap.org/terms). OpenWeather data is licensed
under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); the attribution here
and the in-app About screen satisfy that requirement.

Third-party dependency licences are listed in [`docs/licensing.md`](docs/licensing.md).

---

*Ahmed Nauhaan Athif, UFCF7H-15-3 Mobile Applications, Villa College.*
