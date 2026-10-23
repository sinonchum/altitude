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
        let labeled = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "1.0.0")).firstMatch
        let appeared = version.waitForExistence(timeout: 20) || labeled.waitForExistence(timeout: 5)
        if !appeared {
            XCTFail("settings version missing\n\(String(app.debugDescription.prefix(3500)))")
            return
        }
        let shown = version.exists ? version.label : labeled.label
        XCTAssertTrue(shown.contains("1.0.0"), shown)
        XCTAssertTrue(app.staticTexts["English"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["中文"].exists)
        XCTAssertTrue(app.staticTexts["Français"].exists)
        save(file)
    }

    private func assertLandscape(lens: String, flash: Bool, file: String) throws {
        XCUIDevice.shared.orientation = .landscapeLeft
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

    private func launch(preview: String, language: String, lens: String = "back", flash: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-uiPreview", preview,
            "-uiLang", language,
            "-uiLens", lens,
            "-uiFlash", flash ? "1" : "0",
        ]
        app.launch()
        return app
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func save(_ name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        let data = screenshot.pngRepresentation
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
