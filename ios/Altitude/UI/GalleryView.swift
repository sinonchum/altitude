import SwiftUI
import UIKit

struct GalleryView: View {
    @Bindable var model: AppModel
    @State private var index = 0
    @State private var shareURL: URL?

    var body: some View {
        let copy = model.copy
        ZStack {
            Color.black.ignoresSafeArea()
            if model.photos.isEmpty {
                Text(copy.text("gallery_empty"))
                    .font(Outfit.regular(16))
                    .foregroundStyle(Color(hex: 0xB7B7B2))
                    .multilineTextAlignment(.center)
                    .padding(32)
            } else {
                TabView(selection: $index) {
                    ForEach(Array(model.photos.enumerated()), id: \.element.id) { offset, photo in
                        GalleryPage(photo: photo).tag(offset)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            VStack {
                HStack {
                    Button { model.galleryOpen = false } label: {
                        Image(systemName: AppSymbols.close).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(copy.text("cd_close_gallery"))
                    Spacer()
                    if !model.photos.isEmpty {
                        Text(copy.format("gallery_page", index + 1, model.photos.count))
                            .font(Outfit.medium(13))
                        Spacer()
                        Button { share() } label: {
                            Image(systemName: AppSymbols.share).frame(width: 44, height: 44)
                        }
                        .accessibilityLabel(copy.text("cd_share_photo"))
                        Button { model.deleteCandidate = model.photos[safe: index] } label: {
                            Image(systemName: AppSymbols.trash).frame(width: 44, height: 44)
                        }
                        .accessibilityLabel(copy.text("cd_delete_photo"))
                    }
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 8)
                Spacer()
                if !model.photos.isEmpty {
                    Button(copy.text("gallery_open")) { openPhotos() }
                        .font(Outfit.medium(14))
                        .foregroundStyle(.white)
                        .padding(.bottom, 8)
                        .accessibilityLabel(copy.text("cd_open_gallery"))
                }
            }
        }
        .accessibilityIdentifier("screen.gallery")
        .sheet(isPresented: Binding(get: { shareURL != nil }, set: { if !$0 { shareURL = nil } })) {
            if let shareURL { ShareSheet(url: shareURL) }
        }
        .alert(copy.text("gallery_delete_title"), isPresented: Binding(
            get: { model.deleteCandidate != nil },
            set: { if !$0 { model.deleteCandidate = nil } }
        )) {
            Button(copy.text("gallery_delete_confirm"), role: .destructive) {
                if let photo = model.deleteCandidate {
                    Task { _ = await model.deletePhoto(photo) }
                }
                model.deleteCandidate = nil
            }
            Button(copy.text("gallery_delete_cancel"), role: .cancel) { model.deleteCandidate = nil }
        } message: {
            Text(copy.text("gallery_delete_body"))
        }
    }

    private func share() {
        guard let photo = model.photos[safe: index] else { return }
        Task {
            guard let image = await PhotoStore.image(for: photo.asset, target: CGSize(width: 2000, height: 2000)),
                  let data = image.jpegData(compressionQuality: 0.95) else {
                model.reportSave("error_share_failed")
                return
            }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("Altitude-share.jpg")
            do {
                try data.write(to: url)
                shareURL = url
            } catch {
                model.reportSave("error_share_failed")
            }
        }
    }

    private func openPhotos() {
        guard let url = URL(string: "photos-redirect://") else {
            model.reportSave("error_open_failed")
            return
        }
        UIApplication.shared.open(url) { accepted in
            if !accepted { model.reportSave("error_open_failed") }
        }
    }
}

private struct GalleryPage: View {
    var photo: LibraryPhoto
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFit()
            } else {
                ProgressView().tint(.white)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task { image = await PhotoStore.image(for: photo.asset, target: CGSize(width: 1600, height: 1600)) }
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    var url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
