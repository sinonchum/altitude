# Altitude

[English](#english) · [中文](#zh)

<a id="english"></a>

## English

Altitude shows height above sea level in large type, and writes that reading into photographs.

- **Display.** A full-screen sea-level altitude that stays awake, including at maximum brightness, so someone else can photograph the phone. Switch meters and feet by double-tapping the number. Controls fade until you tap the screen.
- **Camera.** The viewfinder and the saved JPEG share one watermark: altitude, coordinates, time, and a place name when one can be resolved. The picture is redrawn at capture resolution and saved to `Pictures/Altitude`, with latitude, longitude, and sea-level altitude written into EXIF when they exist. The wordmark is `ALTITUDE`, and the red dot is this app’s own mark.

The interface is Simplified Chinese, English, or French, following the system language or the choice in Settings. Photos saved by the app can be reviewed, shared, and deleted in the app. Watermark styles are white, black, frame, and overlay. Display themes are Night, Black, Glacier, Saffron, and Snow.

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

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

On an emulator, inject ellipsoid height as longitude, latitude, then meters. Near Lhasa the geoid is about −35 m, so an injected height of 3650 is shown as about 3685 m above sea level:

```bash
adb emu geo fix 91.117 29.652 3650
```

An emulator has no barometer. A phone with one labels the reading as satellite plus barometer.

### Releases

This tree is Android 1.1.1. Debug APKs are published as GitHub prereleases under tags `v*-debug`:

- [All releases](https://github.com/sinonchum/altitude/releases)

### Data sources and attribution

- **Sea level.** GPS ellipsoid height is converted with the bundled EGM96 15′ geoid (`app/src/main/assets/egm96-15.bin`, about 2 MB), taken from GeographicLib’s `egm96-15.pgm` (NGA public data). On Android 14 and later, the platform sea-level altitude is used when the system provides one. A barometer, when present, follows short motion and is calibrated from GPS. The live reading does not need a network.
- **Place names.** The platform geocoder, when it is available. The line can be empty offline.
- **Type.** [Outfit](https://github.com/Outfitio/Outfit-Fonts) by The Outfit Project Authors, under the SIL Open Font License 1.1. See [third_party/outfit/OFL.txt](third_party/outfit/OFL.txt). Chinese text uses the system sans-serif.

### Privacy

Altitude has no account and does not upload a track. Location, camera, and photo access are requested when those features are used.

The live altitude is computed on the phone. A place-name lookup may send coordinates to the platform geocoder. Photos you take or frame are stored on the device in `Pictures/Altitude`.

<a id="zh"></a>

## 中文

Altitude 用大字显示海平面海拔，并把这个读数写进照片。

- **亮屏。** 全屏显示海平面海拔，屏幕保持常亮，也可以拉到最高亮度，方便别人用另一台手机拍下这个数字。双击数字在米和英尺之间切换。控件会淡出，点一下屏幕再出现。
- **相机。** 取景和保存的 JPEG 使用同一套水印：海拔、经纬度、时间，以及能解析到的地名。成片按拍照分辨率重绘，保存到 `Pictures/Altitude`；有经纬度或海平面海拔时写入 EXIF。词标是 `ALTITUDE`，红点是本应用自己的记号。

界面为简体中文、English 或 Français，可跟随系统，也可在设置里选择。本应用保存的照片可以在应用内查看、分享和删除。水印样式有白底、黑底、画框和浮印。亮屏主题有夜色、纯黑、冰川、暖金和雪地。

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

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

在模拟器上注入椭球高，顺序是经度、纬度、米。拉萨附近大地水准面差距大约 −35 米，所以注入 3650 后，屏幕上的海平面高度大约是 3685 米：

```bash
adb emu geo fix 91.117 29.652 3650
```

模拟器没有气压计。有气压计的手机会把读数来源标成卫星加气压。

### 版本与下载

本树为 Android 1.1.1。调试 APK 以 GitHub 预发布的形式挂在 `v*-debug` 标签上：

- [全部发布](https://github.com/sinonchum/altitude/releases)

### 数据来源与署名

- **海平面高度。** GPS 椭球高用随包的 EGM96 15′ 网格换算（`app/src/main/assets/egm96-15.bin`，约 2 MB），来自 GeographicLib 的 `egm96-15.pgm`（NGA 公开数据）。Android 14 及以上在系统提供海平面高度时采用系统值。有气压计时，短时间的升降交给气压计，并由 GPS 校准。实时读数不需要网络。
- **地名。** 在系统地理编码可用时显示。离线时这一行可以留空。
- **字体。** [Outfit](https://github.com/Outfitio/Outfit-Fonts)，作者为 The Outfit Project Authors，采用 SIL Open Font License 1.1。许可见 [third_party/outfit/OFL.txt](third_party/outfit/OFL.txt)。中文使用系统无衬线字体。

### 隐私

Altitude 没有账号，也不会上传轨迹。定位、相机和照片权限在使用对应功能时才会请求。

实时海拔在手机上计算。解析地名时，系统地理编码可能会收到坐标。拍摄或加框的照片保存在设备上的 `Pictures/Altitude`。
