#!/bin/bash
# Android Emulator Setup Script
# Run this script to set up the Android emulator for HaioBypass development

set -e

echo "=== Android Emulator Setup ==="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check if running as root
if [ "$EUID" -eq 0 ]; then
    log_error "Please run this script as a regular user, not root"
    exit 1
fi

# Check KVM
if [ ! -e /dev/kvm ]; then
    log_error "KVM not available. Android emulator requires hardware virtualization."
    exit 1
fi

if ! groups | grep -q '\bkvm\b'; then
    log_warn "User not in kvm group. Run: sudo usermod -aG kvm $USER && newgrp kvm"
fi

# Set up environment
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools

log_info "Step 1: Checking sdkmanager..."
if ! command -v sdkmanager &> /dev/null; then
    log_error "sdkmanager not found. Please install Android SDK command-line tools first."
    exit 1
fi
log_info "sdkmanager found"

log_info "Step 2: Installing emulator and system image..."
log_warn "This will download ~1.5GB. This may take a while depending on your network."

# Check if emulator already exists
if [ -d "$ANDROID_SDK_ROOT/emulator" ] && [ -f "$ANDROID_SDK_ROOT/emulator/emulator" ]; then
    log_info "Emulator already installed, skipping..."
else
    log_info "Installing emulator..."
    for i in 1 2 3; do
        log_info "Attempt $i/3..."
        if yes | sudo sdkmanager --no_https "emulator" 2>&1; then
            log_info "Emulator installed!"
            break
        else
            log_warn "Attempt $i failed. Retrying..."
            sleep 5
        fi
    done
fi

# Install system image
log_info "Installing ARM64 system image (this is the large download ~1.5GB)..."
log_warn "If this fails, you can download it manually or use a faster mirror."

for i in 1 2 3; do
    log_info "System image download attempt $i/3..."
    if yes | sudo sdkmanager --no_https "system-images;android-34;google_apis;arm64-v8a" 2>&1; then
        log_info "System image installed!"
        break
    else
        log_warn "Attempt $i failed. Retrying..."
        sleep 5
    fi
done

if [ $i -eq 3 ]; then
    log_error "System image installation failed after 3 attempts."
    echo ""
    echo "Alternative: Download manually from:"
    echo "  https://dl.google.com/android/repository/sys-img/google_apis/arm64-v8a-34_r13.zip"
    echo ""
    echo "Then extract to: $ANDROID_SDK_ROOT/system-images/android-34/google_apis/arm64-v8a/"
    exit 1
fi

log_info "Step 3: Creating Android Virtual Device (AVD)..."
if avdmanager list avd 2>/dev/null | grep -q "test_avd"; then
    log_warn "AVD 'test_avd' already exists. Deleting and recreating..."
    echo "no" | avdmanager delete avd -n test_avd 2>/dev/null || true
fi

echo "no" | avdmanager create avd -n test_avd -k "system-images;android-34;google_apis;arm64-v8a" --force

if [ $? -eq 0 ]; then
    log_info "AVD created successfully!"
else
    log_error "Failed to create AVD"
    exit 1
fi

log_info "Step 4: Verifying installation..."
echo ""
echo "Installed AVDs:"
avdmanager list avd 2>/dev/null | grep -A 2 "test_avd" || log_warn "AVD not found"
echo ""
echo "Emulator version:"
$ANDROID_SDK_ROOT/emulator/emulator -version 2>&1 | head -1 || log_warn "Emulator not found"
echo ""

log_info "=== Setup Complete ==="
echo ""
echo "To start the emulator:"
echo "  $ANDROID_SDK_ROOT/emulator/emulator -avd test_avd"
echo ""
echo "To build and install the app:"
echo "  cd /home/devcloud/Documents/mine/Haio-Forever/HaioBypass-android"
echo "  ./build.sh"
echo "  adb wait-for-device"
echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To view logs:"
echo "  adb logcat | grep -iE 'haio|bypass|vpn|dns|xray'"
echo ""
echo "Note: ARM64 emulation on x86_64 is slow. Expect 30-60 second boot time."
echo "VPN functionality will NOT work in the emulator (Android limitation)."
