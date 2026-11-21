package de.fgchamburg.vodhelper.core.tools

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.nio.file.Path

/**
 * Loads libvlc by full path with JNA (never a system VLC), points it at the bundled plugins,
 * and creates one engine instance. That fails if the plugins don't load, so it proves the engine works.
 */
class JnaVlcEngineProbe(private val os: HostOs) : VlcEngineProbe {
    interface CLib : Library {
        fun setenv(name: String, value: String, overwrite: Int): Int
    }

    interface MsvcRt : Library {
        fun _putenv(assignment: String): Int
    }

    interface LibVlc : Library {
        fun libvlc_get_version(): String
        fun libvlc_new(argc: Int, argv: Array<String>?): Pointer?
        fun libvlc_release(instance: Pointer)
    }

    override fun rawVersion(libraries: List<Path>, pluginDir: Path): String {
        val plugins = pluginDir.toAbsolutePath().toString()
        when (os) {
            HostOs.MAC -> Native.load("c", CLib::class.java).setenv("VLC_PLUGIN_PATH", plugins, 1)
            HostOs.WINDOWS -> Native.load("msvcrt", MsvcRt::class.java)._putenv("VLC_PLUGIN_PATH=$plugins")
        }
        // libvlccore first, so libvlc's dependency resolves to our copy.
        NativeLibrary.getInstance(libraries[0].toAbsolutePath().toString())
        val vlc = Native.load(libraries[1].toAbsolutePath().toString(), LibVlc::class.java)
        val version = vlc.libvlc_get_version()
        val instance = vlc.libvlc_new(0, null) ?: throw IllegalStateException("plugins didn't load")
        vlc.libvlc_release(instance)
        return version
    }
}
