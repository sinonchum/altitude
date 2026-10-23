# Altitude for iOS — development plan

The Android app on `cursor/trilingual-ui-f585` (1.3.2, versionCode 9) is the source of behavior. Work stays under `ios/`. The Android tree and pull request #2 are not modified.

Each step lists the features it covers, the implementation, what it depends on, what can proceed beside it, how it is tested, and the acceptance criteria. Status is updated in the checklist at the bottom. “Pass” means the criterion was checked by XCTest on the macOS GitHub Actions runner, or by a file/workflow check that does not need a device.

## Step 0 — Product spec

Features: the whole Android surface, written down before code.

Implementation: `ios/docs/PRD.md`, this file.

Depends on: reading the Android sources. Nothing in `ios/` yet.

Parallel with: nothing. Later steps start after this exists.

Test: a reader can match each Android class named in the PRD to an iOS type.

Acceptance:

- [ ] PRD covers display, camera, watermark, EXIF, zoom, tap focus, front camera, screen flash, landscape rails, gallery, settings, language, place names, GPS, and permissions.
- [ ] PRD states the iOS mapping for each of those, including Nominatim plus Apple geocoder and no place-name table.
- [ ] This plan names features, implementation, dependencies, parallelism, tests, and acceptance for every step.

## Step 1 — Pure logic

Features: units and grouping, ISA pressure altitude, EGM96, sea-level resolution, fusion, fix age, watermark fractions, camera zoom, focus choice, orientation snap, landscape stage math, place-name selection, Nominatim payload parsing, album query shape, coordinate and date formatting.

Implementation: Swift in `ios/Altitude/Domain`. Foundation only. No UIKit, AVFoundation, or Core Location types inside the decision functions. The geoid decoder reads the same `egm96-15.bin` the Android app ships.

Depends on: step 0 for scope. No UI.

Parallel with: step 2 (strings) and the static parts of step 4.

Test: XCTest in `AltitudeTests`, run on an iPhone simulator. Cases are the Android tests in `AltitudeCoreTest`, `PlaceLabelTest` (including the three real Shigatse payloads), `CameraCaptureTest`, `CameraZoomTest`, `FixClockTest`, `AlbumQueryTest`, and the locale-independent formatting cases.

Acceptance:

- [ ] 1013.25 hPa → 0 m. Round trip at 0 / 1000 / 3650 / 5200 / 8849 m within 0.05 m. Pressures outside 250…1100 hPa are rejected.
- [ ] Platform sea-level altitude wins over the geoid. Otherwise H = h − N. Missing both returns nil.
- [ ] Grid nodes: equator 17.160 m, north pole 13.610 m, 40°N 105°W −17.210 m. Lhasa about −34.6 m. Longitude wraps.
- [ ] GPS-only, barometer-only, snap, climb, slow bias, single outlier, 8 second reseat, holdover, and the 20 km rejection match the Android fusion tests.
- [ ] 5,210 m = 17,093 ft. Coordinate `29.65200° N`. Shanghai watermark time `2026.10.22  16:42`.
- [ ] White plate on a 3000×4000 photo is 3000×4486. Frame output is 3276×4738. Landscape plate uses the shorter edge. The 3:4 frame fits the available box.
- [ ] Zoom clamp, presets, pinch, and labels match `CameraZoomTest`.
- [ ] Orientation hysteresis, surface rotation, lens choice, autofocus capability, and tap flags match `CameraCaptureTest`.
- [ ] Landscape preview fraction on the Android sample sizes is at least 0.70. Shutter side follows rotation.
- [ ] Shigatse English payload → `Shigatse`. French payload → `Shigatse`. Chinese payload → `日喀则市`. Suburb is not chosen. A peak keeps its own name. Han in English is transliterated only when the closure returns Latin. Cache keys and the 1 request/second gate match `PlaceLabelTest`.
- [ ] Fix age prefers elapsed time. Stale fixes are dropped. `activeLocationFeeds` still requests satellite fixes when a fused provider exists.

## Step 2 — Strings

Features: every Android user-facing string in Simplified Chinese, English, and French. In-app language switch. Version line. System permission sentences.

Implementation: `Localizable.xcstrings` generated from the Android `strings.xml` catalogs. `InfoPlist.xcstrings` for the system permission dialogs. Lookup goes through `L10n` with an explicit locale, so the switch does not wait for a relaunch. Outfit fonts registered from the app bundle. Wordmark `ALTITUDE` is not translated.

Depends on: step 0. Can be written before the screens that read it.

Parallel with: step 1 and step 3.

Test: `LocaleCatalogTests` reads the catalog and checks key parity, format specifiers, plural categories, the untranslated wordmark, and the Shanghai date patterns (English and Chinese `2026.10.22  16:42`, French `22.10.2026  16:42`).

Acceptance:

- [ ] Every translatable Android key exists in `en`, `zh-Hans`, and `fr`, with the same format specifiers.
- [ ] `app_name`, `wordmark`, and the three language names are not translated. Wordmark is `ALTITUDE`.
- [ ] Plural categories match Android: English one/other, Chinese other, French one/many/other.
- [ ] Settings shows `1.0.0 (1)` from the bundle version, not a literal typed into the view body.
- [ ] No user-facing string is written as a literal in a SwiftUI view. Permission text is in the property-list catalog.

## Step 3 — Sensors, place names, photos

Features: GPS, barometer, geoid load, fusion clock, place-name fetch and cache, photo save with burned-in watermark and metadata, album list, share, delete.

Implementation:

- `AppModel` owns `AltitudeFusion`, a 10 Hz tick, and the last-known reading.
- `CLLocationManager` for fixes. `CMAltimeter` for pressure.
- `PlaceDirectory` calls Nominatim, rate-limits at 1/s, caches, and falls back to `CLGeocoder`.
- `WatermarkDrawing` is the Core Graphics port of `WatermarkRenderer`.
- `PhotoStore` writes JPEG + ImageIO GPS/EXIF and saves into the Altitude album.

Depends on: step 1 math, step 2 copy for errors.

Parallel with: step 4 layout, using a preview state that does not need the hardware.

Test: domain tests cover the decisions. The save path is exercised by `WatermarkRenderTests` (plate size and a non-empty JPEG with a GPS dictionary when coordinates exist, and no altitude tag when the reading is not sea level). Hardware capture is not required on the simulator.

Acceptance:

- [ ] A rendered plate JPEG has the pixel size from `WatermarkMetrics`.
- [ ] With coordinates and a sea-level altitude, the JPEG GPS dictionary contains latitude, longitude, and altitude, and TIFF software is `Altitude`.
- [ ] Without a sea-level altitude, the altitude tag is absent and the drawn altitude string is the unavailable mark.
- [ ] Place-name request spacing uses `millisUntilNextPlaceRequest`.

## Step 4 — Display, onboarding, settings

Features: full-screen altitude, rolling digits, fade-out chrome, unit double-tap, five themes, searching and last-known states, permission prompts, three-step onboarding, settings sheet, version line, language switch.

Implementation: SwiftUI. Background ignores the safe area. Controls use it. `PreviewLaunch` injects a Lhasa-like reading for screenshots so the test does not need a location fix.

Depends on: step 1 formatting, step 2 strings. Live numbers depend on step 3, but the preview state does not.

Parallel with: step 5.

Test: UI test `display-en` and `settings-en`, `settings-zh`, `settings-fr`. Unit tests cover formatting. Snapshot is the simulator PNG uploaded by CI.

Acceptance:

- [ ] Display screenshot shows the hero number, sea-level caption, coordinates, place, and the display/camera switch, inside the safe area.
- [ ] Settings screenshots show the language control and the version line in all three languages.
- [ ] Onboarding is skippable and is not shown again after completion. Preview launches skip it.

## Step 5 — Camera chrome

Features: viewfinder, four watermark styles, top/bottom overlay anchor, zoom presets and slider, shutter, last-photo thumbnail, front/back switch, screen-flash toggle, landscape rails, tap focus target, exposure slider when the device supports it.

Implementation: `CameraScreen` + `CameraSession`. Landscape when the stage is wider than it is tall. Mock scene when `-uiPreview camera` is set, so CI can shoot back and front, portrait and landscape, without a camera device. The mock still runs the real watermark view and the real chrome.

Depends on: step 1 metrics, step 2 strings, step 3 drawing. The live session depends on camera permission. Screenshots do not.

Parallel with: step 4.

Test: UI tests for `camera-portrait-back`, `camera-portrait-front`, `camera-landscape-back`, `camera-landscape-front`. The landscape test asserts the preview height is at least 70% of the stage.

Acceptance:

- [ ] Portrait back and front screenshots show the viewfinder, watermark, shutter, and zoom controls.
- [ ] Front screenshots show the screen-flash control in the same tool cluster as Settings.
- [ ] Landscape screenshots keep the preview large and move controls to side rails.
- [ ] Tap-to-focus uses `Autofocus.tapFlags` with the device capability closure, including the front camera.

## Step 6 — Gallery and permissions on device

Features: in-app pager, share, delete confirm, open in Photos. Permission prompts on the display and on the camera gate.

Implementation: `GalleryView`, Photos framework, `CLLocationManager` authorization, `AVCaptureDevice.requestAccess`, `PHPhotoLibrary.requestAuthorization`.

Depends on: step 3 album, step 5 thumbnail.

Parallel with: the screenshot work in steps 4 and 5. A simulator UI test does not grant the system dialogs.

Test: `AlbumQuery` unit tests. Gallery empty state is reachable from the camera when the album is empty. Manual device check is left to a signed install. CI does not tap the system permission dialog.

Acceptance:

- [ ] Gallery strings exist in all three languages.
- [ ] Delete and share actions are present in the gallery view.
- [ ] Denied location and denied camera render an explanation and a button, not a blank screen.

## Step 7 — CI, screenshots, unsigned build

Features: a build a reviewer can look at without a Mac on this VM.

Implementation: `.github/workflows/ios.yml` on `macos-latest`. XcodeGen creates the project. `xcodebuild test` runs unit and UI tests on an available iPhone simulator. UI tests write PNGs. The workflow uploads the screenshots, the `.xcresult`, a simulator `.app`, and an unsigned `.xcarchive` when archive succeeds. Signing is off. No Apple secret.

Depends on: steps 1–6.

Parallel with: nothing. This is the gate.

Test: the workflow itself.

Acceptance:

- [ ] The workflow is green.
- [ ] Unit tests and UI tests ran on an iPhone simulator.
- [ ] Screenshot artifacts include portrait and landscape, back and front, the display, and Settings in three languages.
- [ ] An unsigned `.app` or `.xcarchive` is uploaded. The workflow does not read a signing certificate or an App Store key.

## Parallelism

| Can run together | Must wait |
| --- | --- |
| Step 1 math, step 2 strings, static layout in steps 4 and 5 | Watermark pixels wait on step 1 fractions |
| Preview screenshots and the live camera session | UI tests wait on steps 4 and 5 |
| Gallery chrome and sensor fusion | Step 7 waits on the rest |

## Checklist

Status values: `pass` only after a green macOS workflow. Evidence: [run 39942210594](https://github.com/sinonchum/altitude/actions/runs/39942210594) on iPhone 17 Pro (Xcode 26.6, iOS 26.5). 63 unit tests passed, including the ported Nominatim payloads, catalog parity, and the watermark JPEG size plus GPS and TIFF software tags. All 8 UI screenshot tests passed, including the landscape preview fraction. Artifacts: `ios-screenshots`, `ios-unsigned-build`, `ios-xcresult`.

| ID | Criterion | Status |
| --- | --- | --- |
| A0 | PRD and this plan exist and cover the Android surface | pass |
| A1 | Domain XCTest suite passes on the iPhone simulator | pass |
| A2 | String catalog parity test passes | pass |
| A3 | Watermark JPEG size and GPS metadata test passes | pass |
| A4 | Display and trilingual settings screenshots are produced | pass |
| A5 | Portrait and landscape, back and front, camera screenshots are produced | pass |
| A6 | Landscape UI test checks the preview fraction | pass |
| A7 | Unsigned build artifact is uploaded | pass |
| A8 | Workflow run is green | pass |

Version shown in Settings: **1.0.0 (1)**.
