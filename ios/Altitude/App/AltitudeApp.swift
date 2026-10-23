import SwiftUI

@main
struct AltitudeApp: App {
    @State private var model = AppModel()

    init() {
        Outfit.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .tint(model.prefs.dynamicColor ? Color.accentColor : Color(hex: 0xF3F0E8))
        }
    }
}
