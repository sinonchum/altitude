# Altitude

[English](#english) · [中文](#zh)

<a id="english"></a>

## English

Altitude shows height above sea level in large type, and writes that reading into photographs.

- **Display.** A full-screen sea-level altitude that stays awake, including at maximum brightness, so someone else can photograph the phone. Switch meters and feet by double-tapping the number. Controls fade until you tap the screen.
- **Camera.** The viewfinder and the saved JPEG share one watermark: altitude, coordinates, time, and a place name when one can be resolved. The picture is redrawn at capture resolution and saved to `Pictures/Altitude`, with latitude, longitude, and sea-level altitude written into EXIF when they exist. The wordmark is `ALTITUDE`, and the red dot is this app’s own mark.
- **Frame a photo you already have.** Choose it from the in-app list. Altitude reads that file’s GPS, altitude, time, and orientation from MediaStore and saves a new copy. The system photo picker removes location, so the list is how GPS is preserved. If the photo has coordinates but no altitude, a Copernicus DEM height from Open-Meteo is shown with a computed mark. If there is still no altitude, that line is left off. The original file is not modified.

The interface is Simplified Chinese, English, or French, following the system language or the choice in Settings. Pinch to zoom, or use a preset when the lens supports it. Tap to focus, and press and hold to lock focus and exposure. Switch between the front and back cameras. In landscape the viewfinder stays large between the side controls. The front camera has a screen flash. Photos saved by the app can be reviewed, shared, and deleted in the app. Watermark styles are white, black, frame, and overlay. Display themes are Night, Black, Glacier, Saffron, and Snow.

There is no account, map, or track log. The product requirements are in [docs/PRD.md](docs/PRD.md).

### Requirements

- JDK 17 or newer
- Android SDK platform 37 and its build-tools
- A device or emulator on Android 8.0 (API 26) or newer

`minSdk` is 26, `targetSdk` is 36, and `compileSdk` is 37. Point Gradle at the SDK in `local.properties`:

```
sdk.dir=/path/to/Android/sdk
```

### Build

The Outfit fonts, the EGM96 grid, and the Gradle wrapper are already in a normal checkout. Restore them if a copy is missing any of the three:

```bash
python3 tools/fetch_assets.py
```

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/Altitude-1.4.1-debug.apk`. Debug builds are signed with the certificate in `app/debug.keystore`, so a newer debug APK replaces an older one.

On an emulator, inject ellipsoid height as longitude, latitude, then meters. Near Lhasa the geoid is about −35 m, so an injected height of 3650 is shown as about 3685 m above sea level:

```bash
adb emu geo fix 91.117 29.652 3650
```

An emulator has no barometer. A phone with one labels the reading as satellite plus barometer.

### Releases

This tree is Android 1.4.1, iOS 1.0.0. Debug APKs are published as GitHub prereleases under tags `v*-debug`:

- [All releases](https://github.com/sinonchum/altitude/releases)

### Data sources and attribution

- **Sea level.** GPS ellipsoid height is converted with the bundled EGM96 15′ geoid (`app/src/main/assets/egm96-15.bin`, about 2 MB), taken from GeographicLib’s `egm96-15.pgm` (NGA public data). On Android 14 and later, the platform sea-level altitude is used when the system provides one. A barometer, when present, follows short motion and is calibrated from GPS. The live reading does not need a network.
- **Place names.** [OpenStreetMap Nominatim](https://nominatim.openstreetmap.org/) reverse geocoding, in the app language, preferring the settlement (city, town, or village) over a suburb. At most one request per second. The User-Agent is `Altitude/<version> (https://github.com/sinonchum/altitude)`. Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors. If Nominatim cannot be reached, the platform geocoder is used.
- **Imported altitude.** When a photo has coordinates and no altitude of its own, the [Open-Meteo elevation API](https://open-meteo.com/) returns a Copernicus DEM height. No API key is required.
- **Type.** [Outfit](https://github.com/Outfitio/Outfit-Fonts) by The Outfit Project Authors, under the SIL Open Font License 1.1. See [third_party/outfit/OFL.txt](third_party/outfit/OFL.txt). Chinese text uses the system sans-serif.

### Privacy

Altitude has no account and does not upload a track. Location, camera, and photo access are requested when those features are used.

The live altitude is computed on the phone. Coordinates are sent to Nominatim when a place name is requested, and to Open-Meteo when an imported photo needs an elevation. Photos you take or frame are stored on the device in `Pictures/Altitude`. On iOS, photos are saved to a Photos album named Altitude.

### iOS

`ios/` is a native iPhone app for iOS 17 and later. This tree’s iOS version is 1.0.0. The bundle id is `app.altitude.ios`. It includes the full-screen altitude display, the stamped camera, the front and back cameras, and landscape shooting.

Saved photos go into a Photos album named Altitude.

Requirements: Xcode, and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

```bash
cd ios
xcodegen generate
xcodebuild build \
  -project Altitude.xcodeproj \
  -scheme Altitude \
  -destination 'platform=iOS Simulator,name=iPhone 17'
```

Pick an iPhone simulator installed on the machine. `xcrun simctl list devices available` shows the names. GitHub Actions runs the same project on `macos-latest` via [.github/workflows/ios.yml](.github/workflows/ios.yml): it generates the project, grants photo, location, and camera access on the simulator, then runs the unit and UI tests. The workflow does not use an Apple signing certificate. Product requirements for iOS are in [ios/docs/PRD.md](ios/docs/PRD.md). The Outfit license for the iOS copy is [ios/Altitude/Resources/Fonts/OFL.txt](ios/Altitude/Resources/Fonts/OFL.txt), and the same EGM96 grid is in the app bundle.

<a id="zh"></a>

## 中文

Altitude 用大字显示海平面海拔，并把这个读数写进照片。

- **亮屏。** 全屏显示海平面海拔，屏幕保持常亮，也可以拉到最高亮度，方便别人用另一台手机拍下这个数字。双击数字在米和英尺之间切换。控件会淡出，点一下屏幕再出现。
- **相机。** 取景和保存的 JPEG 使用同一套水印：海拔、经纬度、时间，以及能解析到的地名。成片按拍照分辨率重绘，保存到 `Pictures/Altitude`；有经纬度或海平面海拔时写入 EXIF。词标是 `ALTITUDE`，红点是本应用自己的记号。
- **给已有照片加框。** 从应用内的照片列表选择。Altitude 通过 MediaStore 读取原文件里的 GPS、海拔、时间和方向，并另存一张新照片。原文件不会被修改。系统照片选择器会去掉位置，所以要从这个列表导入才能保留 GPS。照片有坐标但没有海拔时，用 Open-Meteo 的 Copernicus DEM 高度，并标明是推算值。仍然没有海拔时，不画海拔那一行。

界面为简体中文、English 或 Français，可跟随系统，也可在设置里选择。可以双指变焦，镜头支持时也可以用预设倍数。点按对焦，长按锁定对焦和曝光。可在前置和后置相机之间切换。横屏时取景框位于两侧控件之间，并保持足够大。前置相机可以打开屏幕补光。本应用保存的照片可以在应用内查看、分享和删除。水印样式有白底、黑底、画框和浮印。亮屏主题有夜色、纯黑、冰川、暖金和雪地。

没有账号、地图或轨迹记录。产品需求见 [docs/PRD.md](docs/PRD.md)。

### 环境

- JDK 17 或更高版本
- Android SDK Platform 37 及对应的 build-tools
- Android 8.0（API 26）或更高版本的设备或模拟器

`minSdk` 为 26，`targetSdk` 为 36，`compileSdk` 为 37。在 `local.properties` 中指定 SDK：

```
sdk.dir=/path/to/Android/sdk
```

### 构建

正常检出已经包含 Outfit 字体、EGM96 网格和 Gradle Wrapper。若缺少其中任何一项，可以补回：

```bash
python3 tools/fetch_assets.py
```

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/Altitude-1.4.1-debug.apk`。调试包使用 `app/debug.keystore` 里的证书签名，因此新的调试包可以覆盖安装旧的调试包。

在模拟器上注入椭球高，顺序是经度、纬度、米。拉萨附近大地水准面差距大约 −35 米，所以注入 3650 后，屏幕上的海平面高度大约是 3685 米：

```bash
adb emu geo fix 91.117 29.652 3650
```

模拟器没有气压计。有气压计的手机会把读数来源标成卫星加气压。

### 版本与下载

本树为 Android 1.4.1 与 iOS 1.0.0。调试 APK 以 GitHub 预发布的形式挂在 `v*-debug` 标签上：

- [全部发布](https://github.com/sinonchum/altitude/releases)

### 数据来源与署名

- **海平面高度。** GPS 椭球高用随包的 EGM96 15′ 网格换算（`app/src/main/assets/egm96-15.bin`，约 2 MB），来自 GeographicLib 的 `egm96-15.pgm`（NGA 公开数据）。Android 14 及以上在系统提供海平面高度时采用系统值。有气压计时，短时间的升降交给气压计，并由 GPS 校准。实时读数不需要网络。
- **地名。** 使用 [OpenStreetMap Nominatim](https://nominatim.openstreetmap.org/) 反向地理编码，语言与应用一致，优先取城市、镇或村，而不是郊区。最多每秒一次请求。User-Agent 为 `Altitude/<version> (https://github.com/sinonchum/altitude)`。地图数据 © [OpenStreetMap](https://www.openstreetmap.org/copyright) 贡献者。Nominatim 无法连接时，改用系统地理编码。
- **导入照片的海拔。** 照片有坐标但没有自己的海拔时，向 [Open-Meteo 高程接口](https://open-meteo.com/) 查询 Copernicus DEM 高度。不需要 API 密钥。
- **字体。** [Outfit](https://github.com/Outfitio/Outfit-Fonts)，作者为 The Outfit Project Authors，采用 SIL Open Font License 1.1。许可见 [third_party/outfit/OFL.txt](third_party/outfit/OFL.txt)。中文使用系统无衬线字体。

### 隐私

Altitude 没有账号，也不会上传轨迹。定位、相机和照片权限在使用对应功能时才会请求。

实时海拔在手机上计算。请求地名时会把坐标发给 Nominatim；导入的照片需要补海拔时，会把坐标发给 Open-Meteo。拍摄或加框的照片保存在设备上的 `Pictures/Altitude`。在 iOS 上，照片保存在名为 Altitude 的相簿中。

### iOS

`ios/` 是面向 iPhone、系统为 iOS 17 及以上的原生应用。本树中的 iOS 版本是 1.0.0。Bundle id 为 `app.altitude.ios`。它包含全屏海拔、带水印的相机、前后摄和横屏拍摄。

保存的照片进入名为 Altitude 的照片相簿。

需要 Xcode，以及 [XcodeGen](https://github.com/yonaskolb/XcodeGen)（`brew install xcodegen`）。

```bash
cd ios
xcodegen generate
xcodebuild build \
  -project Altitude.xcodeproj \
  -scheme Altitude \
  -destination 'platform=iOS Simulator,name=iPhone 17'
```

名称以本机已安装的 iPhone 模拟器为准，可用 `xcrun simctl list devices available` 查看。[.github/workflows/ios.yml](.github/workflows/ios.yml) 在 `macos-latest` 上生成工程，向模拟器授予照片、定位和相机权限，然后运行单元测试和界面测试。工作流不使用 Apple 签名证书。iOS 的产品需求见 [ios/docs/PRD.md](ios/docs/PRD.md)。iOS 所用 Outfit 的许可在 [ios/Altitude/Resources/Fonts/OFL.txt](ios/Altitude/Resources/Fonts/OFL.txt)，应用包内带有同一份 EGM96 网格。
