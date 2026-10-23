import SwiftUI

struct RootView: View {
    @Bindable var model: AppModel

    var body: some View {
        ZStack {
            if !model.prefs.onboardingComplete && model.preview == nil {
                OnboardingView(model: model)
            } else if model.settingsOpen {
                SettingsSheet(model: model)
                    .safeAreaPadding(.top, 8)
                    .overlay(alignment: .topTrailing) {
                        Button { model.settingsOpen = false } label: {
                            Image(systemName: AppSymbols.close)
                                .foregroundStyle(Color(hex: 0xF3F0E8))
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(model.copy.text("cd_close_settings"))
                        .padding(.trailing, 8)
                        .padding(.top, 4)
                    }
            } else {
                Group {
                    if model.mode == .camera {
                        CameraScreen(model: model)
                    } else {
                        DisplayScreen(model: model)
                    }
                }
            }
        }
        .animation(model.preview == nil ? .easeInOut(duration: 0.28) : nil, value: model.mode)
        .animation(model.preview == nil ? .easeInOut(duration: 0.28) : nil, value: model.settingsOpen)
        .preferredColorScheme(model.mode == .display && model.prefs.theme.lightBars ? .light : .dark)
        .fullScreenCover(isPresented: $model.galleryOpen) {
            GalleryView(model: model)
        }
        .task { model.start() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.refreshPermissions() }
        }
    }

    @Environment(\.scenePhase) private var scenePhase
}
