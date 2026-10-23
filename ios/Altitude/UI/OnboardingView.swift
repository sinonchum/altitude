import SwiftUI

struct OnboardingView: View {
    @Bindable var model: AppModel
    @State private var step = 0

    var body: some View {
        let copy = model.copy
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { index in
                    Capsule()
                        .fill(index == step ? Color(hex: 0xE10600) : Color(hex: 0x9A958C).opacity(0.45))
                        .frame(width: index == step ? 22 : 7, height: 7)
                }
            }
            .accessibilityLabel(copy.format("cd_onboarding_step", step + 1, 3))
            .padding(.top, 8)
            Spacer(minLength: 24)
            Text(kicker)
                .font(Outfit.medium(13))
                .tracking(1.4)
                .foregroundStyle(Color(hex: 0xE10600))
            Text(title)
                .font(Outfit.semibold(32))
                .foregroundStyle(Color(hex: 0xF3F0E8))
                .padding(.top, 12)
                .fixedSize(horizontal: false, vertical: true)
            Text(bodyText)
                .font(Outfit.regular(16))
                .foregroundStyle(Color(hex: 0x9A958C))
                .padding(.top, 14)
                .fixedSize(horizontal: false, vertical: true)
            if step == 0 {
                Text(copy.format("onboarding_sample_caption", copy.altitudeReading(5210, .meters)))
                    .font(Outfit.medium(15))
                    .foregroundStyle(Color(hex: 0xF3F0E8))
                    .padding(.top, 22)
            }
            Spacer(minLength: 24)
            Button(primaryTitle) { advance(primary: true) }
                .font(Outfit.semibold(16))
                .foregroundStyle(Color(hex: 0x141210))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color(hex: 0xF3F0E8), in: Capsule())
                .buttonStyle(.plain)
            Button(secondaryTitle) { advance(primary: false) }
                .font(Outfit.medium(15))
                .foregroundStyle(Color(hex: 0x9A958C))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .buttonStyle(.plain)
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(hex: 0x090A0C).ignoresSafeArea())
        .accessibilityIdentifier("screen.onboarding")
    }

    private var copy: Copy { model.copy }
    private var kicker: String {
        switch step {
        case 0: return copy.text("onboarding_display_kicker")
        case 1: return copy.text("onboarding_msl_kicker")
        default: return copy.text("onboarding_photo_kicker")
        }
    }
    private var title: String {
        switch step {
        case 0: return copy.text("onboarding_display_title")
        case 1: return copy.text("onboarding_msl_title")
        default: return copy.text("onboarding_photo_title")
        }
    }
    private var bodyText: String {
        switch step {
        case 0: return copy.text("onboarding_display_body")
        case 1: return copy.text("onboarding_msl_body")
        default: return copy.text("onboarding_photo_body")
        }
    }
    private var primaryTitle: String {
        switch step {
        case 0: return copy.text("onboarding_continue")
        case 1: return copy.text("onboarding_allow_location")
        default: return copy.text("onboarding_allow_camera")
        }
    }
    private var secondaryTitle: String {
        step == 2 ? copy.text("onboarding_display_only") : copy.text("onboarding_later")
    }

    private func advance(primary: Bool) {
        if step == 1 && primary { model.requestLocation() }
        if step == 2 && primary { Task { await model.requestCamera() } }
        if step >= 2 {
            model.completeOnboarding()
        } else {
            withAnimation(.easeInOut(duration: 0.25)) { step += 1 }
        }
    }
}
