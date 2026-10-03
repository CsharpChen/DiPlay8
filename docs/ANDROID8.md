# Android 8.0 support

DiPlay was built against `minSdk 28` (Android 9). This document records what was changed to make
the same APK install and run on **Android 8.0 (API 26) and 8.1 (API 27)**, what is reduced there,
and how the change was verified.

## What the APK now targets

| Setting | Before | After |
| --- | --- | --- |
| `:shared` `minSdk` | 28 | 26 |
| `:common` `minSdk` | 28 | 26 |
| `:mobile` `minSdk` | 28 | 26 |
| `:shared` `APP_PLATFORM` (both `build.gradle` and `src/main/jni/Application.mk`) | `android-28` | `android-26` |

`:automotive` still requires 28 and the `:home` / `:maphost` samples still require 30, because they
depend on `SurfaceControlViewHost` / `SurfaceView.getHostToken`. Those modules are not part of the
Android 8 head-unit install.

## What works on Android 8.0/8.1

- **Wired CarPlay over USB.** The USB host path (`IphoneUsbHost`, `Iap2UsbMuxHost`, `NcmUsbBridge`,
  `NcmFunctionDiscovery`) only uses APIs available since API 26, including
  `UsbRequest.queue(ByteBuffer)` (added in API 26) and `UsbRequest`/`requestWait` (API 12).
- **Wireless over the car's own hotspot** (`ManualHotspotManager`), which is the mode the app already
  persists on Android 8/9.
- **Video and audio**: H.264/HEVC decoding into a `Surface`, PCM audio via `AudioTrack`, and audio
  focus via `AudioFocusRequest` (API 26).
- **Foreground service, notification channel, boot start**: `startForegroundService`,
  `NotificationChannel` and `startForeground(id, notification)` without a service type all reach
  back to API 26.

## What is reduced on Android 8.0/8.1

| Feature | Why |
| --- | --- |
| Wi-Fi Direct (`WifiP2pGroupManager`) | `WifiP2pConfig.Builder` and `createGroup(channel, config, listener)` are Android 10 (API 29) APIs. The manager already refused to start below API 29; the Wi-Fi Direct option is now also hidden on the setup screen below API 29 instead of silently collapsing into the car-hotspot mode. |
| LocalOnlyHotspot backend | `AirPlayPersistence` already migrates that mode to `MANUAL`, so it is not offered. The implementation itself now works down to API 26 (see below). |
| Live hotspot channel readout | `registerLocalOnlyHotspotSoftApCallback` is a SystemApi available from Android 13. Below that the code falls back to `WifiConfiguration.apChannel` (legacy) and the read-only WEXT ioctl reader. |
| LocalOnlyHotspot custom SSID/passphrase/band | `SoftApConfiguration` is an API 30 class and the configurable `startLocalOnlyHotspot` entry point is not public before then. On Android 13+/16 the code now reaches it purely reflectively, so no `SoftApConfiguration` class reference can be resolved on Android 8. |
| BYD HUD / cluster / standalone HUD output | Already gated on API 28 / BYD firmware and stays disabled. |
| Per-app language, launcher map embedding, dynamic colour | Android 13 / Android 11 features, unchanged. |
| HEVC and software-decoder preference | Unchanged, but `MediaCodecInfo.isSoftwareOnly` (API 29) is only read on API 29+. |

## Code changes for API 26

Every framework call that needed API 27+ now has an explicit guard or a fallback:

| File | Change |
| --- | --- |
| `network/MacAddressText.kt` (new) | Parses and formats colon-separated MAC addresses without `android.net.MacAddress`, which only exists from API 28. |
| `network/LocalOnlyHotspotManager.kt` | `WifiConfiguration.BSSID` is parsed with `MacAddressText` instead of `MacAddress.fromString`; the `SoftApConfiguration.Builder` path is fully reflective; the 5 GHz-only rule is relaxed on Android 8/9, where the platform cannot be asked for a band and Wi-Fi Direct is not available. |
| `network/ManualHotspotManager.kt` | MAC formatting delegates to `MacAddressText`. |
| `network/WifiP2pGroupManager.kt` | `WifiP2pManager.Channel.close()` (API 27) is guarded; `LocationManager.isLocationEnabled` (API 28) falls back to `isProviderEnabled(GPS_PROVIDER)`. |
| `media/AndroidMediaSink.kt` | `AudioTrack.getAudioAttributes()` (API 29) is only read on Android 10+; earlier releases keep the attributes the track was requested with. |
| `DiPlayActivity.kt` | `Channel.close()` guards in the Wi-Fi Direct reset path; the Wi-Fi Direct choice is hidden below API 29. |
| `MapEmbedService.kt` | `Embed` has always been created only on Android 11+; the service methods that touch it are annotated so lint can see that the remaining calls are unreachable below API 30. |
| `mobile/src/debug/.../StandaloneHudDemoActivity.kt` | The stock-receiver preflight uses API 28 signing APIs and now refuses earlier firmware explicitly. |

`InlinedApi` warnings remain for compile-time constants such as
`WifiConfiguration.KeyMgmt.WPA2_PSK`, `SoftApConfiguration.BAND_5GHZ` and
`UsageEvents.Event.DEVICE_SHUTDOWN`. Those are inlined by the compiler, so they add no runtime
class or field reference; they are warnings, not errors.

## Verification

Run from the repository root with JDK 25 and Android SDK 37:

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

- `:mobile:lintDebug` passes. No `NewApi` error remains in `:shared`, `:common` or `:mobile`; the
  remaining lint errors in `:shared`/`:common` (`MissingPermission`, `MissingTranslation`,
  `StringFormatMatches`, `RestrictedApi`, `ForegroundServicePermission`) are unchanged from the
  `minSdk 28` baseline and are not API-level issues.
- `:mobile:assembleDebug` produces an APK whose manifest reports `minSdkVersion: '26'`, with the
  `xcertplay_i2c` and `local_hotspot_radio` libraries built for `android-26`.
- `:shared:testDebugUnitTest` and `:common:testDebugUnitTest` pass, including the new
  `MacAddressTextTest`.

Nothing here was validated on real Android 8 hardware yet. The remaining risk on such a unit is
device-specific: the decoder/`MediaCodec` behaviour, the vendor audio policy, and whether the
firmware reports a usable `WifiConfiguration.apChannel` for a local-only hotspot.

## Building for the car

Set `DIPLAY_AUTH_ASSETS_DIR` as described in [BUILD.md](BUILD.md) to produce a standalone APK that
can actually authenticate to an iPhone; a plain `assembleDebug` build is identity-free.
