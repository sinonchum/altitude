import XCTest

final class ScreenshotUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testDisplayEnglish() throws {
        let app = launch(preview: "display", language: "en")
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
        let app = launch(preview: "camera", language: "en", lens: "back")
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(element(app, "camera.preview").waitForExistence(timeout: 20))
        XCTAssertTrue(element(app, "shutter").waitForExistence(timeout: 5))
        XCTAssertFalse(element(app, "screen.flash").exists)
        save("camera-portrait-back")
    }

    func testCameraPortraitFront() throws {
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
        let app = launch(preview: "settings", language: language)
        let version = element(app, "settings.version")
        XCTAssertTrue(version.waitForExistence(timeout: 20))
        XCTAssertTrue(version.label.contains("1.0.0"), version.label)
        XCTAssertTrue(app.staticTexts["English"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["中文"].exists)
        XCTAssertTrue(app.staticTexts["Français"].exists)
        save(file)
    }

    private func assertLandscape(lens: String, flash: Bool, file: String) throws {
        let app = launch(preview: "camera", language: "en", lens: lens, flash: flash)
        XCUIDevice.shared.orientation = .landscapeLeft
        let stage = element(app, "camera.stage")
        let preview = element(app, "camera.preview")
        XCTAssertTrue(preview.waitForExistence(timeout: 20))
        let laidOut = expectation(for: NSPredicate { _, _ in
            stage.frame.width > stage.frame.height && preview.frame.height > 40
        }, evaluatedWith: nil)
        wait(for: [laidOut], timeout: 12)
        let fraction = preview.frame.height / max(stage.frame.height, 1)
        XCTAssertGreaterThanOrEqual(fraction, 0.70, "preview \(preview.frame) stage \(stage.frame)")
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
        let directory = screenshotDirectory()
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("\(name).png")
        try? screenshot.pngRepresentation.write(to: url)
    }

    private func screenshotDirectory() -> URL {
        if let raw = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"], !raw.isEmpty {
            return URL(fileURLWithPath: raw, isDirectory: true)
        }
        return URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("build/screenshots", isDirectory: true)
    }
}
