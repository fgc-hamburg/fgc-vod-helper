package de.fgchamburg.vodhelper.core.tools

import java.nio.file.Path

/** Loads VLC's engine library. Tests use a fake. */
fun interface VlcEngineProbe {
    /**
     * Loads the libraries (in the given order) and returns libvlc's raw version string; throws (any Throwable) if it can't.
     * Plugins are not loaded here: that is slow and belongs to the player (FGC-12).
     */
    fun rawVersion(libraries: List<Path>): String
}
