import Foundation

protocol GeoidModel {
    /// Geoid undulation N in meters. Orthometric height H = ellipsoidal h − N.
    func undulationMeters(latitudeDegrees: Double, longitudeDegrees: Double) -> Double
}

func resolveMsl(_ sample: GnssSample, _ geoid: GeoidModel?) -> Double? {
    if let platform = sample.platformMslAltitudeMeters { return platform }
    guard let ellipsoidal = sample.ellipsoidalAltitudeMeters, let geoid else { return nil }
    return ellipsoidal - geoid.undulationMeters(latitudeDegrees: sample.latitude, longitudeDegrees: sample.longitude)
}

/// EGM96 15-arc-minute geoid, stored as little-endian centimeters.
/// Row 0 is 90°N. Longitude starts at 0° and runs east.
final class Egm96Geoid: GeoidModel {
    let rows: Int
    let cols: Int
    let lat0: Double
    let lon0: Double
    let latStep: Double
    let lonStep: Double
    let centimeters: [Int16]

    init(rows: Int, cols: Int, lat0: Double, lon0: Double, latStep: Double, lonStep: Double, centimeters: [Int16]) {
        precondition(rows >= 2 && cols >= 2)
        precondition(centimeters.count == rows * cols)
        precondition(latStep != 0 && lonStep != 0)
        self.rows = rows
        self.cols = cols
        self.lat0 = lat0
        self.lon0 = lon0
        self.latStep = latStep
        self.lonStep = lonStep
        self.centimeters = centimeters
    }

    func undulationMeters(latitudeDegrees: Double, longitudeDegrees: Double) -> Double {
        let minLat = min(lat0, lat0 + Double(rows - 1) * latStep)
        let maxLat = max(lat0, lat0 + Double(rows - 1) * latStep)
        let lat = min(max(latitudeDegrees, minLat), maxLat)
        var lon = longitudeDegrees.truncatingRemainder(dividingBy: 360)
        if lon < 0 { lon += 360 }

        let rowPos = min(max((lat - lat0) / latStep, 0), Double(rows - 1))
        let r0 = min(max(Int(floor(rowPos)), 0), rows - 1)
        let r1 = r0 >= rows - 1 ? r0 : r0 + 1
        let ty = r0 == r1 ? 0.0 : rowPos - Double(r0)

        var colPos = (lon - lon0) / lonStep
        colPos = colPos.truncatingRemainder(dividingBy: Double(cols))
        if colPos < 0 { colPos += Double(cols) }
        let c0 = floorMod(Int(floor(colPos)), cols)
        let c1 = floorMod(c0 + 1, cols)
        let tx = colPos - floor(colPos)

        let v00 = at(r0, c0)
        let v01 = at(r0, c1)
        let v10 = at(r1, c0)
        let v11 = at(r1, c1)
        return v00 * (1 - tx) * (1 - ty) + v01 * tx * (1 - ty) + v10 * (1 - tx) * ty + v11 * tx * ty
    }

    private func at(_ row: Int, _ col: Int) -> Double {
        Double(centimeters[row * cols + col]) / 100.0
    }

    static func decode(_ bytes: Data) throws -> Egm96Geoid {
        guard bytes.count >= 32 else { throw GeoidError.truncated }
        let magic = [UInt8](bytes.prefix(4))
        guard magic == [UInt8(ascii: "E"), UInt8(ascii: "G"), UInt8(ascii: "9"), UInt8(ascii: "6")] else {
            throw GeoidError.badMagic
        }
        var offset = 4
        let version = readUInt16(bytes, &offset)
        guard version == 1 else { throw GeoidError.badVersion }
        _ = readUInt16(bytes, &offset)
        let rows = Int(readUInt16(bytes, &offset))
        let cols = Int(readUInt16(bytes, &offset))
        let lat0 = Double(readInt32(bytes, &offset)) / 10_000.0
        let lon0 = Double(readInt32(bytes, &offset)) / 10_000.0
        let latStep = Double(readInt32(bytes, &offset)) / 10_000.0
        let lonStep = Double(readInt32(bytes, &offset)) / 10_000.0
        _ = readInt32(bytes, &offset)
        let count = rows * cols
        guard bytes.count == 32 + count * 2 else { throw GeoidError.badSize }
        var centimeters = [Int16]()
        centimeters.reserveCapacity(count)
        for _ in 0..<count {
            centimeters.append(readInt16(bytes, &offset))
        }
        return Egm96Geoid(
            rows: rows,
            cols: cols,
            lat0: lat0,
            lon0: lon0,
            latStep: latStep,
            lonStep: lonStep,
            centimeters: centimeters
        )
    }
}

enum GeoidError: Error {
    case truncated
    case badMagic
    case badVersion
    case badSize
}

func floorMod(_ value: Int, _ modulus: Int) -> Int {
    let remainder = value % modulus
    return remainder < 0 ? remainder + modulus : remainder
}

private func readUInt16(_ data: Data, _ offset: inout Int) -> UInt16 {
    let value = UInt16(data[offset]) | (UInt16(data[offset + 1]) << 8)
    offset += 2
    return value
}

private func readInt16(_ data: Data, _ offset: inout Int) -> Int16 {
    Int16(bitPattern: readUInt16(data, &offset))
}

private func readInt32(_ data: Data, _ offset: inout Int) -> Int32 {
    let value = UInt32(data[offset])
        | (UInt32(data[offset + 1]) << 8)
        | (UInt32(data[offset + 2]) << 16)
        | (UInt32(data[offset + 3]) << 24)
    offset += 4
    return Int32(bitPattern: value)
}
