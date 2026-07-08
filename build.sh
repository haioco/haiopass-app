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
