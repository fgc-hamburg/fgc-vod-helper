package de.fgchamburg.vodhelper.core.tools

import java.nio.file.Path

/** Loads VLC's engine. Tests use a fake. */
fun interface VlcEngineProbe {
    /** Loads the engine and returns libvlc's raw version string; throws (any Throwable) if it can't. */
    fun rawVersion(libraries: List<Path>, pluginDir: Path): String
}
