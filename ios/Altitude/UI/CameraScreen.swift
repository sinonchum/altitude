import SwiftUI
import UIKit

struct CameraScreen: View {
    @Bindable var model: AppModel
    @State private var session = CameraSession()
    @State private var zoom: Float = 1
    @State private var zoomMin: Float = 1
    @State private var zoomMax: Float = 1
    @State private var pinchBase: Float = 1
    @State private var exposure: Float = 0
    @State private var exposureMin: Float = 0
    @State private var exposureMax: Float = 0
    @State private var exposureSupported = false
    @State private var backAvailable = false
    @State private var frontAvailable = false
    @State private var busy = false
    @State private var flashCover = false
    @State private var focusPoint: CGPoint?
    @State private var focusLocked = false
    @State private var failed = false
    @State private var previewView: PreviewView?

    var body: some View {
        GeometryReader { geo in
            let landscape = geo.size.width > geo.size.height
            let safe = geo.safeAreaInsets
            ZStack {
                Color.black.ignoresSafeArea()
                if !model.cameraGranted && model.preview == nil {
                    permission
                        .safeAreaPadding(.horizontal, 28)
                } else if landscape {
                    landscapeStage(safe: safe)
                } else {
                    portraitStage(safe: safe)
                }
                if flashCover {
                    Color.white.ignoresSafeArea()
                }
                if let message = model.saveMessage {
                    Text(message)
                        .font(Outfit.medium(14))
                        .foregroundStyle(Color(hex: 0x141414))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color(hex: 0xF4F1EA), in: Capsule())
                        .frame(maxHeight: .infinity, alignment: .top)
                        .padding(.top, safe.top + 8)
                }
            }
        }
        .ignoresSafeArea()
        .onAppear(perform: configure)
        .onChange(of: model.prefs.frontCamera) { _, _ in configure() }
    }

    private var copy: Copy { model.copy }
    private var shutterOnEnd: Bool {
        let orientation = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first?.interfaceOrientation
        return orientation != .landscapeLeft
    }
    private var showLens: Bool { CameraLenses.buttonVisible(back: backAvailable, front: frontAvailable) }
    private var showFlash: Bool { model.prefs.frontCamera && frontAvailable }
    private var presets: [Float] { CameraZoom.presetsWithin(zoomMin, zoomMax) }

    private func portraitStage(safe: EdgeInsets) -> some View {
        VStack(spacing: 0) {
            HStack {
                ModeSwitch(mode: .camera, copy: copy, primary: .white, secondary: Color(hex: 0xB7B7B2)) { model.setMode($0) }
                Spacer()
                tools(stacked: false)
            }
            .padding(.horizontal, 8)
            .padding(.top, safe.top + 4)
            GeometryReader { mid in
                frame(in: mid.size)
            }
            .padding(.horizontal, 4)
            if model.guidance != nil && model.preview == nil {
                guidanceBanner.padding(.bottom, 8)
            }
            zoomRow
                .padding(.top, 6)
            styleRow(vertical: false)
            if model.prefs.watermarkStyle == .caption {
                anchorRow(vertical: false).padding(.top, 8)
            }
            HStack {
                thumbnail
                Spacer(minLength: 8)
                ShutterButton(enabled: model.cameraGranted || model.preview != nil, busy: busy, label: copy.text("cd_shutter")) { shoot() }
                Spacer(minLength: 8)
                lensButton
            }
            .padding(.horizontal, 28)
            .padding(.top, 12)
            .padding(.bottom, max(safe.bottom, 8))
        }
    }

    private func landscapeStage(safe: EdgeInsets) -> some View {
        let leading = safe.leading
        let trailing = safe.trailing
        return HStack(spacing: 0) {
            if shutterOnEnd {
                utilityRail.frame(width: CGFloat(CameraStageMetrics.utilityRail)).padding(.leading, leading).padding(.vertical, max(safe.top, safe.bottom, 4))
                GeometryReader { mid in landscapeFrame(in: mid.size) }
                zoomColumn.frame(width: CGFloat(CameraStageMetrics.zoomRail)).padding(.vertical, max(safe.top, safe.bottom, 4))
                captureRail.frame(width: CGFloat(CameraStageMetrics.shutterRail)).padding(.trailing, trailing).padding(.vertical, max(safe.top, safe.bottom, 4))
            } else {
                captureRail.frame(width: CGFloat(CameraStageMetrics.shutterRail)).padding(.leading, leading).padding(.vertical, max(safe.top, safe.bottom, 4))
                zoomColumn.frame(width: CGFloat(CameraStageMetrics.zoomRail)).padding(.vertical, max(safe.top, safe.bottom, 4))
                GeometryReader { mid in landscapeFrame(in: mid.size) }
                utilityRail.frame(width: CGFloat(CameraStageMetrics.utilityRail)).padding(.trailing, trailing).padding(.vertical, max(safe.top, safe.bottom, 4))
            }
        }
    }

    /// The live image fills the column between the rails. The watermark band sits on the image so the viewfinder still reaches the screen edges.
    private func landscapeFrame(in available: CGSize) -> some View {
        let width = max(available.width, 1)
        let height = max(available.height, 1)
        let metric = min(width, height)
        let plate = CGFloat(WatermarkMetrics.plateHeightPx(
            photoWidth: Int(metric.rounded()),
            hasPlace: model.prefs.showPlace && model.placeName != nil
        ))
        let content = model.watermarkContent()
        return ZStack(alignment: .bottom) {
            scene
                .frame(width: width, height: height)
                .clipped()
            if model.prefs.watermarkStyle == .caption {
                WatermarkPlate(content: content, style: .caption, anchor: model.prefs.captionAnchor, metric: metric)
                    .frame(width: width, height: height)
                    .allowsHitTesting(false)
            } else {
                WatermarkPlate(
                    content: content,
                    style: model.prefs.watermarkStyle,
                    anchor: .bottom,
                    metric: metric
                )
                .frame(width: width, height: min(plate, height * 0.34))
                .allowsHitTesting(false)
            }
        }
        .frame(width: width, height: height)
        .clipped()
        .accessibilityIdentifier("camera.preview")
        .gesture(pinch)
        .simultaneousGesture(tap)
    }

    private var scene: some View {
        Group {
            if model.preview != nil {
                MockScene(front: model.prefs.frontCamera)
            } else {
                CameraPreview(session: session.session) { previewView = $0 }
            }
        }
    }

    private func frame(in available: CGSize) -> some View {
        let width = max(available.width, 1)
        let height = max(available.height, 1)
        let aspect: Float = width > height ? 4 / 3 : 3 / 4
        let fit = WatermarkMetrics.fitFrame(
            availWidth: Float(width),
            availHeight: Float(height),
            photoAspect: aspect,
            style: model.prefs.watermarkStyle,
            hasPlace: model.prefs.showPlace && model.placeName != nil
        )
        let content = model.watermarkContent()
        let metric = min(fit.photoWidth, fit.photoHeight)
        return VStack(spacing: 0) {
            if model.prefs.watermarkStyle == .mount {
                Color.white.frame(height: CGFloat(fit.top))
            }
            ZStack {
                scene
                if model.prefs.watermarkStyle == .caption {
                    WatermarkPlate(content: content, style: .caption, anchor: model.prefs.captionAnchor, metric: CGFloat(metric))
                        .allowsHitTesting(false)
                }
                if let focusPoint {
                    focusMark
                        .position(focusPoint)
                }
            }
            .frame(width: CGFloat(fit.photoWidth), height: CGFloat(fit.photoHeight))
            .clipped()
            .contentShape(Rectangle())
            .gesture(pinch)
            .simultaneousGesture(tap)
            .accessibilityIdentifier("camera.preview")
            .accessibilityLabel(copy.text("cd_camera_preview"))
            if model.prefs.watermarkStyle != .caption {
                WatermarkPlate(
                    content: content,
                    style: model.prefs.watermarkStyle,
                    anchor: model.prefs.captionAnchor,
                    metric: CGFloat(metric)
                )
                .frame(width: CGFloat(fit.photoWidth), height: CGFloat(fit.bottom))
            }
        }
        .background(model.prefs.watermarkStyle == .plateDark ? Color(hex: 0x0A0A0A) : (model.prefs.watermarkStyle == .caption ? Color.black : Color.white))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var focusMark: some View {
        RoundedRectangle(cornerRadius: 2)
            .stroke(Color(hex: 0xF6C453), lineWidth: 1.5)
            .frame(width: 72, height: 72)
            .overlay(alignment: .bottom) {
                if focusLocked {
                    Text(copy.text("focus_locked"))
                        .font(Outfit.medium(11))
                        .foregroundStyle(Color(hex: 0xF6C453))
                        .offset(y: 16)
                }
            }
            .accessibilityLabel(copy.text("cd_focus_target"))
    }

    private var pinch: some Gesture {
        MagnificationGesture()
            .onChanged { scale in
                let next = CameraZoom.afterPinch(pinchBase, Float(scale), zoomMin, zoomMax)
                applyZoom(next)
            }
            .onEnded { _ in pinchBase = zoom }
    }

    private var tap: some Gesture {
        SpatialTapGesture()
            .onEnded { value in
                focusPoint = value.location
                focusLocked = false
                if let previewView {
                    session.focus(at: previewView.devicePoint(from: value.location), lock: false)
                }
                Task {
                    try? await Task.sleep(nanoseconds: 1_400_000_000)
                    if !focusLocked { focusPoint = nil }
                }
            }
            .simultaneously(with: LongPressGesture(minimumDuration: 0.45).onEnded { _ in
                focusLocked = true
                if let previewView, let focusPoint {
                    session.focus(at: previewView.devicePoint(from: focusPoint), lock: true)
                }
            })
    }

    private var zoomRow: some View {
        VStack(spacing: 8) {
            presetButtons(vertical: false)
            if zoomMax - zoomMin > 0.05 {
                Slider(value: Binding(get: { Double(zoom) }, set: { applyZoom(Float($0)) }), in: Double(zoomMin)...Double(zoomMax))
                    .tint(.white)
                    .padding(.horizontal, 36)
                    .accessibilityLabel(copy.text("cd_zoom_slider"))
            }
            if exposureSupported {
                Slider(value: Binding(get: { Double(exposure) }, set: { setExposure(Float($0)) }), in: Double(exposureMin)...Double(exposureMax))
                    .tint(Color(hex: 0xF6C453))
                    .padding(.horizontal, 48)
                    .accessibilityLabel(copy.text("cd_exposure"))
            }
        }
    }

    private var zoomColumn: some View {
        VStack {
            presetButtons(vertical: true)
        }
        .frame(maxHeight: .infinity)
    }

    private func presetButtons(vertical: Bool) -> some View {
        let buttons = ForEach(presets, id: \.self) { preset in
            let selected = CameraZoom.matchesPreset(zoom, preset)
            let label = CameraZoom.formatZoomNumber(preset, model.language.formatLocale)
            Button {
                applyZoom(preset)
            } label: {
                Text(copy.format("zoom_ratio", label))
                    .font(Outfit.semibold(13))
                    .foregroundStyle(selected ? Color.black : Color.white)
                    .padding(.horizontal, vertical ? 6 : 10)
                    .padding(.vertical, 6)
                    .background(selected ? Color.white : Color.white.opacity(0.12), in: Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(copy.format("cd_zoom_preset", label))
        }
        return Group {
            if vertical { VStack(spacing: 8) { buttons } } else { HStack(spacing: 8) { buttons } }
        }
    }

    private func styleRow(vertical: Bool) -> some View {
        let chips = ForEach(WatermarkStyle.allCases, id: \.rawValue) { style in
            ChoiceChip(title: copy.watermarkName(style), selected: model.prefs.watermarkStyle == style, accent: .white) {
                model.setWatermark(style)
            }
        }
        return Group {
            if vertical {
                VStack(spacing: 6) { chips }
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) { chips }
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                }
            }
        }
    }

    private func anchorRow(vertical: Bool) -> some View {
        let chips = ForEach(CaptionAnchor.allCases, id: \.rawValue) { anchor in
            ChoiceChip(title: copy.anchorName(anchor), selected: model.prefs.captionAnchor == anchor, accent: Color(hex: 0xF6C453)) {
                model.setAnchor(anchor)
            }
        }
        return Group {
            if vertical { VStack(spacing: 6) { chips } } else { HStack(spacing: 6) { chips } }
        }
    }

    private var utilityRail: some View {
        ScrollView {
            VStack(spacing: 8) {
                ModeSwitch(mode: .camera, copy: copy, primary: .white, secondary: Color(hex: 0xB7B7B2), vertical: true) { model.setMode($0) }
                styleRow(vertical: true)
                if model.prefs.watermarkStyle == .caption { anchorRow(vertical: true) }
                tools(stacked: true)
            }
            .padding(.vertical, 8)
        }
    }

    private var captureRail: some View {
        VStack {
            lensButton
            Spacer()
            ShutterButton(enabled: model.cameraGranted || model.preview != nil, busy: busy, label: copy.text("cd_shutter")) { shoot() }
            Spacer()
            thumbnail
        }
        .padding(.vertical, 12)
    }

    private func tools(stacked: Bool) -> some View {
        let content = Group {
            if showFlash {
                CircleTool(
                    systemName: model.prefs.screenFlash ? AppSymbols.flashOn : AppSymbols.flashOff,
                    accessibility: copy.text(model.prefs.screenFlash ? "cd_screen_flash_on" : "cd_screen_flash_off"),
                    emphasized: model.prefs.screenFlash
                ) {
                    model.setScreenFlash(!model.prefs.screenFlash)
                }
                .accessibilityIdentifier("screen.flash")
            }
            CircleTool(systemName: AppSymbols.settings, accessibility: copy.text("cd_settings")) {
                model.settingsOpen = true
            }
        }
        return Group {
            if stacked { VStack(spacing: 8) { content } } else { HStack(spacing: 8) { content } }
        }
    }

    private var lensButton: some View {
        Group {
            if showLens {
                CircleTool(
                    systemName: AppSymbols.lens,
                    accessibility: copy.text(model.prefs.frontCamera ? "cd_switch_to_back" : "cd_switch_to_front")
                ) {
                    model.setFrontCamera(!model.prefs.frontCamera)
                }
            } else {
                Color.clear.frame(width: 52, height: 52)
            }
        }
    }

    private var thumbnail: some View {
        Button { model.galleryOpen = true } label: {
            Group {
                if let photo = model.photos.first {
                    LibraryThumb(photo: photo)
                } else {
                    Image(systemName: AppSymbols.photos)
                        .foregroundStyle(Color(hex: 0xB7B7B2))
                }
            }
            .frame(width: 52, height: 52)
            .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(copy.text("cd_last_photo"))
    }

    private var permission: some View {
        VStack(spacing: 10) {
            Text(copy.text("camera_permission_title")).font(Outfit.semibold(20)).foregroundStyle(.white).multilineTextAlignment(.center)
            Text(copy.text("camera_permission_body")).font(Outfit.regular(14)).foregroundStyle(Color(hex: 0xB7B7B2)).multilineTextAlignment(.center)
            Button(copy.text("camera_permission_action")) { Task { await model.requestCamera() } }
                .font(Outfit.semibold(15))
                .foregroundStyle(.black)
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .background(Color.white, in: Capsule())
                .buttonStyle(.plain)
            if failed {
                Text(copy.text("error_camera_open")).font(Outfit.regular(13)).foregroundStyle(Color(hex: 0xE07A5F))
            }
        }
    }

    private var guidanceBanner: some View {
        Group {
            if let guidance = model.guidance {
                Button(guidance.opensSettings ? copy.text("prompt_location_disabled_action") : copy.text("prompt_location_permission_action")) {
                    if guidance.opensSettings { model.openSettings() } else { model.requestLocation() }
                }
                .font(Outfit.medium(13))
                .foregroundStyle(.black)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color(hex: 0xF4F1EA), in: Capsule())
                .buttonStyle(.plain)
            }
        }
    }

    private func configure() {
        if model.preview != nil {
            backAvailable = true
            frontAvailable = true
            if model.prefs.frontCamera {
                zoomMin = 1
                zoomMax = 4
            } else {
                zoomMin = 0.5
                zoomMax = 8
            }
            zoom = CameraZoom.clamp(zoom == 0 ? 1 : zoom, zoomMin, zoomMax)
            pinchBase = zoom
            exposureSupported = true
            exposureMin = -2
            exposureMax = 2
            return
        }
        let available = LensProbe.availability()
        backAvailable = available.back
        frontAvailable = available.front
        let resolved = CameraLenses.resolve(model.prefs.frontCamera ? .front : .back, back: available.back, front: available.front)
        if (resolved == .front) != model.prefs.frontCamera {
            model.setFrontCamera(resolved == .front)
            return
        }
        guard model.cameraGranted else { return }
        session.start(front: resolved == .front) { result in
            switch result {
            case .success(let caps):
                zoomMin = caps.zoomMin
                zoomMax = max(caps.zoomMax, caps.zoomMin)
                zoom = CameraZoom.clamp(1, zoomMin, zoomMax)
                pinchBase = zoom
                exposureSupported = caps.exposureSupported
                exposureMin = caps.exposureMin
                exposureMax = caps.exposureMax
                failed = false
                session.setZoom(zoom)
            case .failure:
                failed = true
            }
        }
    }

    private func applyZoom(_ ratio: Float) {
        zoom = CameraZoom.clamp(ratio, zoomMin, zoomMax)
        pinchBase = zoom
        session.setZoom(zoom)
    }

    private func setExposure(_ value: Float) {
        exposure = min(max(value, exposureMin), exposureMax)
        session.setExposure(exposure)
    }

    private func shoot() {
        guard !busy else { return }
        busy = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        let flash = model.prefs.frontCamera && model.prefs.screenFlash
        Task {
            var restore: CGFloat?
            if flash {
                flashCover = true
                restore = UIScreen.main.brightness
                UIScreen.main.brightness = 1
                try? await Task.sleep(nanoseconds: 160_000_000)
            }
            if model.preview != nil {
                await finish(image: renderMock(), restore: restore)
                return
            }
            session.capture { result in
                Task { @MainActor in
                    switch result {
                    case .success(let image):
                        await finish(image: image, restore: restore)
                    case .failure:
                        model.reportSave("error_camera_open")
                        if let restore { UIScreen.main.brightness = restore }
                        flashCover = false
                        busy = false
                    }
                }
            }
        }
    }

    private func finish(image: UIImage, restore: CGFloat?) async {
        let content = model.watermarkContent()
        let stamped = WatermarkRenderer.render(photo: image, content: content, style: model.prefs.watermarkStyle, anchor: model.prefs.captionAnchor)
        if let data = PhotoJPEG.data(image: stamped, tags: model.captureMetadata()) {
            if model.preview == nil {
                do {
                    _ = try await PhotoStore.save(data)
                    model.refreshAlbum()
                    model.reportSave("save_success")
                } catch {
                    model.reportSave("error_gallery_write")
                }
            } else {
                model.reportSave("save_success")
            }
        } else {
            model.reportSave("error_photo_compress")
        }
        if let restore { UIScreen.main.brightness = restore }
        flashCover = false
        busy = false
    }

    private func renderMock() -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 1200, height: model.prefs.frontCamera ? 1600 : 1600))
        return renderer.image { context in
            UIColor(red: 0.15, green: 0.28, blue: 0.42, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1200, height: 1600))
        }
    }
}

private struct LibraryThumb: View {
    var photo: LibraryPhoto
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Color.clear
            }
        }
        .clipped()
        .task {
            image = await PhotoStore.image(for: photo.asset, target: CGSize(width: 120, height: 120))
        }
    }
}
