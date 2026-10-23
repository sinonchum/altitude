import SwiftUI

struct SettingsSheet: View {
    @Bindable var model: AppModel

    var body: some View {
        let copy = model.copy
        let accent = model.prefs.dynamicColor ? Color.accentColor : Color(hex: 0xF3F0E8)
        VStack(alignment: .leading, spacing: 0) {
            Capsule().fill(Color.white.opacity(0.28)).frame(width: 36, height: 4).frame(maxWidth: .infinity).padding(.top, 8)
            Text(copy.text("settings_title"))
                .font(Outfit.semibold(22))
                .foregroundStyle(Color(hex: 0xF3F0E8))
                .padding(.top, 16)
                .padding(.trailing, 52)
            Text(copy.format("settings_about_version", AppVersion.line))
                .font(Outfit.regular(13))
                .foregroundStyle(Color(hex: 0x8E8A82))
                .padding(.top, 6)
                .padding(.trailing, 8)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityElement(children: .ignore)
                .accessibilityIdentifier("settings.version")
                .accessibilityAddTraits(.isStaticText)
                .accessibilityLabel(copy.format("settings_about_version", AppVersion.line))
            section(copy.text("settings_language"))
            flow {
                ForEach(AppLanguage.allCases) { language in
                    ChoiceChip(title: copy.text(language.labelKey), selected: model.language == language, accent: accent) {
                        model.setLanguage(language)
                    }
                }
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    section(copy.text("settings_section_units"))
                    flow {
                        ChoiceChip(title: copy.text("unit_meters_name"), selected: model.prefs.unit == .meters, accent: accent) { model.setUnit(.meters) }
                        ChoiceChip(title: copy.text("unit_feet_name"), selected: model.prefs.unit == .feet, accent: accent) { model.setUnit(.feet) }
                    }
                    section(copy.text("settings_section_theme"))
                    HStack(spacing: 12) {
                        ForEach(DisplayTheme.allCases, id: \.rawValue) { theme in
                            let palette = Palette.of(theme)
                            VStack(spacing: 6) {
                                Circle()
                                    .fill(palette.background)
                                    .frame(width: 36, height: 36)
                                    .overlay(Circle().stroke(model.prefs.theme == theme ? palette.accent : Color.white.opacity(0.2), lineWidth: model.prefs.theme == theme ? 2 : 1))
                                    .onTapGesture { model.setTheme(theme) }
                                    .accessibilityLabel(copy.format("cd_theme", copy.themeName(theme)))
                                Text(copy.themeName(theme)).font(Outfit.regular(11)).foregroundStyle(Color(hex: 0xB7B3AA)).lineLimit(1).minimumScaleFactor(0.7)
                            }
                        }
                    }
                    .padding(.top, 4)
                    toggle(copy.text("settings_brightness_title"), copy.text("settings_brightness_subtitle"), model.prefs.maxBrightness, accent) { model.setMaxBrightness($0) }
                    toggle(copy.text("settings_coordinates_title"), copy.text("settings_coordinates_subtitle"), model.prefs.showCoordinates, accent) { model.setShowCoordinates($0) }
                    toggle(copy.text("settings_accuracy_title"), copy.text("settings_accuracy_subtitle"), model.prefs.showAccuracy, accent) { model.setShowAccuracy($0) }
                    toggle(copy.text("settings_clock_title"), copy.text("settings_clock_subtitle"), model.prefs.showClock, accent) { model.setShowClock($0) }
                    toggle(copy.text("settings_place_title"), copy.text("settings_place_subtitle"), model.prefs.showPlace, accent) { model.setShowPlace($0) }
                    toggle(copy.text("settings_dynamic_title"), copy.text("settings_dynamic_subtitle"), model.prefs.dynamicColor, accent) { model.setDynamicColor($0) }
                    Text(copy.text("settings_watermark_note"))
                        .font(Outfit.regular(13))
                        .foregroundStyle(Color(hex: 0x8E8A82))
                        .padding(.top, 12)
                        .fixedSize(horizontal: false, vertical: true)
                    Color.clear.frame(height: 28)
                }
            }
        }
        .padding(.horizontal, 22)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(hex: 0x121418).ignoresSafeArea())
        .accessibilityIdentifier("screen.settings")
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(Outfit.medium(12))
            .tracking(0.6)
            .foregroundStyle(Color(hex: 0x8E8A82))
            .padding(.top, 18)
            .padding(.bottom, 8)
    }

    private func flow<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        FlowLayout(spacing: 8) { content() }
    }

    private func toggle(_ title: String, _ subtitle: String, _ on: Bool, _ accent: Color, _ change: @escaping (Bool) -> Void) -> some View {
        Button { change(!on) } label: {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Outfit.medium(16)).foregroundStyle(Color(hex: 0xF3F0E8)).multilineTextAlignment(.leading)
                    Text(subtitle).font(Outfit.regular(12)).foregroundStyle(Color(hex: 0x8E8A82)).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 8)
                Text(model.copy.text(on ? "toggle_on" : "toggle_off"))
                    .font(Outfit.semibold(13))
                    .foregroundStyle(on ? Color(hex: 0x141210) : Color(hex: 0xB7B3AA))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(on ? accent : Color.white.opacity(0.08), in: Capsule())
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}

struct FlowPlacement: Equatable {
    var x: CGFloat
    var y: CGFloat
    var size: CGSize
}

/// Shared wrap used by `FlowLayout` and the chip-fit unit test.
enum FlowArrangement {
    /// Places `sizes` left to right. The next item starts a new row when it would pass `limit`.
    /// An item wider than `limit` is clamped to that width so it cannot draw past the row.
    static func arrange(_ sizes: [CGSize], limit: CGFloat, spacing: CGFloat) -> (placements: [FlowPlacement], size: CGSize) {
        let bounded = limit > 1
        let width = bounded ? limit : 10_000
        var placements: [FlowPlacement] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var used: CGFloat = 0
        for raw in sizes {
            var item = raw
            if bounded, item.width > width + 0.5 {
                item.width = width
            }
            if x > 0, x + item.width > width + 0.5 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            placements.append(FlowPlacement(x: x, y: y, size: item))
            rowHeight = max(rowHeight, item.height)
            used = max(used, x + item.width)
            x += item.width + spacing
        }
        return (placements, CGSize(width: bounded ? width : used, height: y + rowHeight))
    }
}

/// Wrapping row that stays inside the width it is offered. No phone or language table.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes = subviews.map { fitted($0, limit: proposal.width ?? 0) }
        return FlowArrangement.arrange(sizes, limit: proposal.width ?? 0, spacing: spacing).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let sizes = subviews.map { fitted($0, limit: bounds.width) }
        let arranged = FlowArrangement.arrange(sizes, limit: bounds.width, spacing: spacing)
        for (view, placement) in zip(subviews, arranged.placements) {
            view.place(
                at: CGPoint(x: bounds.minX + placement.x, y: bounds.minY + placement.y),
                proposal: ProposedViewSize(placement.size)
            )
        }
    }

    /// Ideal size when it fits. A wider child is measured again at the row width so its text can wrap.
    private func fitted(_ view: LayoutSubview, limit: CGFloat) -> CGSize {
        let ideal = view.sizeThatFits(.unspecified)
        guard limit > 1, ideal.width > limit + 0.5 else { return ideal }
        let constrained = view.sizeThatFits(ProposedViewSize(width: limit, height: nil))
        return CGSize(width: min(constrained.width, limit), height: max(constrained.height, 1))
    }
}
