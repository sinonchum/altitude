import Foundation

enum AlbumQuery {
    static let folder = "Pictures/Altitude"

    static func selection(_ sdkInt: Int) -> (clause: String, args: [String]) {
        if sdkInt >= 29 {
            return ("relative_path LIKE ? AND is_pending = 0", ["%\(folder)%"])
        }
        return ("_data LIKE ?", ["%/\(folder)/%"])
    }
}
