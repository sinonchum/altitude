import Photos
import SwiftUI
import UIKit

struct ImportPreviewView: View {
    @Bindable var model: AppModel
    @State private var framed: UIImage?

    var body: some View {
        let copy = model.copy
        ZStack {
            Color.black.ignoresSafeArea()
            if model.importNeedsAccess {
                accessGate(copy)
            } else if model.importReading && model.importPages.isEmpty {
                Text(copy.text("import_reading"))
                    .font(Outfit.regular(16))
                    .foregroundStyle(Color(hex: 0xB7B7B2))
                    .multilineTextAlignment(.center)
                    .padding(28)
            } else if model.importPages.isEmpty {
                Text(copy.text("error_photo_decode"))
                    .font(Outfit.regular(16))
                    .foregroundStyle(Color(hex: 0xB7B7B2))
                    .multilineTextAlignment(.center)
                    .padding(28)
            } else {
                GeometryReader { geo in
                    if geo.size.width > geo.size.height {
                        landscape(copy, size: geo.size)
                    } else {
                        portrait(copy)
                    }
                }
            }
        }
        .onAppear(perform: renderFrame)
        .onChange(of: frameKey) { _, _ in renderFrame() }
    }

    private var frameKey: String {
        let page = current
        let meters = page?.altitude?.meters.map { String($0) } ?? ""
        return [
            String(model.importIndex),
            model.prefs.watermarkStyle.rawValue,
            model.prefs.captionAnchor.rawValue,
            model.prefs.unit.rawValue,
            page?.altitude?.provenance.rawValue ?? "",
            meters,
            page?.place ?? "",
            model.language.rawValue,
            page?.id ?? "",
        ].joined(separator: "|")
    }

    private var current: ImportPage? {
        guard model.importPages.indices.contains(model.importIndex) else { return model.importPages.first }
        return model.importPages[model.importIndex]
    }

    private func portrait(_ copy: Copy) -> some View {
        VStack(spacing: 8) {
            header(copy)
            if model.importLimited { limitedBanner(copy) }
            framedImage(copy)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            notice(copy)
            controls(copy)
            saveBar(copy)
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }

    private func landscape(_ copy: Copy, size: CGSize) -> some View {
        HStack(spacing: 12) {
            framedImage(copy)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            VStack(alignment: .leading, spacing: 8) {
                header(copy)
                if model.importLimited { limitedBanner(copy) }
                notice(copy)
                Spacer(minLength: 4)
                controls(copy)
                saveBar(copy)
            }
            .frame(width: min(320, max(180, size.width * 0.38)))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private func header(_ copy: Copy) -> some View {
        HStack(spacing: 8) {
            Button { model.closeImport() } label: {
                Image(systemName: AppSymbols.close)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(copy.text("cd_close_import"))
            Text(copy.text("import_title"))
                .font(Outfit.semibold(17))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 4)
            if model.importPages.count > 1 {
                Text(copy.format("gallery_page", model.importIndex + 1, model.importPages.count))
                    .font(Outfit.medium(13))
                    .lineLimit(1)
            }
        }
        .foregroundStyle(.white)
    }

    @ViewBuilder
    private func notice(_ copy: Copy) -> some View {
        if let page = current {
            let kind = importNoticeKind(
                hasCoordinates: page.capture.hasCoordinates,
                altitude: page.altitude,
                locationRedacted: page.locationRedacted
            )
            if let title = noticeTitle(kind, copy), let body = noticeBody(kind, copy) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(Outfit.semibold(15))
                        .foregroundStyle(.white)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(body)
                        .font(Outfit.regular(13))
                        .foregroundStyle(Color(hex: 0xB7B7B2))
                        .fixedSize(horizontal: false, vertical: true)
                    if kind == .locationHidden {
                        Button(copy.text("import_allow_photo_locations")) { model.openSettings() }
                            .font(Outfit.semibold(14))
                            .foregroundStyle(Color(hex: 0xF6C453))
                            .buttonStyle(.plain)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("import.notice")
            }
        }
    }

    private func framedImage(_ copy: Copy) -> some View {
        Group {
            if let framed {
                Image(uiImage: framed)
                    .resizable()
                    .scaledToFit()
                    .accessibilityLabel(copy.text("cd_import_preview"))
                    .accessibilityIdentifier("import.preview")
            } else {
                ProgressView().tint(.white)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func controls(_ copy: Copy) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(WatermarkStyle.allCases, id: \.rawValue) { style in
                        ChoiceChip(title: copy.watermarkName(style), selected: model.prefs.watermarkStyle == style, accent: .white) {
                            model.setWatermark(style)
                        }
                    }
                }
            }
            HStack(spacing: 6) {
                ForEach(CaptionAnchor.allCases, id: \.rawValue) { anchor in
                    ChoiceChip(title: copy.anchorName(anchor), selected: model.prefs.captionAnchor == anchor, accent: Color(hex: 0xF6C453)) {
                        model.setAnchor(anchor)
                    }
                }
            }
        }
    }

    private func saveBar(_ copy: Copy) -> some View {
        HStack(spacing: 8) {
            Button { Task { await model.saveImport(all: false) } } label: {
                Text(model.importSaving ? savingLabel(copy) : copy.text("import_save"))
                    .font(Outfit.semibold(15))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.white, in: Capsule())
                    .foregroundStyle(.black)
            }
            .buttonStyle(.plain)
            .disabled(model.importSaving || current == nil)
            .accessibilityLabel(copy.text("cd_import_save"))
            if model.importPages.count > 1 {
                Button { Task { await model.saveImport(all: true) } } label: {
                    Text(copy.text("import_save_all"))
                        .font(Outfit.semibold(15))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.white.opacity(0.14), in: Capsule())
                        .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
                .disabled(model.importSaving)
                .accessibilityLabel(copy.text("cd_import_save_all"))
            }
        }
    }

    private func limitedBanner(_ copy: Copy) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(copy.text("import_partial_title"))
                .font(Outfit.semibold(13))
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
            Text(copy.text("import_partial_body"))
                .font(Outfit.regular(12))
                .foregroundStyle(Color(hex: 0xB7B7B2))
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func accessGate(_ copy: Copy) -> some View {
        VStack(spacing: 12) {
            Text(copy.text("import_location_off_title"))
                .font(Outfit.semibold(20))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(copy.text("import_needs_access_body"))
                .font(Outfit.regular(14))
                .foregroundStyle(Color(hex: 0xB7B7B2))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Button(copy.text("import_allow_photos")) {
                if model.photoRead == .notDetermined {
                    Task { await model.allowPhotoRead() }
                } else {
                    model.openSettings()
                }
            }
                .font(Outfit.semibold(15))
                .foregroundStyle(.black)
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .background(Color.white, in: Capsule())
                .buttonStyle(.plain)
            Button(copy.text("cd_close_import")) { model.closeImport() }
                .font(Outfit.medium(14))
                .foregroundStyle(.white)
                .buttonStyle(.plain)
        }
        .padding(28)
    }

    private func savingLabel(_ copy: Copy) -> String {
        if model.importSaveTotal > 1 {
            return copy.format("import_saving_progress", model.importSavedCount, model.importSaveTotal)
        }
        return copy.text("import_saving")
    }

    private func noticeTitle(_ kind: ImportNoticeKind, _ copy: Copy) -> String? {
        switch kind {
        case .none: return nil
        case .noLocation: return copy.text("import_no_location_title")
        case .locationHidden: return copy.text("import_location_hidden_title")
        case .computedAltitude: return copy.text("import_computed_title")
        case .altitudeMissing: return copy.text("import_missing_title")
        }
    }

    private func noticeBody(_ kind: ImportNoticeKind, _ copy: Copy) -> String? {
        switch kind {
        case .none: return nil
        case .noLocation: return copy.text("import_no_location_body")
        case .locationHidden: return copy.text("import_location_hidden_body")
        case .computedAltitude: return copy.text("import_altitude_computed")
        case .altitudeMissing: return copy.text("import_altitude_missing")
        }
    }

    private func renderFrame() {
        guard let page = current else {
            framed = nil
            return
        }
        let content = model.copy.importWatermark(
            capture: page.capture,
            altitude: page.altitude,
            place: page.place,
            showPlace: model.prefs.showPlace,
            unit: model.prefs.unit
        )
        framed = WatermarkRenderer.render(
            photo: page.preview,
            content: content,
            style: model.prefs.watermarkStyle,
            anchor: model.prefs.captionAnchor
        )
    }
}
