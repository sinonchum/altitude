import Photos
import PhotosUI
import SwiftUI
import UIKit

struct ImportPage: Identifiable {
    var id: String
    var source: Data
    var preview: UIImage
    var capture: PhotoCapture
    var altitude: SelectedAltitude?
    var place: String?
    var locationRedacted: Bool
}

enum PhotoImporter {
    static let previewEdge: CGFloat = 1_600
    static let saveEdge: CGFloat = 12_000
    /// PhotosPicker is bound to the shared library so each item has an asset identifier.
    static let usesSharedPhotoLibrary = true

    static func makePage(
        id: String,
        data: Data?,
        assetLatitude: Double?,
        assetLongitude: Double?,
        assetWasReadable: Bool
    ) -> ImportPage? {
        guard let data, let image = UIImage(data: data) else { return nil }
        let read = PhotoJPEG.capture(from: data)
        let merged = mergePhotoLocation(
            exifLatitude: read.latitude,
            exifLongitude: read.longitude,
            assetLatitude: assetLatitude,
            assetLongitude: assetLongitude,
            assetWasReadable: assetWasReadable
        )
        var capture = read
        capture.latitude = merged.latitude
        capture.longitude = merged.longitude
        let altitude: SelectedAltitude?
        if capture.exifAltitudeMeters != nil || !capture.hasCoordinates {
            altitude = selectPhotoAltitude(
                exifAltitudeMeters: capture.exifAltitudeMeters,
                elevationLookupMeters: nil,
                hasCoordinates: capture.hasCoordinates
            )
        } else {
            altitude = nil
        }
        return ImportPage(
            id: id,
            source: data,
            preview: image.upright(maxEdge: previewEdge),
            capture: capture,
            altitude: altitude,
            place: nil,
            locationRedacted: merged.redacted
        )
    }

    static func facts(for item: PhotosPickerItem) async -> (data: Data?, latitude: Double?, longitude: Double?, readable: Bool) {
        if let identifier = item.itemIdentifier {
            let assets = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil)
            if let asset = assets.firstObject {
                // Limited library access still returns location for an asset the person just picked.
                let data = await originalData(asset) ?? (try? await item.loadTransferable(type: Data.self))
                return (data, asset.location?.coordinate.latitude, asset.location?.coordinate.longitude, true)
            }
        }
        let data = try? await item.loadTransferable(type: Data.self)
        return (data, nil, nil, false)
    }

    static func originalData(_ asset: PHAsset) async -> Data? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.version = .original
            options.isNetworkAccessAllowed = true
            options.deliveryMode = .highQualityFormat
            options.isSynchronous = false
            let gate = ResumeOnce()
            PHImageManager.default().requestImageDataAndOrientation(for: asset, options: options) { data, _, _, info in
                let cancelled = (info?[PHImageCancelledKey] as? Bool) == true
                let degraded = (info?[PHImageResultIsDegradedKey] as? Bool) == true
                if degraded && !cancelled { return }
                gate.resume(continuation, cancelled ? nil : data)
            }
        }
    }
}

private final class ResumeOnce: @unchecked Sendable {
    private let lock = NSLock()
    private var done = false

    func resume(_ continuation: CheckedContinuation<Data?, Never>, _ data: Data?) {
        lock.lock()
        let should = !done
        done = true
        lock.unlock()
        if should { continuation.resume(returning: data) }
    }
}

enum ImportFixtures {
    static func page(kind: String, place: String) -> ImportPage {
        switch kind {
        case "landscape":
            return make(width: 1280, height: 800, latitude: 29.65218, longitude: 91.117, altitude: 5210, place: place, computed: false)
        case "nogps":
            return make(width: 800, height: 1100, latitude: nil, longitude: nil, altitude: nil, place: nil, computed: false)
        case "computed":
            return make(width: 800, height: 1100, latitude: 29.65218, longitude: 91.117, altitude: nil, place: place, computed: true)
        default:
            return make(width: 800, height: 1100, latitude: 29.65218, longitude: 91.117, altitude: 5210, place: place, computed: false)
        }
    }

    private static func make(
        width: Int,
        height: Int,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        place: String?,
        computed: Bool
    ) -> ImportPage {
        let image = gradient(width: width, height: height)
        let data = PhotoJPEG.data(
            image: image,
            tags: PhotoJPEG.Tags(
                latitude: latitude,
                longitude: longitude,
                altitudeMeters: altitude,
                epochMs: 0,
                dateTimeOriginal: "2024:06:15 14:30:00",
                offsetTimeOriginal: "+08:00"
            )
        ) ?? Data()
        var page = PhotoImporter.makePage(
            id: "fixture-\(width)x\(height)-\(computed)",
            data: data,
            assetLatitude: nil,
            assetLongitude: nil,
            assetWasReadable: true
        ) ?? ImportPage(
            id: "fixture-empty",
            source: data,
            preview: image,
            capture: PhotoCapture(latitude: latitude, longitude: longitude, exifAltitudeMeters: altitude, clock: nil, orientation: .normal),
            altitude: nil,
            place: nil,
            locationRedacted: false
        )
        if computed {
            page.altitude = SelectedAltitude(meters: 3648, provenance: .computed)
        }
        page.place = place
        return page
    }

    private static func gradient(width: Int, height: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            let colors = [
                UIColor(red: 0.16, green: 0.28, blue: 0.42, alpha: 1).cgColor,
                UIColor(red: 0.62, green: 0.72, blue: 0.78, alpha: 1).cgColor,
                UIColor(red: 0.83, green: 0.62, blue: 0.42, alpha: 1).cgColor,
                UIColor(red: 0.45, green: 0.38, blue: 0.30, alpha: 1).cgColor,
            ] as CFArray
            let space = CGColorSpaceCreateDeviceRGB()
            if let gradient = CGGradient(colorsSpace: space, colors: colors, locations: [0, 0.42, 0.62, 1]) {
                context.cgContext.drawLinearGradient(
                    gradient,
                    start: .zero,
                    end: CGPoint(x: 0, y: height),
                    options: []
                )
            }
        }
    }
}
