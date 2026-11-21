package de.fgchamburg.vodhelper.buildlogic

/** The OS + CPU a set of bundled tools is for. [resourceDir] is the Compose appResources subfolder name. */
enum class ToolPlatform(val resourceDir: String, val bytedecoClassifier: String, val isWindows: Boolean) {
    MACOS_ARM64("macos-arm64", "macosx-arm64", false),
    MACOS_X64("macos-x64", "macosx-x86_64", false),
    WINDOWS_X64("windows-x64", "windows-x86_64", true);

    companion object {
        /** null when we don't ship tools for this host (e.g. Linux). */
        fun host(osName: String, osArch: String): ToolPlatform? {
            val arch = when (osArch.lowercase()) { "aarch64", "arm64" -> "arm64"; "x86_64", "amd64" -> "x64"; else -> return null }
            val os = osName.lowercase()
            return when {
                os.startsWith("mac") && arch == "arm64" -> MACOS_ARM64
                os.startsWith("mac") && arch == "x64" -> MACOS_X64
                os.startsWith("windows") && arch == "x64" -> WINDOWS_X64
                else -> null
            }
        }
    }
}
