#!/bin/bash
# Quick system image installer using direct download
# This is faster than sdkmanager for slow connections

set -e

ANDROID_SDK_ROOT=/usr/lib/android-sdk
SYSTEM_IMAGE_DIR="$ANDROID_SDK_ROOT/system-images/android-34/google_apis/arm64-v8a"
ZIP_FILE="/tmp/arm64-v8a-34_r13.zip"
URL="https://dl.google.com/android/repository/sys-img/google_apis/arm64-v8a-34_r13.zip"

echo "=== System Image Quick Installer ==="
echo ""

# Check if already installed
if [ -d "$SYSTEM_IMAGE_DIR" ] && [ -f "$SYSTEM_IMAGE_DIR/system.img" ]; then
    echo "System image already installed!"
    exit 0
fi

# Download with resume support
echo "Downloading system image (1.5GB)..."
echo "This may take a while. The download supports resume if interrupted."
echo ""

# Try to download with resume
if [ -f "$ZIP_FILE" ]; then
    echo "Resuming previous download..."
    curl -L -C - -o "$ZIP_FILE" "$URL"
else
    curl -L -o "$ZIP_FILE" "$URL"
fi

if [ $? -ne 0 ]; then
    echo ""
    echo "Download failed or incomplete. You can resume by running this script again."
    echo "Or download manually from: $URL"
    echo "Then extract to: $SYSTEM_IMAGE_DIR"
    exit 1
fi

# Verify download
if [ ! -f "$ZIP_FILE" ]; then
    echo "Download failed!"
    exit 1
fi

echo ""
echo "Download complete. Extracting..."

# Create target directory
sudo mkdir -p "$SYSTEM_IMAGE_DIR"

# Extract
sudo unzip -q -o "$ZIP_FILE" -d "$SYSTEM_IMAGE_DIR"

# Fix permissions
sudo chmod -R a+rX "$SYSTEM_IMAGE_DIR"

echo ""
echo "System image installed successfully!"
echo ""
echo "Now create the AVD:"
echo "  avdmanager create avd -n test_avd -k 'system-images;android-34;google_apis;arm64-v8a'"
echo ""
echo "Then start the emulator:"
echo "  /usr/lib/android-sdk/emulator/emulator -avd test_avd"
