import SwiftUI
import UIKit
import CoreText

struct Palette {
    var background: Color
    var primary: Color
    var secondary: Color
    var accent: Color

    static func of(_ theme: DisplayTheme) -> Palette {
        switch theme {
        case .night:
            return Palette(background: Color(hex: 0x090A0C), primary: Color(hex: 0xF3F0E8), secondary: Color(hex: 0x9A958C), accent: Color(hex: 0xE10600))
        case .void:
            return Palette(background: .black, primary: .white, secondary: Color(hex: 0xC8C8C8), accent: Color(hex: 0xE10600))
        case .glacier:
            return Palette(background: Color(hex: 0x071018), primary: Color(hex: 0xEAF6FF), secondary: Color(hex: 0x8AA4B5), accent: Color(hex: 0x7ED0FF))
        case .saffron:
            return Palette(background: Color(hex: 0x100E0A), primary: Color(hex: 0xF6C453), secondary: Color(hex: 0xCDB892), accent: Color(hex: 0xE10600))
        case .snow:
            return Palette(background: Color(hex: 0xF4F2EC), primary: Color(hex: 0x141210), secondary: Color(hex: 0x5C574E), accent: Color(hex: 0xE10600))
        }
    }
}

func qualityColor(_ quality: FixQuality) -> Color {
    switch quality {
    case .excellent: return Color(hex: 0x7DDEA0)
    case .good: return Color(hex: 0xC5D67A)
    case .fair: return Color(hex: 0xE6B15A)
    case .poor: return Color(hex: 0xE07A5F)
    case .searching, .none: return Color(hex: 0x9A958C)
    }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

enum Outfit {
    static func register() {
        let files = [
            "outfit_regular",
            "outfit_medium",
            "outfit_semibold",
            "outfit_bold",
            "outfit_extrabold",
        ]
        for file in files {
            let url = Bundle.main.url(forResource: file, withExtension: "ttf", subdirectory: "Fonts")
                ?? Bundle.main.url(forResource: file, withExtension: "ttf")
            guard let url else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    static func font(_ name: String, _ size: CGFloat, fallback: Font.Weight) -> Font {
        if UIFont(name: name, size: size) != nil {
            return .custom(name, size: size)
        }
        return .system(size: size, weight: fallback)
    }

    static func uiFont(_ name: String, _ size: CGFloat, fallback: UIFont.Weight) -> UIFont {
        UIFont(name: name, size: size) ?? .systemFont(ofSize: size, weight: fallback)
    }

    static func regular(_ size: CGFloat) -> Font { font("Outfit-Regular", size, fallback: .regular) }
    static func medium(_ size: CGFloat) -> Font { font("Outfit-Medium", size, fallback: .medium) }
    static func semibold(_ size: CGFloat) -> Font { font("Outfit-SemiBold", size, fallback: .semibold) }
    static func extraBold(_ size: CGFloat) -> Font { font("Outfit-ExtraBold", size, fallback: .heavy) }

    static func uiRegular(_ size: CGFloat) -> UIFont { uiFont("Outfit-Regular", size, fallback: .regular) }
    static func uiMedium(_ size: CGFloat) -> UIFont { uiFont("Outfit-Medium", size, fallback: .medium) }
    static func uiSemibold(_ size: CGFloat) -> UIFont { uiFont("Outfit-SemiBold", size, fallback: .semibold) }
}

enum AppSymbols {
    static let settings = "gearshape"
    static let brightness = "sun.max"
    static let flashOn = "bolt.fill"
    static let flashOff = "bolt.slash"
    static let lens = "arrow.triangle.2.circlepath.camera"
    static let share = "square.and.arrow.up"
    static let trash = "trash"
    static let photos = "photo.on.rectangle"
    static let close = "xmark"
}
