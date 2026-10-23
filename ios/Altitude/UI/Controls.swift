import SwiftUI

struct ModeSwitch: View {
    var mode: AppMode
    var copy: Copy
    var primary: Color
    var secondary: Color
    var vertical: Bool = false
    var onMode: (AppMode) -> Void

    var body: some View {
        let content = Group {
            chip(copy.text("mode_display"), selected: mode == .display) { onMode(.display) }
            chip(copy.text("mode_camera"), selected: mode == .camera) { onMode(.camera) }
        }
        if vertical {
            VStack(spacing: 2) { content }
                .padding(4)
                .background(primary.opacity(0.08), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        } else {
            HStack(spacing: 2) { content }
                .padding(4)
                .background(primary.opacity(0.08), in: Capsule())
        }
    }

    private func chip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(Outfit.semibold(14))
                .foregroundStyle(selected ? primary : secondary)
                .lineLimit(1)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(selected ? primary.opacity(0.14) : Color.clear, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct CircleTool: View {
    var systemName: String
    var accessibility: String
    var emphasized: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(emphasized ? Color.black : Color(hex: 0xB7B7B2))
                .frame(width: 44, height: 44)
                .background(emphasized ? Color.white : Color.white.opacity(0.08), in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibility)
    }
}

struct ShutterButton: View {
    var enabled: Bool
    var busy: Bool
    var label: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Circle()
                .fill(busy ? Color(hex: 0xB9B9B4) : Color.white)
                .padding(6)
                .frame(width: 78, height: 78)
                .overlay(Circle().stroke(Color.white, lineWidth: 2))
                .scaleEffect(busy ? 0.92 : 1)
        }
        .buttonStyle(.plain)
        .disabled(!enabled || busy)
        .accessibilityLabel(label)
        .accessibilityIdentifier("shutter")
    }
}

struct ChoiceChip: View {
    var title: String
    var selected: Bool
    var accent: Color
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Outfit.semibold(14))
                .foregroundStyle(selected ? Color(hex: 0x141210) : Color(hex: 0xF3F0E8))
                .lineLimit(1)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? accent : Color.white.opacity(0.08), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct MockScene: View {
    var front: Bool

    var body: some View {
        Canvas { context, size in
            let sky = Gradient(colors: [
                Color(hex: 0x1C3E66),
                Color(hex: 0x7EA4C4),
                Color(hex: 0xE7C8A2),
                Color(hex: 0xC4A27A),
            ])
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
                sky,
                startPoint: .zero,
                endPoint: CGPoint(x: 0, y: size.height)
            ))
            var ridge = Path()
            ridge.move(to: CGPoint(x: 0, y: size.height * 0.62))
            ridge.addQuadCurve(
                to: CGPoint(x: size.width * 0.46, y: size.height * 0.40),
                control: CGPoint(x: size.width * 0.22, y: size.height * 0.34)
            )
            ridge.addQuadCurve(
                to: CGPoint(x: size.width, y: size.height * 0.58),
                control: CGPoint(x: size.width * 0.72, y: size.height * 0.30)
            )
            ridge.addLine(to: CGPoint(x: size.width, y: size.height))
            ridge.addLine(to: CGPoint(x: 0, y: size.height))
            ridge.closeSubpath()
            context.fill(ridge, with: .linearGradient(
                Gradient(colors: [Color(hex: 0xF4F1EA), Color(hex: 0x8E7B68), Color(hex: 0x3E342C)]),
                startPoint: CGPoint(x: 0, y: size.height * 0.4),
                endPoint: CGPoint(x: 0, y: size.height)
            ))
            let sun = CGRect(x: size.width * 0.72, y: size.height * 0.16, width: size.width * 0.08, height: size.width * 0.08)
            context.fill(Path(ellipseIn: sun), with: .color(Color(hex: 0xF6E2B8)))
            if front {
                let face = CGRect(
                    x: size.width * 0.30,
                    y: size.height * 0.22,
                    width: size.width * 0.40,
                    height: size.height * 0.46
                )
                context.fill(Path(ellipseIn: face), with: .color(Color(hex: 0xE6B89C).opacity(0.92)))
                context.stroke(Path(ellipseIn: face.insetBy(dx: 8, dy: 8)), with: .color(Color.white.opacity(0.35)), lineWidth: 2)
            }
        }
        .accessibilityIdentifier("camera.preview")
    }
}
