import SwiftUI
import UIKit

enum WatermarkColors {
    static let red = UIColor(red: 225 / 255, green: 6 / 255, blue: 0, alpha: 1)
    static let lightBackground = UIColor.white
    static let darkBackground = UIColor(red: 10 / 255, green: 10 / 255, blue: 10 / 255, alpha: 1)
    static let ink = UIColor(red: 18 / 255, green: 18 / 255, blue: 18 / 255, alpha: 1)
    static let paper = UIColor(red: 246 / 255, green: 244 / 255, blue: 239 / 255, alpha: 1)
    static let muteDark = UIColor(red: 92 / 255, green: 92 / 255, blue: 88 / 255, alpha: 1)
    static let muteLight = UIColor(red: 194 / 255, green: 194 / 255, blue: 188 / 255, alpha: 1)
}

enum WatermarkRenderer {
    static func render(photo: UIImage, content: WatermarkContent, style: WatermarkStyle, anchor: CaptionAnchor) -> UIImage {
        let upright = photo.normalizedUp()
        let size = WatermarkMetrics.outputSize(
            photoWidth: Int(upright.size.width),
            photoHeight: Int(upright.size.height),
            style: style,
            hasPlace: content.place != nil
        )
        let metric = CGFloat(min(upright.size.width, upright.size.height))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size.width, height: size.height), format: format)
        return renderer.image { context in
            let canvas = context.cgContext
            switch style {
            case .caption:
                upright.draw(at: .zero)
                drawCaption(canvas, width: CGFloat(size.width), height: CGFloat(size.height), content: content, anchor: anchor)
            case .plateLight, .plateDark:
                upright.draw(at: .zero)
                let plate = CGRect(x: 0, y: upright.size.height, width: upright.size.width, height: CGFloat(size.height) - upright.size.height)
                drawPlate(canvas, rect: plate, content: content, dark: style == .plateDark, metric: metric)
            case .mount:
                canvas.setFillColor(WatermarkColors.lightBackground.cgColor)
                canvas.fill(CGRect(x: 0, y: 0, width: size.width, height: size.height))
                let reference = min(Int(upright.size.width), Int(upright.size.height))
                let side = CGFloat(WatermarkMetrics.scaledPx(reference, WatermarkMetrics.mountSideThousandths))
                let top = CGFloat(WatermarkMetrics.scaledPx(reference, WatermarkMetrics.mountTopThousandths))
                upright.draw(at: CGPoint(x: side, y: top))
                let bottom = CGRect(
                    x: side,
                    y: top + upright.size.height,
                    width: CGFloat(size.width) - side * 2,
                    height: CGFloat(size.height) - (top + upright.size.height)
                )
                drawBottomBand(canvas, rect: bottom, metric: metric, content: content, dark: false)
            }
        }
    }

    static func drawPlate(_ canvas: CGContext, rect: CGRect, content: WatermarkContent, dark: Bool, metric: CGFloat) {
        canvas.setFillColor((dark ? WatermarkColors.darkBackground : WatermarkColors.lightBackground).cgColor)
        canvas.fill(rect)
        drawBottomBand(canvas, rect: rect, metric: metric, content: content, dark: dark)
    }

    static func drawCaption(_ canvas: CGContext, width: CGFloat, height: CGFloat, content: WatermarkContent, anchor: CaptionAnchor) {
        let metric = min(width, height)
        let band = 0.28 * max(width, height)
        let colors = [
            UIColor(white: 0.06, alpha: 0.90).cgColor,
            UIColor(white: 0, alpha: 0).cgColor,
        ] as CFArray
        let locations: [CGFloat] = anchor == .top ? [0, 1] : [0, 1]
        if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: locations) {
            let start = anchor == .top ? CGPoint(x: 0, y: 0) : CGPoint(x: 0, y: height - band)
            let end = anchor == .top ? CGPoint(x: 0, y: band) : CGPoint(x: 0, y: height)
            canvas.saveGState()
            canvas.clip(to: CGRect(x: 0, y: start.y, width: width, height: band))
            if anchor == .top {
                canvas.drawLinearGradient(gradient, start: start, end: end, options: [])
            } else {
                let reversed = [
                    UIColor(white: 0, alpha: 0).cgColor,
                    UIColor(white: 0.06, alpha: 0.90).cgColor,
                ] as CFArray
                if let fade = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: reversed, locations: [0, 1]) {
                    canvas.drawLinearGradient(fade, start: CGPoint(x: 0, y: height - band), end: CGPoint(x: 0, y: height), options: [])
                }
            }
            canvas.restoreGState()
        }
        let textTop = anchor == .top ? height * 0.035 : height - band * 0.82
        let textBottom = anchor == .top ? band * 0.92 : height - height * 0.03
        let inset = metric * 0.05
        let rect = CGRect(x: inset, y: textTop, width: width - inset * 2, height: max(1, textBottom - textTop))
        drawSignature(canvas, rect: rect, metric: metric, content: content, dark: true)
    }

    static func drawBottomBand(_ canvas: CGContext, rect: CGRect, metric: CGFloat, content: WatermarkContent, dark: Bool) {
        let hairline = max(1, metric * 0.0013)
        canvas.setFillColor((dark ? UIColor(white: 1, alpha: 0.14) : UIColor(white: 0, alpha: 0.09)).cgColor)
        canvas.fill(CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: hairline))
        let inset = metric * 0.048
        let text = rect.insetBy(dx: inset, dy: 0)
        drawSignature(canvas, rect: text, metric: metric, content: content, dark: dark)
    }

    static func drawSignature(_ canvas: CGContext, rect: CGRect, metric: CGFloat, content: WatermarkContent, dark: Bool) {
        guard rect.width > 2, rect.height > 2 else { return }
        let primary = dark ? WatermarkColors.paper : WatermarkColors.ink
        let secondary = dark ? WatermarkColors.muteLight : WatermarkColors.muteDark
        let wordmarkSize = metric * 0.0275
        let altitudeSize = metric * 0.056
        let metaSize = metric * 0.0235
        let gap = metric * 0.012
        let leftWidth = rect.width * 0.62
        let rightWidth = rect.width * 0.36

        let wordmarkFont = Outfit.uiMedium(wordmarkSize)
        let wordmarkKern = wordmarkSize * 0.20
        let wordmarkWidth = measure(content.wordmark, font: wordmarkFont, kern: wordmarkKern).width
        let wordmarkHeight = wordmarkFont.lineHeight

        var altSize = altitudeSize
        var altFont = Outfit.uiSemibold(altSize)
        var altKern = -altSize * 0.03
        while measure(content.altitude, font: altFont, kern: altKern).width > rightWidth && altSize > metric * 0.032 {
            altSize *= 0.94
            altFont = Outfit.uiSemibold(altSize)
            altKern = -altSize * 0.03
        }
        let altitudeHeight = altFont.lineHeight

        let coordinate = content.coordinates?.trimmingCharacters(in: .whitespacesAndNewlines)
        let coordinateShown = (coordinate?.isEmpty == false) ? coordinate : nil
        var coordinateSize = metaSize
        var coordinateFont = font(for: coordinateShown, size: coordinateSize)
        var coordinateKern = kern(for: coordinateShown, size: coordinateSize, base: 0.015)
        let coordinateHeight: CGFloat
        if let coordinateShown {
            while measure(coordinateShown, font: coordinateFont, kern: coordinateKern).width > leftWidth && coordinateSize > metric * 0.015 {
                coordinateSize *= 0.92
                coordinateFont = font(for: coordinateShown, size: coordinateSize)
                coordinateKern = kern(for: coordinateShown, size: coordinateSize, base: 0.015)
            }
            coordinateHeight = coordinateFont.lineHeight
        } else {
            coordinateHeight = 0
        }

        var placeSize = metaSize
        var placeFont = UIFont.systemFont(ofSize: placeSize)
        let placeHeight: CGFloat
        if let place = content.place {
            while measure(place, font: placeFont, kern: 0).width > leftWidth && placeSize > metric * 0.015 {
                placeSize *= 0.92
                placeFont = UIFont.systemFont(ofSize: placeSize)
            }
            placeHeight = placeFont.lineHeight
        } else {
            placeHeight = 0
        }

        var timeSize = metaSize * 0.96
        var timeFont = Outfit.uiRegular(timeSize)
        var timeKern = timeSize * 0.06
        let timeHeight: CGFloat
        if let time = content.time {
            while measure(time, font: timeFont, kern: timeKern).width > rightWidth && timeSize > metric * 0.014 {
                timeSize *= 0.92
                timeFont = Outfit.uiRegular(timeSize)
                timeKern = timeSize * 0.06
            }
            timeHeight = timeFont.lineHeight
        } else {
            timeHeight = 0
        }

        let leftHeight = wordmarkHeight
            + (coordinateShown != nil ? gap + coordinateHeight : 0)
            + (content.place != nil ? gap + placeHeight : 0)
        let rightHeight = altitudeHeight + (content.time != nil ? gap + timeHeight : 0)
        let block = max(leftHeight, rightHeight)
        let top = rect.minY + (rect.height - block) / 2
        let leftTop = top + (block - leftHeight) / 2
        let rightTop = top + (block - rightHeight) / 2

        drawText(content.wordmark, font: wordmarkFont, kern: wordmarkKern, color: primary, x: rect.minX, y: leftTop, alignRight: false, right: rect.maxX)
        let dotRadius = wordmarkSize * 0.145
        let dotCenter = CGPoint(x: rect.minX + wordmarkWidth + wordmarkSize * 0.52, y: leftTop + wordmarkHeight / 2)
        canvas.setFillColor(WatermarkColors.red.cgColor)
        canvas.fillEllipse(in: CGRect(x: dotCenter.x - dotRadius, y: dotCenter.y - dotRadius, width: dotRadius * 2, height: dotRadius * 2))

        var cursor = leftTop + wordmarkHeight
        if let coordinateShown {
            cursor += gap
            drawText(coordinateShown, font: coordinateFont, kern: coordinateKern, color: secondary, x: rect.minX, y: cursor, alignRight: false, right: rect.maxX)
            cursor += coordinateHeight
        }
        if let place = content.place {
            cursor += gap
            drawText(place, font: placeFont, kern: 0, color: secondary, x: rect.minX, y: cursor, alignRight: false, right: rect.maxX)
        }

        drawText(content.altitude, font: altFont, kern: altKern, color: primary, x: rect.minX, y: rightTop, alignRight: true, right: rect.maxX)
        if let time = content.time {
            let timeTop = rightTop + altitudeHeight + gap
            drawText(time, font: timeFont, kern: timeKern, color: secondary, x: rect.minX, y: timeTop, alignRight: true, right: rect.maxX)
        }
    }

    private static func font(for text: String?, size: CGFloat) -> UIFont {
        if text?.containsCJK == true { return .systemFont(ofSize: size) }
        return Outfit.uiRegular(size)
    }

    private static func kern(for text: String?, size: CGFloat, base: CGFloat) -> CGFloat {
        text?.containsCJK == true ? 0 : size * base
    }

    private static func measure(_ text: String, font: UIFont, kern: CGFloat) -> CGSize {
        attributed(text, font: font, kern: kern, color: .black).boundingRect(
            with: CGSize(width: 20_000, height: 800),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            context: nil
        ).size
    }

    private static func drawText(_ text: String, font: UIFont, kern: CGFloat, color: UIColor, x: CGFloat, y: CGFloat, alignRight: Bool, right: CGFloat) {
        let string = attributed(text, font: font, kern: kern, color: color)
        let width = measure(text, font: font, kern: kern).width
        let originX = alignRight ? right - width : x
        string.draw(at: CGPoint(x: originX, y: y))
    }

    private static func attributed(_ text: String, font: UIFont, kern: CGFloat, color: UIColor) -> NSAttributedString {
        NSAttributedString(string: text, attributes: [
            .font: font,
            .kern: kern,
            .foregroundColor: color,
        ])
    }
}

private extension String {
    var containsCJK: Bool {
        unicodeScalars.contains { $0.value > 0x2E80 }
    }
}

private extension UIImage {
    func normalizedUp() -> UIImage {
        if imageOrientation == .up { return self }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}

final class WatermarkCanvasView: UIView {
    var content = WatermarkContent(altitude: "— —", coordinates: nil, place: nil, time: nil)
    var dark = false
    var mode: WatermarkStyle = .plateLight
    var anchor: CaptionAnchor = .bottom
    var metric: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { nil }

    override func draw(_ rect: CGRect) {
        guard let canvas = UIGraphicsGetCurrentContext() else { return }
        let metric = self.metric > 0 ? self.metric : min(bounds.width, bounds.height)
        switch mode {
        case .caption:
            WatermarkRenderer.drawCaption(canvas, width: bounds.width, height: bounds.height, content: content, anchor: anchor)
        case .plateLight, .plateDark, .mount:
            WatermarkRenderer.drawPlate(canvas, rect: bounds, content: content, dark: dark, metric: metric)
        }
    }
}

struct WatermarkPlate: UIViewRepresentable {
    var content: WatermarkContent
    var style: WatermarkStyle
    var anchor: CaptionAnchor
    var metric: CGFloat

    func makeUIView(context: Context) -> WatermarkCanvasView {
        let view = WatermarkCanvasView()
        view.setContentHuggingPriority(.defaultLow, for: .horizontal)
        view.setContentHuggingPriority(.defaultLow, for: .vertical)
        return view
    }

    func updateUIView(_ uiView: WatermarkCanvasView, context: Context) {
        uiView.content = content
        uiView.mode = style
        uiView.anchor = anchor
        uiView.metric = metric
        uiView.dark = style == .plateDark || style == .caption
        uiView.setNeedsDisplay()
    }
}
