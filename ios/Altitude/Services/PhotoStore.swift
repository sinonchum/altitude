import Photos
import UIKit

struct LibraryPhoto: Identifiable, Equatable {
    var id: String
    var asset: PHAsset

    static func == (lhs: LibraryPhoto, rhs: LibraryPhoto) -> Bool { lhs.id == rhs.id }
}

enum PhotoStore {
    static let albumTitle = "Altitude"

    /// Saving a new photo needs add-only access. It does not ask to read the library.
    static func canAdd() -> Bool {
        if PHPhotoLibrary.authorizationStatus(for: .addOnly) == .authorized { return true }
        let read = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return read == .authorized || read == .limited
    }

    static func requestAddOnly() async -> Bool {
        if canAdd() { return true }
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        return status == .authorized || canAdd()
    }

    static func readStatus() -> PHAuthorizationStatus {
        PHPhotoLibrary.authorizationStatus(for: .readWrite)
    }

    static func canRead() -> Bool {
        let status = readStatus()
        return status == .authorized || status == .limited
    }

    /// Read access is requested only from the gallery or import explanation, never at launch.
    static func requestRead() async -> PHAuthorizationStatus {
        await PHPhotoLibrary.requestAuthorization(for: .readWrite)
    }

    static func save(_ data: Data) async throws -> String {
        let granted = await requestAddOnly()
        guard granted else { throw PhotoStoreError.rejected }
        // Fetching an album while read access is undetermined presents the full-access dialog.
        let album = canRead() ? existingAlbum() : nil
        let createAlbum = album == nil && (canRead() || !albumWasCreated)
        let identifier: String = try await withCheckedThrowingContinuation { continuation in
            var placeholderID: String?
            PHPhotoLibrary.shared().performChanges({
                let request = PHAssetCreationRequest.forAsset()
                request.addResource(with: .photo, data: data, options: nil)
                guard let placeholder = request.placeholderForCreatedAsset else { return }
                placeholderID = placeholder.localIdentifier
                if let album, let change = PHAssetCollectionChangeRequest(for: album) {
                    change.addAssets([placeholder] as NSArray)
                } else if createAlbum {
                    let created = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumTitle)
                    created.addAssets([placeholder] as NSArray)
                }
            }, completionHandler: { success, error in
                if createAlbum && success {
                    UserDefaults.standard.set(true, forKey: albumCreatedKey)
                }
                if success, let placeholderID, album == nil, !createAlbum {
                    var waiting = pendingAlbumAssetIDs
                    waiting.append(placeholderID)
                    pendingAlbumAssetIDs = waiting
                }
                if let error {
                    continuation.resume(throwing: error)
                } else if success, let placeholderID {
                    continuation.resume(returning: placeholderID)
                } else {
                    continuation.resume(throwing: PhotoStoreError.rejected)
                }
            })
        }
        if canRead() { await adoptPending() }
        return identifier
    }

    /// Files photos saved under add-only access into the Altitude album once reading is allowed.
    static func adoptPending() async {
        guard canRead(), let album = existingAlbum() else { return }
        let ids = pendingAlbumAssetIDs
        guard !ids.isEmpty else { return }
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)
        var found: [String] = []
        assets.enumerateObjects { asset, _, _ in found.append(asset.localIdentifier) }
        guard !found.isEmpty else { return }
        let added = await withCheckedContinuation { continuation in
            PHPhotoLibrary.shared().performChanges({
                if let change = PHAssetCollectionChangeRequest(for: album) {
                    change.addAssets(assets)
                }
            }, completionHandler: { success, _ in
                continuation.resume(returning: success)
            })
        }
        if added {
            pendingAlbumAssetIDs = ids.filter { !found.contains($0) }
        }
    }

    static func list() -> [LibraryPhoto] {
        // Fetching the album while access is undetermined presents the system dialog. Callers ask first.
        guard canRead() else { return [] }
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

    private static let albumCreatedKey = "altitude.photoAlbumCreated"
    private static let pendingAlbumKey = "altitude.pendingAlbumAssetIDs"

    private static var albumWasCreated: Bool {
        UserDefaults.standard.bool(forKey: albumCreatedKey)
    }

    private static var pendingAlbumAssetIDs: [String] {
        get { UserDefaults.standard.stringArray(forKey: pendingAlbumKey) ?? [] }
        set { UserDefaults.standard.set(newValue, forKey: pendingAlbumKey) }
    }

    private static func existingAlbum() -> PHAssetCollection? {
        guard canRead() else { return nil }
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "title = %@", albumTitle)
        return PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: options).firstObject
    }
}

enum PhotoStoreError: Error {
    case rejected
    case encode
}
