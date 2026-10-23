import XCTest
@testable import Altitude

/// The import chips wrap inside the width they are given. Labels are measured with the
/// same font and padding as `ChoiceChip`. Widths come from `ImportChrome`, not from a language.
final class ChipFlowTests: XCTestCase {
    private let styles = [
        "en": ["White", "Black", "Frame", "Overlay"],
        "zh": ["白底", "黑底", "画框", "浮印"],
        "fr": ["Fond blanc", "Fond noir", "Cadre", "Incrustation"],
    ]
    private let anchors = [
        "en": ["Top", "Bottom"],
        "zh": ["顶部", "底部"],
        "fr": ["Haut", "Bas"],
    ]

    override func setUp() {
        super.setUp()
        Outfit.register()
    }

    func testColumnWidthFollowsTheOfferedWidth() {
        XCTAssertEqual(ImportChrome.portraitChipWidth, 351, accuracy: 0.1)
        XCTAssertEqual(
            ImportChrome.landscapeColumnWidth(contentWidth: ImportChrome.narrowestLandscapeWidth),
            667 * 0.38,
            accuracy: 0.1
        )
        XCTAssertEqual(ImportChrome.landscapeColumnWidth(contentWidth: 100), 180, accuracy: 0.1)
        XCTAssertEqual(ImportChrome.landscapeColumnWidth(contentWidth: 874), 320, accuracy: 0.1)
    }

    func testStyleAndAnchorChipsFitPortraitAndLandscape() {
        let font = Outfit.uiSemibold(14)
        XCTAssertEqual(font.fontName, "Outfit-SemiBold", "chip measurement must use the bundled face")
        let widths = [
            ImportChrome.portraitChipWidth,
            ImportChrome.landscapeColumnWidth(contentWidth: ImportChrome.narrowestLandscapeWidth),
            ImportChrome.landscapeColumnWidth(contentWidth: 756),
            ImportChrome.landscapeColumnWidth(contentWidth: 100),
        ]
        for width in widths {
            for language in styles.keys {
                assertFits(styles[language]!, width: width, spacing: ImportChrome.chipSpacing)
                assertFits(anchors[language]!, width: width, spacing: ImportChrome.chipSpacing)
            }
        }
    }

    func testAnOverwideChipIsClampedAndTheNextChipWraps() {
        let sizes = [CGSize(width: 400, height: 32), CGSize(width: 48, height: 32)]
        let arranged = FlowArrangement.arrange(sizes, limit: 200, spacing: ImportChrome.chipSpacing)
        XCTAssertEqual(arranged.placements.count, 2)
        XCTAssertEqual(arranged.placements[0].size.width, 200, accuracy: 0.1)
        XCTAssertEqual(arranged.placements[0].x, 0, accuracy: 0.1)
        XCTAssertEqual(arranged.placements[1].x, 0, accuracy: 0.1)
        XCTAssertGreaterThan(arranged.placements[1].y, arranged.placements[0].y)
        XCTAssertLessThanOrEqual(arranged.placements[1].x + arranged.placements[1].size.width, 200.5)
    }

    private func assertFits(_ titles: [String], width: CGFloat, spacing: CGFloat) {
        let sizes = titles.map { chipSize($0) }
        let arranged = FlowArrangement.arrange(sizes, limit: width, spacing: spacing)
        XCTAssertEqual(arranged.placements.count, titles.count, titles.joined(separator: ", "))
        let total = sizes.reduce(CGFloat(0)) { $0 + $1.width } + spacing * CGFloat(max(titles.count - 1, 0))
        if total > width + 0.5 {
            let rows = Set(arranged.placements.map(\.y))
            XCTAssertGreaterThan(rows.count, 1, "\(titles) at \(width)")
        }
        for (title, place) in zip(titles, arranged.placements) {
            XCTAssertGreaterThanOrEqual(place.x, 0, title)
            XCTAssertLessThanOrEqual(place.x + place.size.width, width + 0.5, "\(title) at \(width)")
            let ideal = chipSize(title)
            if ideal.width <= width + 0.5 {
                XCTAssertEqual(place.size.width, ideal.width, accuracy: 0.5, "\(title) should keep its full label")
            } else {
                XCTAssertLessThanOrEqual(place.size.width, width + 0.5, title)
            }
        }
    }

    /// Horizontal padding 14 and vertical padding 8 match `ChoiceChip`.
    private func chipSize(_ title: String) -> CGSize {
        let font = Outfit.uiSemibold(14)
        let rect = (title as NSString).boundingRect(
            with: CGSize(width: 10_000, height: 10_000),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font],
            context: nil
        )
        return CGSize(width: ceil(rect.width) + 28, height: ceil(rect.height) + 16)
    }
}
