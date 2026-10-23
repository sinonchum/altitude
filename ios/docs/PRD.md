# Altitude for iOS — product requirements

Product name stays **Altitude**. The wordmark on the display and the watermark is the Latin word `ALTITUDE`. It is not translated. The red dot is this app’s own mark. The app does not imitate a camera brand, and it does not invent focal length or aperture.

This document describes the iOS app that matches the Android app at version **1.3.2** (versionCode 9) on branch `cursor/trilingual-ui-f585`, plus the import-and-frame behavior from Android branch `cursor/import-photo-frame-c835`. The iOS app lives in `ios/` and does not change the Android project. The first iOS release was **1.0.0 (1)**. Import framing first shipped as **1.1.0 (2)**. This build is **1.1.1 (3)**.

Feature parity is the goal. Platform differences are called out in the mapping column, not papered over with special-case tables.

## 1. Why this app exists

On a high road, from a city around 3,600 m up toward a base camp around 5,200 m, two pictures matter:

1. Someone else photographs you. You hold your phone up. The screen shows one large, stable altitude that is still readable in sun.
2. You photograph the place or yourself. Altitude, coordinates, and the place name are already in the saved photo, and the location is in the photo metadata. Nothing is pasted on afterwards.

The core reading works offline. Place names need a network and can be empty.

## 2. Goals and non-goals

Do:

- Full-screen, high-contrast altitude in portrait and landscape. The screen stays awake. Maximum brightness is optional. Meters and feet. Digits roll. Coordinates, accuracy, time, and place name are optional on the display.
- A camera whose viewfinder and saved photo share one watermark. The saved photo is redrawn at capture resolution. It is saved into an album named Altitude. Latitude, longitude, and mean-sea-level altitude are written into the photo metadata (EXIF GPS).
- Framing an existing photo from the library. Coordinates, capture time, and altitude come from that photo. The original file is not modified. The framed result is a new image in the Altitude album.
- Mean sea level, not ellipsoid height. Use the platform’s sea-level altitude when Core Location provides one. Otherwise subtract the bundled EGM96 15′ geoid. When a barometer is present, it tracks short-term motion and GPS slowly calibrates it.
- Location, camera, photo library, and motion permission flows. After a denial, the screen explains the gap and offers another request or a jump to Settings.
- In-app gallery of the photos this app saved.
- Settings with an in-app language switch (Simplified Chinese, English, French, or the system language) and a version line.
- Front camera, including a user toggle for screen flash (a white fill light). Tap to focus and meter from the lens’s real capabilities. Pinch zoom, presets, and a slider.
- Landscape shooting: the live viewfinder fills the stage, controls sit on a vertical rail, and the watermark lays out for a landscape frame.

Do not:

- Accounts, cloud sync, tracks, maps, or a social feed.
- Fake lens data.
- A bundled worldwide place-name table. Names come from Nominatim, with the Apple geocoder only as a fallback.
- Label an uncalibrated barometer reading as sea-level altitude on the watermark.

## 3. Scenarios

| Scene | What the person is doing | What success looks like |
| --- | --- | --- |
| Pass photo | One hand holds the phone up; another camera shoots it | A large number in the middle. Controls fade after a few seconds. The screen stays on. |
| Camp selfie or landscape | Open the camera, check the footer, press the shutter | The Altitude album has a photo with a white or black footer. Metadata has location and altitude. |
| No signal | Airplane mode, or no place-name network | Satellites and the barometer still produce a number. The place line can be empty. |
| Indoors, no fix yet | Waiting | The screen says it is looking for satellites and, if one exists, shows the last reading. It never treats 0 as the altitude. |

## 4. Experience

- Dark by default. The five display themes are fixed high-contrast palettes. They do not follow the system dynamic color. The “follow system colors” switch colors Settings controls only.
- Numbers use Outfit ExtraBold with tabular figures. When the value changes, each digit rolls. It does not flash the whole string.
- On the display, controls fade after about 4.6 seconds. A tap brings them back. A double tap on the number switches meters and feet.
- The shutter gives a short haptic tap.
- The viewfinder is the future print: portrait 3:4, landscape 4:3. The watermark sits in that frame. The shutter sits outside it.
- Layout uses the safe area (notch, Dynamic Island, home indicator, rounded corners). Controls are inset by the safe area. There is no per-device coordinate table.
- Backgrounds run edge to edge. Text and buttons stay inside the safe area.

### 4.1 Visual language, matched to Android

| Token | Value |
| --- | --- |
| Night background / primary / secondary / accent | `#090A0C` / `#F3F0E8` / `#9A958C` / `#E10600` |
| Void | `#000000` / `#FFFFFF` / `#C8C8C8` / `#E10600` |
| Glacier | `#071018` / `#EAF6FF` / `#8AA4B5` / `#7ED0FF` |
| Saffron | `#100E0A` / `#F6C453` / `#CDB892` / `#E10600` |
| Snow | `#F4F2EC` / `#141210` / `#5C574E` / `#E10600` |
| Quality dots | excellent `#7DDEA0`, good `#C5D67A`, fair `#E6B15A`, poor `#E07A5F`, searching `#9A958C` |
| Type | Outfit for Latin; the system face for Han |
| Wordmark | `ALTITUDE`, medium weight, wide tracking, red dot after it |

## 5. Information architecture

```
First launch
  └─ Three steps: display → location permission → camera permission (skippable)
Home
  ├─ Display
  └─ Camera
Settings (from either mode)
  Language, units, theme, brightness, display extras, watermark note, version
Gallery (from the camera thumbnail)
```

Display and Camera are one switch. There is no deeper navigation stack.

## 6. Features and the iOS mapping

### 6.1 Display

Android source: `DisplayScreen`, `AltitudeViewModel`, `Format`, `LocalFormat`.

- The hero number is the rounded meters or feet. Under it: the unit and “sea level”, or the uncalibrated-barometer caption.
- Accuracy line: `± 4 m   Satellite + barometer`, with a quality dot (excellent / good / fair / poor).
- Coordinates default on. Time, place, and accuracy can be turned off. Those lines stay in the layout when shown, so a photo of the screen does not jump. Fading hides buttons, not the reading.
- No location permission, approximate location only, location services off: a title, a sentence, and a button. The app does not crash.
- No altitude yet: “Looking for satellites”, plus a hint to give the antenna some sky. If a previous sea-level reading was stored, show “Last …”. It is not presented as live.
- Before the barometer is tied to a satellite fix, the number may show, with the caption “not yet tied to satellites”. The watermark altitude is “— —”, and EXIF altitude is omitted.

iOS:

- `CLLocation.altitude` is the platform sea-level altitude when `verticalAccuracy >= 0`. `ellipsoidalAltitude` is the ellipsoid height used only when the platform value is missing.
- `CMAltimeter` pressure (kPa × 10 = hPa) feeds the same fusion filter as Android’s barometer.
- `UIApplication.isIdleTimerDisabled` keeps the screen on while the app is active.
- Maximum brightness sets `UIScreen.brightness` to 1 and restores the previous value when turned off.
- Safe area insets pad the top controls and the mode switch. The background ignores the safe area.

### 6.2 Camera, zoom, focus, front camera, landscape

Android source: `CameraScreen`, `CameraStage`, `CameraGestures`, `CameraZoom`, `CameraCapture`, `CameraStageMetrics`.

| Behavior | Android | iOS |
| --- | --- | --- |
| Preview | CameraX `PreviewView`, aspect fill inside the 3:4 or 4:3 frame | `AVCaptureVideoPreviewLayer` in that same frame |
| Still | `ImageCapture` JPEG, rotated upright, watermark redrawn, JPEG quality 95 | `AVCapturePhotoOutput`, upright via `videoRotationAngle`, same redraw |
| Back lens | The widest virtual camera CameraX can open | Discovery order: triple, dual-wide, dual, wide |
| Front lens | Front camera | `builtInWideAngleCamera`, front |
| Lens button | Shown only when both cameras exist | `CameraLenses.buttonVisible` |
| Zoom | Pinch, presets 0.5 / 1 / 2 / 5 that the range contains, slider | `videoZoomFactor` between `minAvailableVideoZoomFactor` and `maxAvailableVideoZoomFactor`. Same preset list and the same clamp math. |
| Tap | Focus + meter if the lens can autofocus; exposure only if it cannot | Same decision, from `Autofocus` / `FocusMeteringChoice`. Capabilities come from `isFocusModeSupported`, `minimumFocusDistance`, `isFocusPointOfInterestSupported`, `isExposurePointOfInterestSupported`. Front camera uses the same function. No front-camera special case. |
| Long press | Lock focus | `focusMode = .locked` after the point is set, when the device supports it |
| Exposure slider | Shown when compensation is supported | `exposureTargetBias` across `minExposureTargetBias...maxExposureTargetBias` |
| Screen flash | User toggle, front camera only, white fill while the shutter runs | Same toggle, drawn as a circle tool beside Settings (portrait) or in the utility rail (landscape). On capture: white overlay, brightness 1, then restore. |
| Landscape | Viewfinder on the long axis. Shutter rail on the phone’s physical bottom. Utility rail on the other side. Preview height at least 70% of the stage. | Width > height selects the landscape stage. `shutterOnEnd` follows interface orientation so the shutter stays on the physical bottom. Rails are padded by the safe area. |
| Watermark orientation | The plate uses the shorter photo edge, so landscape margins match portrait | Same `WatermarkMetrics` |
| Haptic | Short vibration | `UIImpactFeedbackGenerator` |
| Selfie mirroring | The saved bitmap is not mirrored, so the watermark stays readable | Preview is mirrored for the front camera. The saved photo is not. |

Saved photo name: `Altitude_yyyyMMdd_HHmmss.jpg`.

### 6.3 Watermark

Android source: `WatermarkRenderer`, `WatermarkMetrics`, `PhotoPipeline`.

Four styles, shared by the viewfinder and the still:

| Style | Print | Use |
| --- | --- | --- |
| White | A white bar under the photo | Default |
| Black | The same bar, black with light type | Night, dark clothes |
| Frame | White margin around the photo, type in the bottom margin | A mounted print |
| Overlay | Frame unchanged. Type sits on a gradient at the bottom or the top. | When the aspect must stay |

Footer content, two or three lines:

```
ALTITUDE ●                         5,210 m
29.65218° N   91.11700° E          2026.10.22  16:42
Shigatse
```

The red dot is ours. Plate height is 16.2% of the shorter photo edge, or 21% when a place name is present. Frame side and top margins are 4.6% of that edge. The frame bottom is 20%.

Overlay anchor is Bottom or Top, chosen on the camera chrome, stored with the other preferences.

iOS drawing uses Core Graphics and the same fractions. Outfit is used for Latin. The system face is used when the line contains Han. The still is compressed to JPEG and the metadata is written with ImageIO.

EXIF / GPS:

- Latitude, longitude, and altitude in meters above sea level, when each value exists.
- `DateTime` and `DateTimeOriginal`.
- `Software = Altitude`.
- Orientation normal, because the pixels are already upright.
- No sea-level altitude: watermark shows “— —”, and the altitude tag is omitted.
- No coordinates: the GPS dictionary is omitted. The coordinate line on the watermark says the fix is still waiting.

Album:

- Android writes `Pictures/Altitude` through MediaStore.
- iOS creates a Photos album named **Altitude** and saves the JPEG there. The user-facing sentence stays the Android wording (“Pictures/Altitude”), because that is the shared copy. The PRD records the real destination.

### 6.4 Altitude math

Android source: `AltitudeFusion`, `PressureAltitude`, `Egm96Geoid`, `resolveMsl`. The Swift types are a direct port. They do not import UIKit.

Sea-level height H = ellipsoid height h − geoid undulation N.

- If the platform provides a sea-level altitude, use it and do not subtract N.
- Otherwise N comes from `egm96-15.bin` (little-endian centimeters, GeographicLib registration: row 0 is 90°N, longitude starts at 0° and steps east). Bilinear interpolation. Published bilinear RMS of this grid is about 4 cm. The maximum is about 1.2 m, smaller than a phone’s vertical GPS error. Near Lhasa, N is about −35 m, so the ellipsoid height sits below the sign on the road. It must be converted.
- Pressure altitude is the International Standard Atmosphere troposphere formula. It is not sea level by itself.
- Fusion: the first trustworthy GPS sea-level sample snaps the barometer offset. After that, short climbs belong to the barometer. GPS moves the offset with a time constant of about 12 seconds when accuracy is excellent. One outlier beyond max(25 m, 3σ) does not pull the reading. If the disagreement lasts 8 seconds, the offset reseats. After GPS drops out, the source becomes “barometer hold” and the error grows by 0.15 m each second starting at the fifth second.
- A sample with vertical accuracy worse than 40 m does not calibrate. Altitudes outside −500…12,000 m are dropped.
- Fixes older than 12 seconds (fusion) or 30 seconds (coordinates) are ignored. Age prefers elapsed time over the wall clock, with 2 seconds of allowed skew, matching `FixClock`.

iOS feeds:

- GPS: `CLLocationManager`, `kCLLocationAccuracyBest`, distance filter none, updates about once a second. Precise authorization is required for the satellite-quality path. Approximate authorization is surfaced as its own prompt.
- iOS has no separate “GPS provider” switch. `gpsEnabled` stays true while Location Services are on, so the Android “satellite positioning is off” prompt is not shown as a duplicate of the Location Services prompt. The string still exists for the same condition if a future reading needs it.
- Network-only versus fused versus satellite is an Android provider set (`activeLocationFeeds`). The pure function is ported and tested. On iOS the single Core Location stream is the feed.
- Barometer absent: GPS only. The source line does not claim a barometer.

### 6.5 Place names

Android source: `PlaceLabel`, `PlaceNames`.

Rules, with no city table:

1. Call Nominatim reverse geocode: `https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&addressdetails=1`, `accept-language` set to the app language (`en`, `fr`, or `zh`), one request at a time, at most one per second, 4 second timeout. User-Agent `Altitude/1.0.0 (https://github.com/sinonchum/altitude)`.
2. From the address object, take the first non-empty of city, town, village, municipality, hamlet, then county, district, region, state. Ignore suburb, neighbourhood, quarter, road, and building.
3. If `addresstype` is peak, island, water, or natural, use the object’s own name.
4. If nothing matched, use the text before the first comma of `display_name`.
5. In English or French, if the chosen string is Han and a later field is Latin, use that Latin string. This is how Nominatim’s own exonym wins: Shigatse, not a pinyin rendering and not the suburb.
6. Cache a successful Nominatim label on a 0.01° cell plus the language. A reached response with an empty name is not replaced by the Apple geocoder.
7. The Apple geocoder (`CLGeocoder`) runs only when Nominatim cannot be reached. Its label is the first of locality, subAdministrativeArea, administrativeArea, name.
8. In English or French, Han text is passed through the system transliterator (`Han`/`Any` to Latin, then strip diacritics), and spaced pinyin syllables are joined (`ri ka ze shi` → `Rikazeshi`). A failed transliteration keeps the original characters. A geocoder string that is only spaced pinyin syllables is joined the same way. A multi-word Nominatim name that is already Latin (`Hong Kong`, `Everest Base Camp`) is left as it arrived.

Standard exonyms are Nominatim’s, per `accept-language`. The app does not rename places itself.

### 6.6 Permissions

| Need | When | If denied |
| --- | --- | --- |
| Location when in use, precise | The Allow button on onboarding step 2, and the button on the display prompt. Not at launch, and not when Settings opens. | The explanation is already on screen. The button asks, or opens Settings once the system will not ask. |
| Camera | The Allow button on onboarding step 3 (skippable), and the button on the camera gate. Opening the camera shows the explanation first. | Display still works. A denial opens Settings. |
| Photo library add | The first save, as `PHAccessLevel.addOnly`. Not at launch, and not when the camera or Settings opens. | The save error string. Add-only access does not read the library. |
| Photo library read | The Allow button on the gallery explanation, or the Allow button on the import explanation. | The gallery shows that explanation instead of an empty album. Import stays on the explanation until access is granted. Limited access still exposes `PHAsset.location` for the assets the person picked. If a location had to be dropped, the preview says so in Chinese, English, and French. A denial opens Settings → Altitude → Photos. |
| Motion | Barometer, requested with the altimeter start | GPS-only reading. No crash. |

Onboarding can be finished with “Not now” and “Use the display only”. Completion is stored.

### 6.7 Gallery

Android source: `GalleryScreen`, `AlbumIndex`, `AlbumQuery`.

- Opens from the last-photo thumbnail.
- Before read access, shows a short explanation. The system dialog waits for that button.
- Pages through the Altitude album, newest first, once read access is granted.
- Share, delete (with confirm), open in the system Photos app, close.
- Empty state copy matches Android, and only appears when the album itself is empty.
- `AlbumQuery` is ported for the unit tests. iOS does not query MediaStore. It fetches `PHAsset`s in the Altitude `PHAssetCollection`.

### 6.8 Settings and language

- Language: System, 中文, English, Français. The choice applies immediately inside the app. It is stored in preferences. It does not depend on a process restart.
- Units: meters, feet.
- Theme: Night, Black, Glacier, Saffron, Snow.
- Toggles: maximum brightness, coordinates, accuracy, time, place name, follow system colors.
- A note that the watermark always carries altitude, coordinates, and time.
- Version line: `About / Version 1.1.1 (3)`, from `CFBundleShortVersionString` and `CFBundleVersion`. The first release was `1.0.0 (1)`. Import framing first shipped as `1.1.0 (2)`. Bump both on every later release.

Strings live in `Localizable.xcstrings`. Development language is English. Localizations: `en`, `zh-Hans`, `fr`. Copy is the Android `values`, `values-zh-rCN`, and `values-fr` catalogs. Format placeholders are converted from `%1$s` to `%1$@` only at the call site. The catalog keeps the Android specifiers so the parity test can compare them. Plural categories follow the Android catalogs (English one/other, Chinese other, French one/many/other).

System permission sentences live in `InfoPlist.xcstrings` for the same three languages, because iOS shows those from the property list rather than from the Android string table. They are short restatements of the onboarding body, not a fourth product language.

### 6.9 Preferences

Stored in `UserDefaults`, same keys in spirit as Android: unit, theme, brightness, coordinate/accuracy/clock/place toggles, dynamic color, watermark style, caption anchor, onboarding complete, lens, screen flash, and the last known reading.

Defaults: meters, Night, coordinates on, accuracy on, clock on, place on, white plate, bottom anchor, back camera, screen flash off, onboarding incomplete.

## 7. Technical map

| Layer | iOS |
| --- | --- |
| UI | SwiftUI. Safe area from the system. No device table. |
| Camera | AVFoundation session, photo output, preview layer |
| Location | Core Location |
| Barometer | Core Motion `CMAltimeter` |
| Geoid | `egm96-15.bin` in the app bundle, same bytes as Android |
| Photos | Photos framework, album “Altitude”, ImageIO metadata |
| Language | String Catalog, in-app locale passed into every lookup |
| Font | Outfit (OFL), files under `ios/Altitude/Resources/Fonts` |
| Pure logic | `ios/Altitude/Domain`, no UIKit |
| Tests | XCTest unit tests in the app host, XCUITest screenshots with a mock viewfinder |
| Version | 1.1.1 (3), iOS 17 and later, iPhone |

The mock viewfinder exists so screenshots and UI tests do not depend on a simulator camera. Launch arguments `-uiPreview`, `-uiLang`, `-uiLens`, `-uiFlash`, and `-uiImport` drive it. They are not a user-facing mode.

## 9. Import an existing photo

Android source: `PhotoExif`, `PhotoAltitude`, `ElevationLookup`, `PhotoImport`, `ImportPreviewScreen` on `cursor/import-photo-frame-c835`.

The person picks one to twelve photos that already exist. Altitude frames each one with the same watermark styles and the same top or bottom position. Every number on the frame comes from that photo.

| Value | Where it comes from | If it is missing |
| --- | --- | --- |
| Coordinates | EXIF/GPS latitude and longitude. If the file has none and the Photos asset was readable, `PHAsset.location`. | A notice. The frame can still be saved. No invented coordinates, and no “awaiting fix” line. |
| Capture time | `DateTimeOriginal`, then digitized, then TIFF `DateTime`, then GPS UTC. `OffsetTimeOriginal` is kept. The wall clock is shown as recorded, not shifted into the phone’s zone. | The time line is omitted. |
| Altitude | `GPSAltitude`, signed with `GPSAltitudeRef` (1 means below sea level). Zero and negative values count. | If the photo has coordinates, one Open-Meteo elevation lookup (Copernicus DEM). The frame shows `≈` and the preview says the altitude was computed. If that also fails, the altitude line is omitted. No placeholder dashes are burned into the saved image. |
| Place name | The existing Nominatim pipeline, only when coordinates exist and place names are enabled. | The place line is omitted. |

Limited photo access still exposes `PHAsset.location` for each asset the person picked. That location fills a file whose GPS was stripped, and the hidden-location notice stays off. A photo whose asset could not be read, and whose file also has no GPS, says the location was left out. A photo that was read and still has no GPS says the photo has no location. Neither case invents a coordinate.

The picker is `PhotosPicker` on `PHPhotoLibrary.shared()`, at most 12 images, so each item has an asset identifier. The loader asks Photos for the original image data (`PHImageRequestOptions.version = .original`). EXIF inside that file wins. The asset location is only a fill-in for a file whose GPS was removed. Saving calls `PHAssetCreationRequest` and writes a new JPEG into the Altitude album. The original asset is never edited.

Preview and the saved image use `WatermarkRenderer`. Plate and mount honor the top anchor: the band sits above the photo, and the hairline sits on the photo side of that band. EXIF orientation is baked into upright pixels before the frame is drawn. Portrait and landscape photos, and portrait and landscape devices, keep the notice, the style chips, and the position chips inside the safe area. Those chips wrap onto the next line when the row is narrower than their labels, including the landscape side column and the narrowest iPhone width (375pt). A label wider than the row wraps to two lines. Widths are not chosen per language.

Open-Meteo requests use the same user agent and the same one-request-per-second gate as place names. Results are cached on a 0.01°-scale key (`%.4f`). A null or failed height is not stored and is not drawn.

## 8. What “done” means

See `DEV_PLAN.md` for the step-by-step acceptance list. The product is done when:

- Every Android unit-test behavior listed in section 9 of the Android PRD, plus the place-name, zoom, focus, orientation, and locale tests, passes as XCTest on an iPhone simulator.
- UI tests capture portrait and landscape, back and front, the display, and Settings in Chinese, English, and French, from the mock camera.
- GitHub Actions on `macos-latest` builds for testing, grants photos, location, and camera on the simulator, then runs the tests. A screenshot fails if a system alert is visible. The workflow uploads those screenshots, an unsigned `.app`, and, when the toolchain allows it, an unsigned `.xcarchive`. No Apple account secret is used.
