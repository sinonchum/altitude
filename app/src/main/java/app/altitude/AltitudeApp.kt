package app.altitude

import android.app.Application
import app.altitude.ui.theme.AltitudeTypefaces

class AltitudeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AltitudeTypefaces.load(this)
    }
}
