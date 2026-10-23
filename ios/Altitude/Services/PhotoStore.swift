import Photos
import UIKit

struct LibraryPhoto: Identifiable, Equatable {
    var id: String
    var asset: PHAsset

    static func == (lhs: LibraryPhoto, rhs: LibraryPhoto) -> Bool { lhs.id == rhs.id }
}

enum PhotoStore {
    static let albumTitle = "Altitude"

    static func authorizationGranted() -> Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return status == .authorized || status == .limited
    }

    static func requestAccess() async -> Bool {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        return status == .authorized || status == .limited
    }

    static func save(_ data: Data) async throws -> String {
        let granted: Bool
        if authorizationGranted() {
            granted = true
        } else {
            granted = await requestAccess()
        }
        guard granted else { throw PhotoStoreError.rejected }
        let album = try await albumCollection()
        return try await withCheckedThrowingContinuation { continuation in
            var placeholderID: String?
            PHPhotoLibrary.shared().performChanges({
                let request = PHAssetCreationRequest.forAsset()
                request.addResource(with: .photo, data: data, options: nil)
                if let placeholder = request.placeholderForCreatedAsset {
                    placeholderID = placeholder.localIdentifier
                    if let album, let change = PHAssetCollectionChangeRequest(for: album) {
                        change.addAssets([placeholder] as NSArray)
                    }
                }
            }, completionHandler: { success, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if success, let placeholderID {
                    continuation.resume(returning: placeholderID)
                } else {
                    continuation.resume(throwing: PhotoStoreError.rejected)
                }
            })
        }
    }

    static func list() -> [LibraryPhoto] {
        guard let album = existingAlbum() else { return [] }
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        let result = PHAsset.fetchAssets(in: album, options: options)
        var photos: [LibraryPhoto] = []
        result.enumerateObjects { asset, _, _ in
            photos.append(LibraryPhoto(id: asset.localIdentifier, asset: asset))
        }
        return photos
    }

    static func delete(_ identifier: String) async -> Bool {
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil)
        guard assets.count > 0 else { return false }
        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.deleteAssets(assets)
            }, completionHandler: { success, _ in
                continuation.resume(returning: success)
            })
        }
    }

    static func image(for asset: PHAsset, target: CGSize) async -> UIImage? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.deliveryMode = .highQualityFormat
            options.isNetworkAccessAllowed = true
            options.resizeMode = .fast
            PHImageManager.default().requestImage(for: asset, targetSize: target, contentMode: .aspectFill, options: options) { image, _ in
                continuation.resume(returning: image)
            }
        }
    }

    private static func existingAlbum() -> PHAssetCollection? {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "title = %@", albumTitle)
        return PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: options).firstObject
    }

    private static func albumCollection() async throws -> PHAssetCollection? {
        if let existing = existingAlbum() { return existing }
        var placeholder: PHObjectPlaceholder?
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges({
                let request = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumTitle)
                placeholder = request.placeholderForCreatedAssetCollection
            }, completionHandler: { success, error in
                if let error { continuation.resume(throwing: error) }
                else if success { continuation.resume() }
                else { continuation.resume(throwing: PhotoStoreError.rejected) }
            })
        }
        guard let placeholder else { return nil }
        return PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [placeholder.localIdentifier], options: nil).firstObject
    }
}

enum PhotoStoreError: Error {
    case rejected
    case encode
}
