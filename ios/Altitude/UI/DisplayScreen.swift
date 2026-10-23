import SwiftUI

struct DisplayScreen: View {
    @Bindable var model: AppModel
    @State private var chrome = true
    @State private var token = 0

    var body: some View {
        let palette = Palette.of(model.prefs.theme)
        let copy = model.copy
        ZStack {
            palette.background.ignoresSafeArea()
            VStack(spacing: 0) {
                Circle().fill(palette.accent).frame(width: 7, height: 7).padding(.top, 18)
                Text(copy.text("wordmark"))
                    .font(Outfit.medium(13))
                    .tracking(4)
                    .foregroundStyle(palette.secondary)
                    .padding(.top, 18)
                Spacer(minLength: 12)
                hero(palette: palette, copy: copy)
                caption(palette: palette, copy: copy)
                    .padding(.top, 8)
                Spacer(minLength: 12)
                footer(palette: palette, copy: copy)
                    .padding(.bottom, 18)
            }
            .padding(.horizontal, 28)
            .safeAreaPadding(.bottom, 72)

            VStack {
                topBar(palette: palette, copy: copy)
                Spacer()
                if chrome {
                    ModeSwitch(mode: .display, copy: copy, primary: palette.primary, secondary: palette.secondary) { mode in
                        reveal()
                        model.setMode(mode)
                    }
                    .padding(.bottom, 12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .safeAreaPadding(.horizontal, 12)
        }
        .contentShape(Rectangle())
        .onTapGesture { reveal() }
        .accessibilityIdentifier("screen.display")
        .task(id: token) {
            guard model.preview == nil, !model.settingsOpen else { return }
            try? await Task.sleep(nanoseconds: 4_600_000_000)
            withAnimation(.easeOut(duration: 0.42)) { chrome = false }
        }
    }

    private func reveal() {
        withAnimation(.easeOut(duration: 0.22)) { chrome = true }
        token += 1
    }

    private func hero(palette: Palette, copy: Copy) -> some View {
        let meters = model.reading.altitudeMslMeters
        let shown = meters.map { copy.altitudeNumber($0, model.prefs.unit) } ?? copy.text("altitude_placeholder")
        return ViewThatFits(in: .horizontal) {
            number(shown, size: 168, palette: palette, copy: copy)
            number(shown, size: 120, palette: palette, copy: copy)
            number(shown, size: 84, palette: palette, copy: copy)
        }
        .frame(maxWidth: .infinity)
        .accessibilityLabel(meters == nil ? copy.text("cd_altitude_waiting") : copy.format("cd_altitude", copy.altitudeReading(meters ?? 0, model.prefs.unit)))
    }

    private func number(_ shown: String, size: CGFloat, palette: Palette, copy: Copy) -> some View {
        Text(shown)
            .font(Outfit.extraBold(size))
            .foregroundStyle(palette.primary)
            .monospacedDigit()
            .lineLimit(1)
            .minimumScaleFactor(0.4)
            .contentTransition(.numericText())
            .animation(.snappy(duration: 0.35), value: shown)
            .onTapGesture(count: 2) {
                reveal()
                model.toggleUnit()
            }
            .onTapGesture { reveal() }
    }

    private func caption(palette: Palette, copy: Copy) -> some View {
        let meters = model.reading.altitudeMslMeters
        let text: String
        let color: Color
        if meters == nil {
            text = copy.text("caption_placeholder")
            color = palette.secondary
        } else if model.reading.absoluteMsl {
            text = copy.format("unit_level_caption", copy.text(model.prefs.unit == .feet ? "unit_feet_symbol" : "unit_meters_symbol"))
            color = palette.secondary
        } else {
            text = copy.text("unit_pressure_caption")
            color = palette.accent
        }
        return Text(text)
            .font(Outfit.medium(15))
            .foregroundStyle(color)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func footer(palette: Palette, copy: Copy) -> some View {
        if let guidance = model.guidance, model.preview == nil {
            prompt(guidance, palette: palette, copy: copy)
        } else if model.reading.altitudeMslMeters == nil {
            searching(palette: palette, copy: copy)
        } else {
            meta(palette: palette, copy: copy)
        }
    }

    private func meta(palette: Palette, copy: Copy) -> some View {
        VStack(spacing: 8) {
            if model.prefs.showAccuracy, let accuracy = model.reading.accuracyMeters {
                HStack(spacing: 8) {
                    Circle().fill(qualityColor(model.reading.quality)).frame(width: 6, height: 6)
                    Text(copy.format("format_accuracy_source", copy.accuracy(accuracy, model.prefs.unit), copy.sourceLabel(model.reading.source)))
                        .font(Outfit.medium(13))
                        .foregroundStyle(palette.secondary)
                        .multilineTextAlignment(.center)
                }
            }
            if model.prefs.showCoordinates, let latitude = model.latitude, let longitude = model.longitude {
                Text(copy.coordinatePair(latitude: latitude, longitude: longitude))
                    .font(Outfit.regular(15))
                    .foregroundStyle(palette.primary.opacity(0.88))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if model.prefs.showPlace, let place = model.placeName, !place.isEmpty {
                Text(place)
                    .font(Outfit.medium(14))
                    .foregroundStyle(palette.secondary)
                    .multilineTextAlignment(.center)
            }
            if model.prefs.showClock {
                Text(copy.displayClock(model.nowMillis))
                    .font(Outfit.medium(13))
                    .tracking(1.5)
                    .foregroundStyle(palette.secondary)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func searching(palette: Palette, copy: Copy) -> some View {
        VStack(spacing: 8) {
            Text(copy.text(model.geoidReady ? "searching_satellites" : "preparing_geoid"))
                .font(Outfit.medium(16))
                .foregroundStyle(palette.primary)
            Text(copy.text("searching_hint"))
                .font(Outfit.regular(13))
                .foregroundStyle(palette.secondary)
                .multilineTextAlignment(.center)
            if let known = model.lastKnown {
                Text(copy.format("last_known", copy.altitudeReading(known.altitudeMeters, model.prefs.unit), copy.age(since: known.timeMs, now: model.nowMillis)))
                    .font(Outfit.regular(13))
                    .foregroundStyle(palette.secondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private func prompt(_ guidance: LocationGuidance, palette: Palette, copy: Copy) -> some View {
        let title: String
        let body: String
        let action: String
        switch guidance {
        case .permission:
            title = copy.text("prompt_location_permission_title")
            body = copy.text("prompt_location_permission_body")
            action = copy.text("prompt_location_permission_action")
        case .approximate:
            title = copy.text("prompt_location_approximate_title")
            body = copy.text("prompt_location_approximate_body")
            action = copy.text("prompt_location_approximate_action")
        case .servicesOff:
            title = copy.text("prompt_location_disabled_title")
            body = copy.text("prompt_location_disabled_body")
            action = copy.text("prompt_location_disabled_action")
        case .gpsOff:
            title = copy.text("prompt_gps_off_title")
            body = copy.text("prompt_gps_off_body")
            action = copy.text("prompt_gps_off_action")
        }
        return VStack(spacing: 8) {
            Text(title).font(Outfit.semibold(18)).foregroundStyle(palette.primary).multilineTextAlignment(.center)
            Text(body).font(Outfit.regular(14)).foregroundStyle(palette.secondary).multilineTextAlignment(.center)
            Button(action) {
                if guidance.opensSettings { model.openSettings() } else { model.requestLocation() }
            }
            .font(Outfit.semibold(15))
            .foregroundStyle(palette.primary)
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .background(palette.primary.opacity(0.1), in: Capsule())
            .buttonStyle(.plain)
            .padding(.top, 8)
        }
    }

    private func topBar(palette: Palette, copy: Copy) -> some View {
        HStack {
            if chrome {
                Button {
                    reveal()
                    model.setMaxBrightness(!model.prefs.maxBrightness)
                } label: {
                    Image(systemName: AppSymbols.brightness)
                        .font(.system(size: 20, weight: .regular))
                        .foregroundStyle(model.prefs.maxBrightness ? palette.accent : palette.secondary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(copy.text(model.prefs.maxBrightness ? "cd_brightness_on" : "cd_brightness_off"))
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
            Spacer()
            if chrome {
                Button {
                    reveal()
                    model.toggleUnit()
                } label: {
                    Text(copy.text(model.prefs.unit == .feet ? "unit_feet_name" : "unit_meters_name"))
                        .font(Outfit.medium(14))
                        .foregroundStyle(palette.primary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(copy.format("cd_unit_toggle", copy.text(model.prefs.unit == .feet ? "unit_feet_name" : "unit_meters_name")))
            }
            Spacer()
            Button { model.settingsOpen = true } label: {
                Image(systemName: AppSymbols.settings)
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(palette.secondary)
                    .frame(width: 44, height: 44)
                    .background(palette.primary.opacity(0.08), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(copy.text("cd_settings"))
        }
        .padding(.top, 4)
    }
}
