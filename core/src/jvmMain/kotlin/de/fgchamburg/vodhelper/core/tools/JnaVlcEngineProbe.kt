package de.fgchamburg.vodhelper.core.tools

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import java.nio.file.Path

/**
 * Loads libvlc by full path with JNA (never a system VLC) and reads its version.
 * It doesn't start an engine instance or load plugins: that's slow, and the player (FGC-12) does it.
 */
class JnaVlcEngineProbe : VlcEngineProbe {
    interface LibVlc : Library {
        fun libvlc_get_version(): String
    }

    override fun rawVersion(libraries: List<Path>): String {
        // libvlccore first, so libvlc's dependency resolves to our copy.
        NativeLibrary.getInstance(libraries[0].toAbsolutePath().toString())
        return Native.load(libraries[1].toAbsolutePath().toString(), LibVlc::class.java).libvlc_get_version()
    }
}
