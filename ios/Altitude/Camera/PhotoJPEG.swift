import ImageIO
import UIKit
import UniformTypeIdentifiers

enum PhotoJPEG {
    struct Tags {
        var latitude: Double?
        var longitude: Double?
        var altitudeMeters: Double?
        var epochMs: Int64
        var software: String = "Altitude"
    }

    static func data(image: UIImage, tags: Tags) -> Data? {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let rendered = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
        guard let cgImage = rendered.cgImage else { return nil }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, UTType.jpeg.identifier as CFString, 1, nil) else {
            return nil
        }
        var properties: [CFString: Any] = [
            kCGImageDestinationLossyCompressionQuality: 0.95,
            kCGImagePropertyTIFFDictionary: tiff(tags),
            kCGImagePropertyExifDictionary: [
                kCGImagePropertyExifDateTimeOriginal: formatExifDateTime(tags.epochMs),
            ],
        ]
        if let gps = gpsDictionary(tags) {
            properties[kCGImagePropertyGPSDictionary] = gps
        }
        CGImageDestinationAddImage(destination, cgImage, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { return nil }
        return output as Data
    }

    static func read(_ data: Data) -> (latitude: Double?, longitude: Double?, altitude: Double?, software: String?) {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else {
            return (nil, nil, nil, nil)
        }
        let tiff = properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
        let software = tiff?[kCGImagePropertyTIFFSoftware] as? String
        guard let gps = properties[kCGImagePropertyGPSDictionary] as? [CFString: Any] else {
            return (nil, nil, nil, software)
        }
        let latitude = signed(gps[kCGImagePropertyGPSLatitude], reference: gps[kCGImagePropertyGPSLatitudeRef] as? String, positive: "N")
        let longitude = signed(gps[kCGImagePropertyGPSLongitude], reference: gps[kCGImagePropertyGPSLongitudeRef] as? String, positive: "E")
        let altitude: Double?
        if let raw = doubleValue(gps[kCGImagePropertyGPSAltitude]) {
            let below = (gps[kCGImagePropertyGPSAltitudeRef] as? Int) == 1
                || (gps[kCGImagePropertyGPSAltitudeRef] as? NSNumber)?.intValue == 1
            altitude = below ? -raw : raw
        } else {
            altitude = nil
        }
        return (latitude, longitude, altitude, software)
    }

    private static func tiff(_ tags: Tags) -> [CFString: Any] {
        [
            kCGImagePropertyTIFFSoftware: tags.software,
            kCGImagePropertyTIFFDateTime: formatExifDateTime(tags.epochMs),
        ]
    }

    private static func gpsDictionary(_ tags: Tags) -> [CFString: Any]? {
        guard let latitude = tags.latitude, let longitude = tags.longitude else { return nil }
        var gps: [CFString: Any] = [
            kCGImagePropertyGPSLatitude: abs(latitude),
            kCGImagePropertyGPSLatitudeRef: latitude >= 0 ? "N" : "S",
            kCGImagePropertyGPSLongitude: abs(longitude),
            kCGImagePropertyGPSLongitudeRef: longitude >= 0 ? "E" : "W",
        ]
        if let altitude = tags.altitudeMeters {
            gps[kCGImagePropertyGPSAltitude] = abs(altitude)
            gps[kCGImagePropertyGPSAltitudeRef] = altitude >= 0 ? 0 : 1
        }
        return gps
    }

    private static func signed(_ value: Any?, reference: String?, positive: String) -> Double? {
        guard let number = doubleValue(value) else { return nil }
        if reference == positive { return number }
        return -number
    }

    private static func doubleValue(_ value: Any?) -> Double? {
        if let number = value as? NSNumber { return number.doubleValue }
        if let number = value as? Double { return number }
        return nil
    }
}
