# Altitude

把海平面海拔举到镜头前，或直接写进拍下的照片。

两种用法：

- **亮屏**：全屏大数字，屏幕常亮，可拉到最高亮度。适合在拉萨到珠峰大本营一带把手机举起来让别人拍。
- **相机**：取景时底栏显示海拔和经纬度，按下快门后按成片分辨率重画进 JPEG，存到相册 `Pictures/Altitude`，并把经纬度与海拔写入 EXIF。

界面跟随系统语言，也可以在设置里改成简体中文、English 或 Français。水印上的词标是不翻译的拉丁文 `ALTITUDE`，红点是本应用自己的记号，不使用任何相机品牌的名称或标志，也不填写焦段和光圈。

产品范围和验收标准在 [docs/PRD.md](docs/PRD.md)。

## 环境

- JDK 17 或 21
- Android SDK：compileSdk 37，build-tools 37，以及一台 API 26 以上的设备或模拟器
- `local.properties` 里写 `sdk.dir`

```
sdk.dir=/path/to/Android/sdk
```

minSdk 26，targetSdk 36。

## 构建与安装

```
python3 tools/fetch_assets.py
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`tools/fetch_assets.py` 补上三样东西：Outfit 字体、Gradle Wrapper、EGM96 网格。仓库里已经有这些文件时，脚本不会重新下载。

调试包：`app/build/outputs/apk/debug/app-debug.apk`。

## 模拟器里看海拔

模拟器没有气压计，定位也要手动注入。`geo fix` 的第三个参数是椭球高（米），顺序是经度、纬度、高度。拉萨附近大地水准面差距大约 −35 米，所以注入 3650 后，屏幕上的海平面高度大约是 3685，这是预期结果。

```
adb emu geo fix 91.117 29.652 3650
```

真机有气压计时，读数来源会写成「卫星 + 气压」。模拟器上只有卫星。

这台开发机的嵌套虚拟化不可用（`/dev/kvm` 能打开，但创建 vCPU 时内核会出错）。模拟器只能用软件模拟，启动和第一帧都会很慢，系统界面也可能弹出「没有响应」。真机或打开了 KVM 的电脑上不会这样。

## 离线

海拔换算使用随包的 EGM96 15′ 网格（约 2 MB）。Android 14 及以上如果系统已经给出海平面高度，就用系统值。地名依赖系统地理编码，没网时可以空着。核心读数不需要网络。

EGM96 网格来自 GeographicLib 的 `egm96-15.pgm`（NGA 公开数据）。字体 Outfit 使用 SIL Open Font License，见 `third_party/outfit/OFL.txt`。
