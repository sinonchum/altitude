import UIKit
import XCTest

final class ScreenshotUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testDisplayEnglish() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = launch(preview: "display", language: "en")
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(element(app, "screen.display").waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["ALTITUDE"].waitForExistence(timeout: 5))
        save("display-en")
    }

    func testSettingsEnglish() throws {
        try assertSettings(language: "en", file: "settings-en")
    }

    func testSettingsChinese() throws {
        try assertSettings(language: "zh", file: "settings-zh")
    }

    func testSettingsFrench() throws {
        try assertSettings(language: "fr", file: "settings-fr")
    }

    func testCameraPortraitBack() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = launch(preview: "camera", language: "en", lens: "back")
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(element(app, "camera.preview").waitForExistence(timeout: 20))
        XCTAssertTrue(element(app, "shutter").waitForExistence(timeout: 5))
        XCTAssertFalse(element(app, "screen.flash").exists)
        save("camera-portrait-back")
    }

    func testCameraPortraitFront() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = launch(preview: "camera", language: "en", lens: "front", flash: true)
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(element(app, "camera.preview").waitForExistence(timeout: 20))
        XCTAssertTrue(element(app, "screen.flash").waitForExistence(timeout: 5))
        XCTAssertTrue(element(app, "shutter").exists)
        save("camera-portrait-front")
    }

    func testCameraLandscapeBack() throws {
        try assertLandscape(lens: "back", flash: false, file: "camera-landscape-back")
    }

    func testCameraLandscapeFront() throws {
        try assertLandscape(lens: "front", flash: true, file: "camera-landscape-front")
    }

    private func assertSettings(language: String, file: String) throws {
        XCUIDevice.shared.orientation = .portrait
        let app = launch(preview: "settings", language: language)
        XCUIDevice.shared.orientation = .portrait
        let version = element(app, "settings.version")
        let versionText = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "1.1.0")).firstMatch
        let appeared = version.waitForExistence(timeout: 20) || versionText.waitForExistence(timeout: 5)
        if !appeared {
            XCTFail("settings version missing\n\(String(app.debugDescription.prefix(3500)))")
            return
        }
        let shown = version.exists ? version.label : versionText.label
        XCTAssertTrue(shown.contains("1.1.0"), shown)
        XCTAssertTrue(elementLabeled(app, "English").waitForExistence(timeout: 8), String(app.debugDescription.prefix(2500)))
        XCTAssertTrue(elementLabeled(app, "中文").exists)
        XCTAssertTrue(elementLabeled(app, "Français").exists)
        save(file)
    }

    private func assertLandscape(lens: String, flash: Bool, file: String) throws {
        // Rotate after launch. Turning the simulator before the first launch makes Xcode time out attaching.
        let app = launch(preview: "camera", language: "en", lens: lens, flash: flash)
        XCUIDevice.shared.orientation = .landscapeLeft
        let preview = element(app, "camera.preview")
        let window = app.windows.firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 20), String(app.debugDescription.prefix(2000)))
        let laidOut = expectation(for: NSPredicate { _, _ in
            guard preview.exists, window.exists else { return false }
            let previewFrame = preview.frame
            let windowFrame = window.frame
            return windowFrame.width > windowFrame.height && previewFrame.height > 40 && previewFrame.width > 40
        }, evaluatedWith: nil, handler: nil)
        wait(for: [laidOut], timeout: 12)
        let windowFrame = window.frame
        let fraction = preview.frame.height / max(windowFrame.height, 1)
        XCTAssertGreaterThanOrEqual(
            fraction,
            0.70,
            "preview \(preview.frame) window \(windowFrame)"
        )
        if flash {
            XCTAssertTrue(element(app, "screen.flash").exists)
        }
        XCTAssertTrue(element(app, "shutter").exists)
        save(file)
    }

    func testImportEntryPortrait() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = launch(preview: "camera", language: "en")
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(element(app, "import.photo").waitForExistence(timeout: 20))
        XCTAssertTrue(element(app, "camera.preview").exists)
        save("import-entry")
    }

    func testImportEntryLandscape() throws {
        let app = launch(preview: "camera", language: "en")
        XCUIDevice.shared.orientation = .landscapeLeft
        let button = element(app, "import.photo")
        XCTAssertTrue(button.waitForExistence(timeout: 20))
        let window = app.windows.firstMatch
        let laidOut = expectation(for: NSPredicate { _, _ in
            guard button.exists, window.exists else { return false }
            return window.frame.width > window.frame.height && button.frame.width > 20
        }, evaluatedWith: nil, handler: nil)
        wait(for: [laidOut], timeout: 12)
        save("import-entry-landscape")
    }

    func testImportPortraitPhotoPortrait() throws {
        try assertImport(fixture: "portrait", language: "en", landscape: false, file: "import-portrait-photo")
    }

    func testImportPortraitPhotoLandscape() throws {
        try assertImport(fixture: "portrait", language: "en", landscape: true, file: "import-portrait-photo-landscape")
    }

    func testImportLandscapePhotoPortrait() throws {
        try assertImport(fixture: "landscape", language: "en", landscape: false, file: "import-landscape-photo")
    }

    func testImportLandscapePhotoLandscape() throws {
        try assertImport(fixture: "landscape", language: "en", landscape: true, file: "import-landscape-photo-landscape")
    }

    func testImportNoGpsPortrait() throws {
        try assertImport(fixture: "nogps", language: "en", landscape: false, file: "import-nogps", notice: "No location in this photo")
    }

    func testImportNoGpsLandscape() throws {
        try assertImport(fixture: "nogps", language: "en", landscape: true, file: "import-nogps-landscape", notice: "No location in this photo")
    }

    func testImportComputedPortrait() throws {
        try assertImport(fixture: "computed", language: "en", landscape: false, file: "import-computed", notice: "Computed from coordinates")
    }

    func testImportComputedLandscape() throws {
        try assertImport(fixture: "computed", language: "en", landscape: true, file: "import-computed-landscape", notice: "Computed from coordinates")
    }

    func testImportNoGpsChinese() throws {
        try assertImport(fixture: "nogps", language: "zh", landscape: false, file: "import-nogps-zh", notice: "这张照片没有位置")
    }

    func testImportNoGpsFrench() throws {
        try assertImport(fixture: "nogps", language: "fr", landscape: false, file: "import-nogps-fr", notice: "Cette photo n’a pas de lieu")
    }

    func testImportNoGpsFrenchLandscape() throws {
        try assertImport(fixture: "nogps", language: "fr", landscape: true, file: "import-nogps-fr-landscape", notice: "Cette photo n’a pas de lieu")
    }

    private func assertImport(fixture: String, language: String, landscape: Bool, file: String, notice: String? = nil) throws {
        let app = launch(preview: "import", language: language, fixture: fixture)
        if landscape {
            XCUIDevice.shared.orientation = .landscapeLeft
        } else {
            XCUIDevice.shared.orientation = .portrait
        }
        let preview = element(app, "import.preview")
        let window = app.windows.firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 20), String(app.debugDescription.prefix(2000)))
        let laidOut = expectation(for: NSPredicate { _, _ in
            guard preview.exists, window.exists else { return false }
            let windowFrame = window.frame
            let previewFrame = preview.frame
            let oriented = landscape ? windowFrame.width > windowFrame.height : windowFrame.height >= windowFrame.width
            return oriented && previewFrame.width > 40 && previewFrame.height > 40
        }, evaluatedWith: nil, handler: nil)
        wait(for: [laidOut], timeout: 12)
        XCTAssertTrue(preview.frame.width > 40 && preview.frame.height > 40)
        if let notice {
            let label = elementLabeled(app, notice)
            XCTAssertTrue(label.waitForExistence(timeout: 8), String(app.debugDescription.prefix(2000)))
            XCTAssertTrue(window.frame.insetBy(dx: -2, dy: -2).contains(label.frame), "notice \(label.frame) window \(window.frame)")
        }
        let anchor = elementLabeled(app, language == "zh" ? "顶部" : (language == "fr" ? "Haut" : "Top"))
        XCTAssertTrue(anchor.waitForExistence(timeout: 5))
        XCTAssertTrue(window.frame.insetBy(dx: -2, dy: -2).contains(anchor.frame), "anchor \(anchor.frame) window \(window.frame)")
        save(file)
    }

    private func launch(preview: String, language: String, lens: String = "back", flash: Bool = false, fixture: String = "portrait") -> XCUIApplication {
        let app = XCUIApplication()
        var arguments = [
            "-uiPreview", preview,
            "-uiLang", language,
            "-uiLens", lens,
            "-uiFlash", flash ? "1" : "0",
        ]
        if preview == "import" {
            arguments += ["-uiImport", fixture]
        }
        app.launchArguments = arguments
        app.launch()
        return app
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func elementLabeled(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func save(_ name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        var data = screenshot.pngRepresentation
        if let image = UIImage(data: data), image.imageOrientation != .up {
            let format = UIGraphicsImageRendererFormat()
            format.scale = image.scale
            let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
            let upright = renderer.image { _ in
                image.draw(in: CGRect(origin: .zero, size: image.size))
            }
            if let baked = upright.pngData() {
                data = baked
            }
        }
        var written: [String] = []
        for directory in screenshotDirectories() {
            do {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                let url = directory.appendingPathComponent("\(name).png")
                try data.write(to: url)
                written.append(url.path)
            } catch {
                continue
            }
        }
        XCTAssertFalse(written.isEmpty, "Could not write \(name).png")
        assertNoSystemAlert()
    }

    /// A system permission sheet means the shot does not show the screen under test. Do not tap it.
    private func assertNoSystemAlert() {
        let appAlert = XCUIApplication().alerts.firstMatch
        let systemAlert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if appAlert.exists {
            XCTFail("An alert is covering the screenshot: \(appAlert.label)")
        }
        if systemAlert.exists {
            XCTFail("A system alert is covering the screenshot: \(systemAlert.label)")
        }
    }

    private func screenshotDirectories() -> [URL] {
        var directories: [URL] = []
        if let raw = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"], !raw.isEmpty, !raw.contains("$") {
            directories.append(URL(fileURLWithPath: raw, isDirectory: true))
        }
        directories.append(
            URL(fileURLWithPath: #filePath)
                .deletingLastPathComponent()
                .deletingLastPathComponent()
                .appendingPathComponent("build/screenshots", isDirectory: true)
        )
        directories.append(
            URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
                .appendingPathComponent("altitude-screenshots", isDirectory: true)
        )
        directories.append(URL(fileURLWithPath: "/tmp/altitude-screenshots", isDirectory: true))
        return directories
    }
}
