# Third-party notices

FGC VOD Helper bundles the programs below, unchanged. Each keeps its own license. Source code links are given so you can get the code for any of them.

## FFmpeg 8.1.2 (ffmpeg, ffprobe and their libraries)

- License: LGPL v3 or later.
- Built by the Bytedeco project (`org.bytedeco:ffmpeg:8.1.2-1.5.14`, the non-GPL variant).
- FFmpeg source: https://ffmpeg.org/releases/ffmpeg-8.1.2.tar.xz
- Build script: https://github.com/bytedeco/javacpp-presets/tree/1.5.14/ffmpeg
- The build includes third-party codec libraries (for example OpenH264, libvpx, LAME, Opus, Speex, libaom, SVT-AV1, libwebp, OpenSSL, FreeType, HarfBuzz, libxml2, SRT, zimg, zlib, opencore-amr, vo-amrwbenc). They are under their own permissive or LGPL-compatible licenses, listed in the build script above.

## VLC media player engine 3.0.24 (libVLC, libVLCcore and plugins)

- License: libVLC and libVLCcore are LGPL v2.1 or later. Some plugins are GPL v2 or later.
- Source: https://download.videolan.org/pub/videolan/vlc/3.0.24/vlc-3.0.24.tar.xz
- License texts: https://www.videolan.org/legal.html

## yt-dlp 2026.10.26

- License: The Unlicense (public domain).
- The folder build also contains Python and other components under their own licenses: https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/THIRD_PARTY_LICENSES.txt
- Source: https://github.com/yt-dlp/yt-dlp/tree/2026.10.26

## JNA 5.19.1 (used to load VLC's engine library)

- License: Apache 2.0 or LGPL v2.1 (dual).
- Source: https://github.com/java-native-access/jna

## java-keyring 1.0.4 (stores sign-in details in the macOS Keychain / Windows Credential Manager)

- License: BSD 3-Clause.
- Source: https://github.com/javakeyring/java-keyring
- Comes with: jkeychain 1.1.0 (MIT), JNA Platform 5.13.0 (Apache 2.0 or LGPL v2.1), SLF4J API 2.0.7 (MIT), and, used only on Linux: secret-service 1.8.1 (MIT), dbus-java 4.2.1 (MIT), hkdf 1.1.0 (Apache 2.0).
