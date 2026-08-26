#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Android SDK location
export ANDROID_HOME="${ANDROID_HOME:-/usr/lib/android-sdk}"
export SDK_ROOT="$ANDROID_HOME"

# Unset proxy env vars that HaioBypass desktop app sets via _JAVA_OPTIONS
# dl.google.com requires the system proxy to resolve Maven artifacts,
# so we let _JAVA_OPTIONS propagate naturally (set by the desktop app).
# If building outside that environment, ensure http_proxy/https_proxy are set.

# Gradle JVM args
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx2048m -Dfile.encoding=UTF-8}"

BUILD_TYPE="${1:-debug}"

echo "========================================="
echo " HaioBypass Android Build"
echo " Build type: $BUILD_TYPE"
echo " SDK: $ANDROID_HOME"
echo "========================================="

# Verify SDK
if [ ! -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
    echo "ERROR: Android SDK Platform 34 not found at $ANDROID_HOME/platforms/android-34/"
    echo "Run the SDK setup steps first (see PLAN.md)."
    exit 1
fi

if [ ! -f "$ANDROID_HOME/build-tools/34.0.0/aapt2" ] && [ ! -f "$ANDROID_HOME/build-tools/34.0.0/aapt2.exe" ]; then
    echo "ERROR: Android SDK Build-Tools 34.0.0 not found at $ANDROID_HOME/build-tools/34.0.0/"
    exit 1
fi

# Pre-patch tun2socks ELF header at build time (ET_EXEC -> ET_DYN, TLS alignment)
# This avoids runtime patching on the device which triggers Android 14+ W^X enforcement.
ASSETS_DIR="app/src/main/assets"
TUN2SOCKS_SRC="$ASSETS_DIR/tun2socks_arm64_v8a"
if [ -f "$TUN2SOCKS_SRC" ]; then
    echo "Pre-patching tun2socks ELF header..."
    python3 - "$TUN2SOCKS_SRC" <<'PYEOF'
import sys, struct

path = sys.argv[1]
with open(path, "rb") as f:
    data = bytearray(f.read())

# ELF header: e_type at offset 16 (2 bytes, little-endian)
e_type = struct.unpack_from("<H", data, 16)[0]
print(f"  e_type before: 0x{e_type:04X} (ET_EXEC=2, ET_DYN=3)")
if e_type == 2:
    struct.pack_into("<H", data, 16, 3)
    print("  -> Patched to ET_DYN (3)")

# Walk program headers to find PT_TLS and patch p_align
e_phoff = struct.unpack_from("<Q", data, 32)[0]
e_phentsize = struct.unpack_from("<H", data, 54)[0]
e_phnum = struct.unpack_from("<H", data, 56)[0]
PT_TLS = 7
MIN_TLS_ALIGN = 64
for i in range(e_phnum):
    off = e_phoff + i * e_phentsize
    p_type = struct.unpack_from("<I", data, off)[0]
    if p_type == PT_TLS:
        p_align = struct.unpack_from("<Q", data, off + 48)[0]
        if p_align < MIN_TLS_ALIGN:
            struct.pack_into("<Q", data, off + 48, MIN_TLS_ALIGN)
            print(f"  -> Patched PT_TLS p_align: {p_align} -> {MIN_TLS_ALIGN}")
        break

with open(path, "wb") as f:
    f.write(data)
print("  ELF patching complete.")
PYEOF
else
    echo "WARNING: tun2socks_arm64_v8a not found in assets, skipping ELF pre-patch"
fi

# Build
if [ "$BUILD_TYPE" = "release" ]; then
    echo "Building release APK..."
    ./gradlew assembleRelease --no-daemon
    APK_DIR="app/build/outputs/apk/release"
    APK_NAME="app-release.apk"
else
    echo "Building debug APK..."
    ./gradlew assembleDebug --no-daemon
    APK_DIR="app/build/outputs/apk/debug"
    APK_NAME="app-debug.apk"
fi

APK_PATH="$APK_DIR/$APK_NAME"

if [ -f "$APK_PATH" ]; then
    SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo ""
    echo "========================================="
    echo " BUILD SUCCESSFUL"
    echo " APK: $APK_PATH ($SIZE)"
    echo "========================================="
    echo ""
    echo "To install on a connected device:"
    echo "  adb install $APK_PATH"
else
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi
