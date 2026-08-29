# Licensing and attribution

Addresses **MO4**, *reflect on trends, licensing, and best practices*.

Every dependency, its licence, and what that licence obliges us to do. Anything added to
`android/gradle/libs.versions.toml` must also be added here.

---

## 1. This project

SkyCast is released under the **MIT Licence** (see [`LICENSE`](LICENSE)).

MIT was chosen over the alternatives deliberately:

| Option | Why not |
| --- | --- |
| **MIT** ✅ | Permissive, three paragraphs, universally understood. Anyone may read, learn from, fork or reuse the code, appropriate for academic work whose purpose is to demonstrate technique. |
| GPL-3.0 | Copyleft would force derivative works to also be GPL. Imposing that on someone learning from a student project is a burden with no upside. |
| Apache-2.0 | Adds an explicit patent grant and a NOTICE requirement. Neither is meaningful for a project with no patents, and it is longer for no benefit here. |
| Unlicensed / "all rights reserved" | Legally the default, and the worst outcome: nobody can lawfully reuse the code, which defeats the point of publishing it. |

---

## 2. Data source: OpenWeather

| | |
| --- | --- |
| **Service** | [OpenWeather](https://openweathermap.org) |
| **Endpoints** | Current Weather, 5 Day / 3 Hour Forecast, Geocoding |
| **Plan** | Free tier |
| **Terms** | https://openweathermap.org/terms |
| **Data licence** | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| **Code/API licence** | Not applicable, the API is used as a service, no code is redistributed |

### Obligations, and where we satisfy them

CC BY-SA 4.0 requires **attribution** and **share-alike** for the *data*:

| Obligation | How SkyCast complies |
| --- | --- |
| Credit the source | In-app **Settings ▸ About & licences** screen, plus the README's *Licence and attribution* section |
| Link to the licence | Both places link to the CC BY-SA 4.0 deed |
| Indicate changes | The app displays and unit-converts the data; it does not alter the readings |
| Share-alike | SkyCast does not redistribute OpenWeather data to third parties, so this is not triggered. **If a future feature exported or published the data, it would be.** |

Do not remove either attribution. This is a licence obligation, not a courtesy.

### Practical terms worth knowing

- API keys are **personal and must not be committed**. Both platforms read the key from a
  gitignored file; the pre-commit hook and a SwiftLint custom rule both refuse a hardcoded
  32-hex-character key.
- The free tier permits non-commercial and commercial use within the rate limits.
- Caching for display is permitted. SkyCast caches with short TTLs (10 min / 3 h), enough
  for genuine offline use, not long enough to constitute republishing a dataset.

---

## 3. iOS dependencies

**None.** Every framework shipped in the iOS app is first-party Apple:

| Framework | Purpose | Licence |
| --- | --- | --- |
| SwiftUI | UI | Apple SDK, Xcode/iOS SDK licence agreement |
| SwiftData | Persistence | Apple SDK |
| Foundation | Core types | Apple SDK |
| Observation | View-model observation | Apple SDK |
| Network (`NWPathMonitor`) | Connectivity | Apple SDK |
| Swift Charts | Temperature trend chart | Apple SDK |
| UIKit | Interop for a few SwiftUI details | Apple SDK |
| Swift Testing | Unit tests (test target only) | Apache-2.0 with LLVM exception |
| XCTest / XCUITest | UI tests (test target only) | Apple SDK |

Apple SDK frameworks are governed by the Xcode and Apple SDKs Agreement. They are dynamically
linked from the OS, not redistributed, so there is no bundled licence text to ship.

Having zero third-party runtime dependencies on iOS was a deliberate choice. It removes an
entire class of supply-chain and licence-compliance risk.

### iOS build tooling (not shipped)

| Tool | Licence |
| --- | --- |
| [XcodeGen](https://github.com/yonaskolb/XcodeGen) | MIT |
| [SwiftLint](https://github.com/realm/SwiftLint) | MIT |
| [SwiftFormat](https://github.com/nicklockwood/SwiftFormat) | MIT |
| [xcbeautify](https://github.com/cpisciotta/xcbeautify) | MIT |

Build-time tools are not distributed with the app, so their licences impose no obligation on
the binary.

---

## 4. Android dependencies

All versions are pinned in `android/gradle/libs.versions.toml`.

### Shipped in the APK

| Dependency | Version | Licence |
| --- | --- | --- |
| Kotlin stdlib | 2.2.21 | Apache-2.0 |
| kotlinx.coroutines | 1.10.2 | Apache-2.0 |
| kotlinx.serialization | 1.9.0 | Apache-2.0 |
| AndroidX Core KTX | 1.17.0 | Apache-2.0 |
| AndroidX Core Splashscreen | 1.0.1 | Apache-2.0 |
| AndroidX Activity Compose | 1.12.4 | Apache-2.0 |
| AndroidX Lifecycle | 2.9.4 | Apache-2.0 |
| Jetpack Compose (BOM) | 2026.01.01 | Apache-2.0 |
| Compose Material 3 | 1.5.0-alpha14 (pinned, overrides the BOM) | Apache-2.0 |
| Navigation Compose | 2.9.8 | Apache-2.0 |
| Room | 2.8.4 | Apache-2.0 |
| DataStore Preferences | 1.1.7 | Apache-2.0 |
| Hilt / Dagger | 2.58 | Apache-2.0 |
| AndroidX Hilt Navigation Compose | 1.2.0 | Apache-2.0 |
| Retrofit | 2.12.0 | Apache-2.0 |
| retrofit2-kotlinx-serialization-converter | 1.0.0 | Apache-2.0 |
| OkHttp | 4.12.0 | Apache-2.0 |
| Coil 3 | 3.3.0 | Apache-2.0 |
| desugar_jdk_libs | 2.1.5 | GPL-2.0 **with Classpath Exception** |

### Test-only

| Dependency | Version | Licence |
| --- | --- | --- |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| MockK | 1.14.7 | Apache-2.0 |
| Turbine | 1.2.1 | Apache-2.0 |
| kotlinx-coroutines-test | 1.10.2 | Apache-2.0 |
| AndroidX Test / Espresso | 1.2.1 / 3.6.1 | Apache-2.0 |
| Compose UI Test | via BOM | Apache-2.0 |

### Build tooling (not shipped)

| Tool | Version | Licence |
| --- | --- | --- |
| Android Gradle Plugin | 8.13.2 | Apache-2.0 |
| Gradle | 8.14.5 | Apache-2.0 |
| KSP | 2.2.21-2.0.5 | Apache-2.0 |
| detekt | 1.23.8 | Apache-2.0 |
| ktlint / ktlint-gradle | 1.5.0 / 12.3.0 | MIT / Apache-2.0 |

---

## 5. What these licences actually require

### Apache-2.0: the overwhelming majority

| Requirement | Applies to SkyCast? |
| --- | --- |
| Include the licence text | Yes, if distributing binaries. Android satisfies this via the About screen; a Play Store release should use `play-services-oss-licenses` or an equivalent generated list. |
| Include NOTICE files where present | Yes, same mechanism |
| State significant changes | No, every dependency is used unmodified |
| Patent grant | Received, not owed |
| Trademark use | Not granted; we do not use dependency trademarks in branding |

Apache-2.0 is **not** copyleft: it imposes no licence requirement on our own source, which is
why MIT for SkyCast is compatible.

### GPL-2.0 with Classpath Exception: `desugar_jdk_libs`

The scary-looking one, and the reason it is fine. Plain GPL-2.0 would require SkyCast itself
to be GPL. The **Classpath Exception** explicitly permits linking without that consequence,
it is the same exception OpenJDK uses, which is why every Java application in existence is
not GPL. `desugar_jdk_libs` backports `java.time` so `minSdk 26` can use it.

No obligation beyond not modifying and redistributing the library itself.

### Eclipse Public License 1.0: JUnit 4

Weak copyleft, and only relevant to distributed binaries. JUnit is a **test-only**
dependency: it never enters the APK, so no obligation is triggered.

### MIT: tooling

Attribution only, and only when redistributing the tool. We do not redistribute any of them.

---

## 6. Licence compliance checklist

Before any distribution (Play Store, TestFlight, or handing over an APK):

- [ ] OpenWeather attribution present in-app **and** in the README
- [ ] Android: generate an OSS licences screen from the real dependency graph rather than
      relying on this hand-maintained table
- [ ] iOS: confirm no third-party runtime dependency has crept in
- [ ] No API key in the repository (`git log -p | grep -iE '[0-9a-f]{32}'`)
- [ ] This document matches `libs.versions.toml`
- [ ] Every new dependency's licence checked **before** adding it, not after

---

## 7. Best practices this project follows

Also part of MO4, the licensing and best-practice reflection.

| Practice | How it is applied |
| --- | --- |
| **Secrets never in version control** | Gitignored config files, committed `.example` templates, a pre-commit hook that refuses secret files, and a SwiftLint rule matching key-shaped literals |
| **Pinned, auditable versions** | One version catalog on Android, one `project.yml` plus `.xcconfig` set on iOS. No dynamic or `+` versions anywhere. |
| **Minimal dependency surface** | Zero third-party runtime dependencies on iOS; on Android every dependency is first-party AndroidX or an industry-standard library. Fewer dependencies means less to audit and less to break. |
| **Least privilege** | Only `INTERNET` and `ACCESS_NETWORK_STATE`. Location permission is deliberately **not** requested, places are chosen by name. |
| **No secrets in logs** | OkHttp logging is disabled in release and redacts `appid` in debug, because OpenWeather passes the key as a query parameter that would otherwise appear in every logged URL. |
| **Reproducible builds** | Committed Gradle wrapper, pinned JDK toolchain, a pinned Kotlin/AGP version catalog and a pinned Swift toolchain |
| **Licence review before adoption** | Section 6 checklist; every dependency in this document was checked when added |
| **Reviewable schema changes** | Room schemas exported to `app/schemas/` and committed, so a migration-less schema change is caught in review rather than by a crash on a user's device |

## Data sources

| Source | Terms | Notes |
| --- | --- | --- |
| [OpenWeather](https://openweathermap.org/api) | Free tier, [CC BY-SA 4.0](https://openweathermap.org/faq) for the data | Requires an API key, which is never committed. Attribution appears on the About screen. |
| [NOAA Aviation Weather Center](https://aviationweather.gov/) | US federal government work, **public domain** | No API key. Their usage policy asks callers to identify themselves, so both platforms send a `User-Agent`. |
| [NOAA Space Weather Prediction Center](https://www.swpc.noaa.gov/) | US federal government work, **public domain** | No API key. The planetary K index forecast, for the aurora card. Shares the aviation service's keyless client and `User-Agent`. |
| Lunar astronomy, **no source** | Not applicable; nothing is fetched | The Moon tab is computed on the device from published equations, so there is no service, no key and no licence to honour. The algorithms are the standard ones from Meeus, *Astronomical Algorithms* (2nd ed., Willmann-Bell, 1998), a textbook, cited in the source as scholarly practice rather than as a licence obligation, since mathematical methods are not copyrightable. |
