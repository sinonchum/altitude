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

    struct ReadTags {
        var latitude: Double?
        var longitude: Double?
        var altitude: Double?
        var software: String?
        var dump: String
    }

    static func data(image: UIImage, tags: Tags) -> Data? {
        guard let jpeg = image.jpegData(compressionQuality: 0.95),
              let source = CGImageSourceCreateWithData(jpeg as CFData, nil) else {
            return nil
        }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, UTType.jpeg.identifier as CFString, 1, nil) else {
            return nil
        }
        let properties = NSMutableDictionary()
        if let existing = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) {
            let bridged = existing as NSDictionary
            for (key, value) in bridged {
                properties[key] = value
            }
        }
        let tiff = NSMutableDictionary()
        if let existing = dictionary(properties[kCGImagePropertyTIFFDictionary]) {
            tiff.addEntries(from: existing)
        }
        tiff[kCGImagePropertyTIFFSoftware] = tags.software
        tiff[kCGImagePropertyTIFFDateTime] = formatExifDateTime(tags.epochMs)
        properties[kCGImagePropertyTIFFDictionary] = tiff

        let exif = NSMutableDictionary()
        if let existing = dictionary(properties[kCGImagePropertyExifDictionary]) {
            exif.addEntries(from: existing)
        }
        exif[kCGImagePropertyExifDateTimeOriginal] = formatExifDateTime(tags.epochMs)
        properties[kCGImagePropertyExifDictionary] = exif

        if let gps = gpsDictionary(tags) {
            properties[kCGImagePropertyGPSDictionary] = gps
        }
        properties[kCGImageDestinationLossyCompressionQuality] = 0.95
        CGImageDestinationAddImageFromSource(destination, source, 0, properties)
        guard CGImageDestinationFinalize(destination) else { return nil }
        return output as Data
    }

    static func read(_ data: Data) -> ReadTags {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let raw = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) else {
            return ReadTags(latitude: nil, longitude: nil, altitude: nil, software: nil, dump: "no properties")
        }
        let properties = dictionary(raw) ?? [:]
        let tiff = dictionary(properties[kCGImagePropertyTIFFDictionary as String])
        let software = (tiff?[kCGImagePropertyTIFFSoftware as String] as? String) ?? findSoftware(raw)
        let gps = dictionary(properties[kCGImagePropertyGPSDictionary as String]) ?? [:]
        let latitude = signed(gps[kCGImagePropertyGPSLatitude as String], reference: gps[kCGImagePropertyGPSLatitudeRef as String] as? String, positive: "N")
        let longitude = signed(gps[kCGImagePropertyGPSLongitude as String], reference: gps[kCGImagePropertyGPSLongitudeRef as String] as? String, positive: "E")
        let altitude: Double?
        if let value = doubleValue(gps[kCGImagePropertyGPSAltitude as String]) {
            let reference = gps[kCGImagePropertyGPSAltitudeRef as String]
            let below = (reference as? Int) == 1 || (reference as? NSNumber)?.intValue == 1
            altitude = below ? -value : value
        } else {
            altitude = nil
        }
        return ReadTags(latitude: latitude, longitude: longitude, altitude: altitude, software: software, dump: describe(raw))
    }

    private static func gpsDictionary(_ tags: Tags) -> NSMutableDictionary? {
        guard let latitude = tags.latitude, let longitude = tags.longitude else { return nil }
        let gps = NSMutableDictionary()
        gps[kCGImagePropertyGPSLatitude] = abs(latitude)
        gps[kCGImagePropertyGPSLatitudeRef] = latitude >= 0 ? "N" : "S"
        gps[kCGImagePropertyGPSLongitude] = abs(longitude)
        gps[kCGImagePropertyGPSLongitudeRef] = longitude >= 0 ? "E" : "W"
        if let altitude = tags.altitudeMeters {
            gps[kCGImagePropertyGPSAltitude] = abs(altitude)
            gps[kCGImagePropertyGPSAltitudeRef] = altitude >= 0 ? 0 : 1
        }
        return gps
    }

    private static func dictionary(_ value: Any?) -> [String: Any]? {
        if let dict = value as? [String: Any] { return dict }
        guard let object = value as? NSDictionary else { return nil }
        var result: [String: Any] = [:]
        for (key, entry) in object {
            if let string = key as? String {
                result[string] = entry
            } else if let string = key as? NSString {
                result[string as String] = entry
            }
        }
        return result
    }

    private static func findSoftware(_ value: Any) -> String? {
        guard let object = value as? NSDictionary else { return nil }
        for (key, entry) in object {
            let name = (key as? String) ?? ((key as? NSString).map { $0 as String }) ?? ""
            if name == "Software" || name == (kCGImagePropertyTIFFSoftware as String), let text = entry as? String {
                return text
            }
            if let nested = findSoftware(entry) { return nested }
        }
        return nil
    }

    private static func describe(_ value: Any, depth: Int = 0) -> String {
        guard depth < 4, let object = value as? NSDictionary else { return "" }
        var lines: [String] = []
        for (key, entry) in object {
            let name = (key as? String) ?? ((key as? NSString).map { $0 as String }) ?? "?"
            if entry is NSDictionary {
                lines.append(String(repeating: "  ", count: depth) + name + " {")
                lines.append(describe(entry, depth: depth + 1))
                lines.append(String(repeating: "  ", count: depth) + "}")
            } else {
                lines.append(String(repeating: "  ", count: depth) + "\(name)=\(entry)")
            }
        }
        return lines.joined(separator: "\n")
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
