package de.fgchamburg.vodhelper.core.settings

import de.fgchamburg.vodhelper.core.tools.HostOs
import java.nio.file.Path
import java.nio.file.Paths

object AppDataDir {
    const val FOLDER = "FGC VOD Helper"

    fun resolve(os: HostOs, userHome: String, appDataEnv: String?): Path = when (os) {
        HostOs.MAC -> Paths.get(userHome, "Library", "Application Support", FOLDER)
        HostOs.WINDOWS ->
            if (appDataEnv != null) Paths.get(appDataEnv, FOLDER)
            else Paths.get(userHome, "AppData", "Roaming", FOLDER)
    }

    /** Linux isn't shipped; it only keeps dev and CI from crashing. */
    fun current(): Path {
        val home = System.getProperty("user.home")
        val os = HostOs.current() ?: return Paths.get(home, ".fgc-vod-helper")
        return resolve(os, home, System.getenv("APPDATA"))
    }
}
