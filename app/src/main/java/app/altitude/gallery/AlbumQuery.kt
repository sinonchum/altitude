package app.altitude.gallery

object AlbumQuery {
    const val FOLDER = "Pictures/Altitude"

    fun selection(sdkInt: Int): Pair<String, Array<String>> {
        return if (sdkInt >= 29) {
            "relative_path LIKE ? AND is_pending = 0" to arrayOf("%$FOLDER%")
        } else {
            "_data LIKE ?" to arrayOf("%/$FOLDER/%")
        }
    }
}
