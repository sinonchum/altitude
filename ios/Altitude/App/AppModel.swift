import AVFoundation
import CoreLocation
import CoreMotion
import Observation
import Photos
import PhotosUI
import SwiftUI
import UIKit

struct PreviewLaunch {
    enum Kind { case display, settings, camera, photoImport }
    var kind: Kind
    var language: AppLanguage
    var front: Bool
    var flash: Bool
    var fixture: String

    var place: String {
        switch language {
        case .chinese: return "\u{65E5}\u{5580}\u{5219}"
        case .french: return "Shigats\u{00E9}"
        default: return "Shigatse"
        }
    }

    static func parse(_ arguments: [String] = ProcessInfo.processInfo.arguments) -> PreviewLaunch? {
        guard let index = arguments.firstIndex(of: "-uiPreview"), index + 1 < arguments.count else { return nil }
        let kind: Kind
        switch arguments[index + 1] {
        case "settings": kind = .settings
        case "camera": kind = .camera
        case "import": kind = .photoImport
        default: kind = .display
        }
        let language = Self.flag(arguments, "-uiLang").flatMap { tag -> AppLanguage? in
            if tag.hasPrefix("zh") { return .chinese }
            if tag.hasPrefix("fr") { return .french }
            if tag.hasPrefix("en") { return .english }
            return nil
        } ?? .english
        return PreviewLaunch(
            kind: kind,
            language: language,
            front: Self.flag(arguments, "-uiLens") == "front",
            flash: Self.flag(arguments, "-uiFlash") == "1",
            fixture: Self.flag(arguments, "-uiImport") ?? "portrait"
        )
    }

    private static func flag(_ arguments: [String], _ name: String) -> String? {
        guard let index = arguments.firstIndex(of: name), index + 1 < arguments.count else { return nil }
        return arguments[index + 1]
    }
}

enum LocationGuidance {
    case permission
    case approximate
    case servicesOff
    case gpsOff

    var opensSettings: Bool { self == .servicesOff || self == .gpsOff }
}

@MainActor
@Observable
final class AppModel: NSObject, CLLocationManagerDelegate {
    var reading = FusionOutput.empty()
    var latitude: Double?
    var longitude: Double?
    var placeName: String?
    var prefs = PrefsSnapshot.default
    var mode: AppMode = .display
    var saveMessage: String?
    var settingsOpen = false
    var locationGranted = false
    var locationPrecise = false
    var cameraGranted = false
    var locationEnabled = true
    var gpsEnabled = true
    var photos: [LibraryPhoto] = []
    var galleryOpen = false
    var hasBarometer = false
    var lastKnown: LastKnown?
    var nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    var geoidReady = false
    var language: AppLanguage = .system
    var deleteCandidate: LibraryPhoto?
    var importPickerPresented = false
    var importSelection: [PhotosPickerItem] = []
    var importOpen = false
    var importPages: [ImportPage] = []
    var importIndex = 0
    var importReading = false
    var importSaving = false
    var importSavedCount = 0
    var importSaveTotal = 0
    var importNeedsAccess = false
    var importLimited = false

    let preview: PreviewLaunch?
    private let store = PreferenceStore()
    private let fusion = AltitudeFusion()
    private let places = PlaceDirectory()
    private let elevations = ElevationDirectory()
    private let location = CLLocationManager()
    private let altimeter = CMAltimeter()
    private var geoid: GeoidModel?
    private var sample: GnssSample?
    private var pressureHpa: Double?
    private var savedBrightness: CGFloat?
    private var ticker: Timer?
    private var placeTask: Task<Void, Never>?
    private var placeCell: String?
    private var lastPersist: Int64 = 0
    private var saveToken = 0

    var copy: Copy { Copy(language: language) }

    var guidance: LocationGuidance? {
        if !locationGranted { return .permission }
        if !locationPrecise { return .approximate }
        if !locationEnabled { return .servicesOff }
        if !gpsEnabled { return .gpsOff }
        return nil
    }

    override init() {
        preview = PreviewLaunch.parse()
        super.init()
        prefs = store.load()
        language = store.loadLanguage()
        lastKnown = store.loadLastKnown()
        if let preview {
            applyPreview(preview)
        }
        location.delegate = self
        location.desiredAccuracy = kCLLocationAccuracyBest
        location.distanceFilter = kCLDistanceFilterNone
    }

    func start() {
        UIApplication.shared.isIdleTimerDisabled = true
        applyBrightness()
        guard preview == nil else { return }
        refreshPermissions()
        loadGeoid()
        startBarometer()
        ticker = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.stepFusion()
            }
        }
        ensureLocation()
        refreshAlbum()
    }

    func setMode(_ mode: AppMode) { self.mode = mode }

    func toggleUnit() {
        prefs.unit = prefs.unit == .meters ? .feet : .meters
        persist()
    }

    func setUnit(_ unit: AltitudeUnit) { prefs.unit = unit; persist() }
    func setTheme(_ theme: DisplayTheme) { prefs.theme = theme; persist() }
    func setMaxBrightness(_ enabled: Bool) { prefs.maxBrightness = enabled; persist(); applyBrightness() }
    func setShowCoordinates(_ enabled: Bool) { prefs.showCoordinates = enabled; persist() }
    func setShowAccuracy(_ enabled: Bool) { prefs.showAccuracy = enabled; persist() }
    func setShowClock(_ enabled: Bool) { prefs.showClock = enabled; persist() }
    func setShowPlace(_ enabled: Bool) { prefs.showPlace = enabled; persist() }
    func setDynamicColor(_ enabled: Bool) { prefs.dynamicColor = enabled; persist() }
    func setWatermark(_ style: WatermarkStyle) { prefs.watermarkStyle = style; persist() }
    func setAnchor(_ anchor: CaptionAnchor) { prefs.captionAnchor = anchor; persist() }
    func setFrontCamera(_ front: Bool) { prefs.frontCamera = front; persist() }
    func setScreenFlash(_ enabled: Bool) { prefs.screenFlash = enabled; persist() }

    func setLanguage(_ language: AppLanguage) {
        self.language = language
        store.saveLanguage(language)
        placeCell = nil
        schedulePlaceLookup(force: true)
    }

    func completeOnboarding() {
        prefs.onboardingComplete = true
        persist()
    }

    func requestLocation() {
        refreshPermissions()
        if location.authorizationStatus == .notDetermined {
            location.requestWhenInUseAuthorization()
            return
        }
        if location.accuracyAuthorization == .reducedAccuracy {
            location.requestTemporaryFullAccuracyAuthorization(withPurposeKey: "AltitudeReading") { [weak self] _ in
                Task { @MainActor in self?.refreshPermissions() }
            }
            return
        }
        openSettings()
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    func requestCamera() async {
        let granted = await AVCaptureDevice.requestAccess(for: .video)
        cameraGranted = granted
    }

    func refreshPermissions() {
        let status = location.authorizationStatus
        locationGranted = status == .authorizedAlways || status == .authorizedWhenInUse
        locationPrecise = location.accuracyAuthorization == .fullAccuracy
        locationEnabled = CLLocationManager.locationServicesEnabled()
        gpsEnabled = locationEnabled
        cameraGranted = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
        ensureLocation()
    }

    func refreshAlbum() {
        guard preview == nil else { return }
        photos = PhotoStore.list()
    }

    func beginImport() async {
        guard preview == nil else { return }
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        switch status {
        case .authorized:
            importLimited = false
            importNeedsAccess = false
            importPickerPresented = true
        case .limited:
            importLimited = true
            importNeedsAccess = false
            importPickerPresented = true
        case .notDetermined:
            let resolved = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
            importLimited = resolved == .limited
            if resolved == .authorized || resolved == .limited {
                importNeedsAccess = false
                importPickerPresented = true
            } else {
                importNeedsAccess = true
                importOpen = true
            }
        default:
            importNeedsAccess = true
            importOpen = true
        }
    }

    func closeImport() {
        importOpen = false
        importNeedsAccess = false
        importReading = false
        importSaving = false
        importPages = []
        importIndex = 0
        importSelection = []
    }

    func loadImport(_ items: [PhotosPickerItem]) async {
        let chosen = Array(items.prefix(importSelectionLimit))
        guard !chosen.isEmpty else { return }
        importNeedsAccess = false
        importOpen = true
        importReading = true
        importIndex = 0
        importPages = []
        var built: [ImportPage] = []
        for (offset, item) in chosen.enumerated() {
            let facts = await PhotoImporter.facts(for: item)
            let identifier = item.itemIdentifier ?? "item-\(offset)"
            if let page = PhotoImporter.makePage(
                id: "\(offset)-\(identifier)",
                data: facts.data,
                assetLatitude: facts.latitude,
                assetLongitude: facts.longitude,
                assetWasReadable: facts.readable
            ) {
                built.append(page)
            }
        }
        importPages = built
        await resolveImportLookups()
        importReading = false
    }

    func saveImport(all: Bool) async {
        guard !importSaving else { return }
        let pages: [ImportPage]
        if all {
            pages = importPages
        } else if importPages.indices.contains(importIndex) {
            pages = [importPages[importIndex]]
        } else if let page = importPages.first {
            pages = [page]
        } else {
            return
        }
        importSaving = true
        importSavedCount = 0
        importSaveTotal = pages.count
        var failed = false
        for page in pages {
            if let data = framedJPEG(page) {
                do {
                    _ = try await PhotoStore.save(data)
                    importSavedCount += 1
                } catch {
                    failed = true
                }
            } else {
                failed = true
            }
        }
        importSaving = false
        refreshAlbum()
        if failed {
            reportSave("error_gallery_write")
        }
    }

    private func resolveImportLookups() async {
        let pending = importPages.enumerated().compactMap { index, page -> (Int, Double, Double)? in
            guard page.altitude == nil, let latitude = page.capture.latitude, let longitude = page.capture.longitude else { return nil }
            return (index, latitude, longitude)
        }
        if !pending.isEmpty {
            let heights = await elevations.elevations(pending.map { ($0.1, $0.2) })
            for (slot, item) in pending.enumerated() {
                let lookup = heights.indices.contains(slot) ? heights[slot] : nil
                importPages[item.0].altitude = selectPhotoAltitude(
                    exifAltitudeMeters: importPages[item.0].capture.exifAltitudeMeters,
                    elevationLookupMeters: lookup,
                    hasCoordinates: true
                )
            }
        }
        guard prefs.showPlace else { return }
        for index in importPages.indices {
            guard let latitude = importPages[index].capture.latitude, let longitude = importPages[index].capture.longitude else { continue }
            importPages[index].place = await places.resolve(latitude: latitude, longitude: longitude, language: language)
        }
    }

    private func framedJPEG(_ page: ImportPage) -> Data? {
        guard let image = UIImage(data: page.source) else { return nil }
        let scaled = image.upright(maxEdge: PhotoImporter.saveEdge)
        let content = copy.importWatermark(
            capture: page.capture,
            altitude: page.altitude,
            place: page.place,
            showPlace: prefs.showPlace,
            unit: prefs.unit
        )
        let rendered = WatermarkRenderer.render(
            photo: scaled,
            content: content,
            style: prefs.watermarkStyle,
            anchor: prefs.captionAnchor
        )
        let clock = page.capture.clock
        return PhotoJPEG.data(
            image: rendered,
            tags: PhotoJPEG.Tags(
                latitude: page.capture.latitude,
                longitude: page.capture.longitude,
                altitudeMeters: page.altitude?.meters,
                epochMs: nowMillis,
                dateTimeOriginal: clock.map { formatExifWallClock($0.local) },
                offsetTimeOriginal: clock?.offset.map(formatExifOffset)
            )
        )
    }

    func reportSave(_ key: String) {
        saveMessage = copy.text(key)
        saveToken += 1
        let token = saveToken
        Task {
            try? await Task.sleep(nanoseconds: 2_800_000_000)
            if saveToken == token { saveMessage = nil }
        }
    }

    func deletePhoto(_ photo: LibraryPhoto) async -> Bool {
        let removed = await PhotoStore.delete(photo.id)
        if removed {
            photos.removeAll { $0.id == photo.id }
            if photos.isEmpty { galleryOpen = false }
        } else {
            reportSave("error_delete_failed")
        }
        return removed
    }

    func watermarkContent() -> WatermarkContent {
        copy.watermark(
            altitudeMeters: reading.altitudeMslMeters,
            absoluteMsl: reading.absoluteMsl,
            unit: prefs.unit,
            latitude: latitude,
            longitude: longitude,
            place: prefs.showPlace ? placeName : nil,
            epochMs: nowMillis
        )
    }

    func captureMetadata() -> PhotoJPEG.Tags {
        let absolute = reading.absoluteMsl ? reading.altitudeMslMeters : nil
        return PhotoJPEG.Tags(
            latitude: latitude,
            longitude: longitude,
            altitudeMeters: absolute,
            epochMs: nowMillis
        )
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            self.refreshPermissions()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        let vertical = location.verticalAccuracy
        let hasVertical = vertical >= 0
        let fix = GnssSample(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            ellipsoidalAltitudeMeters: hasVertical ? location.ellipsoidalAltitude : nil,
            platformMslAltitudeMeters: hasVertical ? location.altitude : nil,
            horizontalAccuracyMeters: location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil,
            verticalAccuracyMeters: hasVertical ? vertical : nil,
            timeMs: Int64(location.timestamp.timeIntervalSince1970 * 1000),
            elapsedRealtimeMs: 0
        )
        Task { @MainActor in
            self.sample = fix
            self.schedulePlaceLookup(force: false)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}

    private func applyPreview(_ preview: PreviewLaunch) {
        language = preview.language
        prefs.onboardingComplete = true
        prefs.frontCamera = preview.front
        prefs.screenFlash = preview.flash
        prefs.showCoordinates = true
        prefs.showAccuracy = true
        prefs.showClock = true
        prefs.showPlace = true
        locationGranted = true
        locationPrecise = true
        locationEnabled = true
        gpsEnabled = true
        cameraGranted = true
        geoidReady = true
        hasBarometer = true
        placeName = preview.place
        latitude = 29.65218
        longitude = 91.11700
        nowMillis = epochMillis(
            year: 2026, month: 10, day: 22, hour: 16, minute: 42, second: 8,
            zone: TimeZone(identifier: "Asia/Shanghai") ?? .current
        )
        reading = FusionOutput(
            altitudeMslMeters: 5210,
            accuracyMeters: 4,
            source: .fused,
            quality: .excellent,
            absoluteMsl: true,
            barometerCalibrated: true,
            calibrationOffsetMeters: nil
        )
        mode = preview.kind == .camera || preview.kind == .photoImport ? .camera : .display
        settingsOpen = preview.kind == .settings
        importOpen = preview.kind == .photoImport
        if preview.kind == .photoImport {
            importPages = [ImportFixtures.page(kind: preview.fixture, place: preview.place)]
            importIndex = 0
            importReading = false
        }
    }

    private func persist() { store.save(prefs) }

    private func applyBrightness() {
        if prefs.maxBrightness {
            if savedBrightness == nil { savedBrightness = UIScreen.main.brightness }
            UIScreen.main.brightness = 1
        } else if let savedBrightness {
            UIScreen.main.brightness = savedBrightness
            self.savedBrightness = nil
        }
    }

    private func loadGeoid() {
        Task.detached { [weak self] in
            guard let url = Bundle.main.url(forResource: "egm96-15", withExtension: "bin"),
                  let data = try? Data(contentsOf: url),
                  let model = try? Egm96Geoid.decode(data) else { return }
            await MainActor.run {
                self?.geoid = model
                self?.geoidReady = true
            }
        }
    }

    private func startBarometer() {
        guard CMAltimeter.isRelativeAltitudeAvailable() else { return }
        hasBarometer = true
        altimeter.startRelativeAltitudeUpdates(to: .main) { [weak self] data, _ in
            guard let kilopascals = data?.pressure.doubleValue else { return }
            Task { @MainActor in
                self?.pressureHpa = kilopascals * 10
            }
        }
    }

    private func ensureLocation() {
        guard preview == nil, locationGranted, locationEnabled else {
            location.stopUpdatingLocation()
            return
        }
        location.startUpdatingLocation()
    }

    private func stepFusion() {
        let elapsed = Int64(ProcessInfo.processInfo.systemUptime * 1000)
        let wall = Int64(Date().timeIntervalSince1970 * 1000)
        nowMillis = wall
        let current = sample
        let msl = current.flatMap { resolveMsl($0, geoid) }
        let fresh = current != nil && msl != nil && FixClock.accept(
            FixClock.ageMillis(
                elapsedRealtimeMs: current?.elapsedRealtimeMs ?? 0,
                wallTimeMs: current?.timeMs ?? 0,
                nowElapsedMs: elapsed,
                nowWallMs: wall
            ),
            FixClock.fusionWindowMs
        )
        let output = fusion.update(FusionInput(
            timestampMs: elapsed,
            gpsMslMeters: fresh ? msl : nil,
            gpsVerticalAccuracyMeters: fresh ? current?.verticalAccuracyMeters : nil,
            pressureHpa: pressureHpa
        ))
        reading = output
        let coordinatesFresh = current != nil && FixClock.accept(
            FixClock.ageMillis(
                elapsedRealtimeMs: current?.elapsedRealtimeMs ?? 0,
                wallTimeMs: current?.timeMs ?? 0,
                nowElapsedMs: elapsed,
                nowWallMs: wall
            ),
            FixClock.coordinateWindowMs
        )
        if coordinatesFresh, let current {
            latitude = current.latitude
            longitude = current.longitude
        } else if !coordinatesFresh {
            latitude = nil
            longitude = nil
        }
        if output.absoluteMsl, let altitude = output.altitudeMslMeters, let current, elapsed - lastPersist > 5_000 {
            lastPersist = elapsed
            let known = LastKnown(altitudeMeters: altitude, timeMs: wall, latitude: current.latitude, longitude: current.longitude)
            store.saveLastKnown(known)
            lastKnown = known
        }
    }

    private func schedulePlaceLookup(force: Bool) {
        guard let latitude, let longitude else { return }
        let cell = "\(Int((latitude / 0.05).rounded())):\(Int((longitude / 0.05).rounded())):\(language.nominatimTag)"
        if !force && cell == placeCell { return }
        placeCell = cell
        let language = language
        let lat = (Double(Int((latitude / 0.05).rounded())) * 0.05)
        let lon = (Double(Int((longitude / 0.05).rounded())) * 0.05)
        placeTask?.cancel()
        placeTask = Task { [places] in
            let name = await places.resolve(latitude: lat, longitude: lon, language: language)
            if Task.isCancelled { return }
            await MainActor.run {
                if let name, !name.isEmpty { self.placeName = name }
            }
        }
    }
}
