# HaioBypass Android - Build Instructions

## Prerequisites

### Android SDK Setup

The build requires Android SDK Platform 34 and Build-Tools 34.0.0.

If not already installed, set up the SDK at `/usr/lib/android-sdk`:

```bash
# Platform 34
sudo mkdir -p /usr/lib/android-sdk/platforms
cd /usr/lib/android-sdk/platforms
sudo curl -sL "https://dl.google.com/android/repository/platform-34-ext7_r03.zip" -o /tmp/platform-34.zip
sudo unzip -q /tmp/platform-34.zip
sudo chmod -R a+rX android-34

# Build-Tools 34
sudo mkdir -p /usr/lib/android-sdk/build-tools
cd /usr/lib/android-sdk/build-tools
sudo curl -sL "https://dl.google.com/android/repository/build-tools_r34-linux.zip" -o /tmp/build-tools-34.zip
sudo unzip -q /tmp/build-tools-34.zip
sudo ln -sf android-14 34.0.0
sudo chmod -R a+rX android-14

# Licenses
sudo mkdir -p /usr/lib/android-sdk/licenses
echo -e "\n24333f8a63b6825ea9c5514f83c2829b004d1fee" | sudo tee /usr/lib/android-sdk/licenses/android-sdk-license
echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" | sudo tee /usr/lib/android-sdk/licenses/android-sdk-preview-license
```

Verify `local.properties` points to the SDK:
```
sdk.dir=/usr/lib/android-sdk
```

### Java

Requires JDK 17+. JDK 21 is recommended.

```bash
java -version   # should show openjdk 17+ or 21+
```

### Network

The build requires access to Google Maven (`dl.google.com/dl/android/maven2/`) and Maven Central (`repo.maven.apache.org`). If you're behind a proxy (e.g., HaioBypass desktop app), ensure the proxy is running so these repositories are reachable.

## Quick Build

```bash
./build.sh           # debug build
./build.sh release   # release build
```

The script:
1. Verifies Android SDK Platform 34 and Build-Tools 34.0.0 are installed
2. Runs Gradle with `--no-daemon`
3. Outputs the APK path on success

**Output:**
```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## Manual Build

```bash
./gradlew assembleDebug --no-daemon    # debug
./gradlew assembleRelease --no-daemon  # release
```

## Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or for release:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

## Build Output

| Artifact | Path |
|----------|------|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| Build metadata | `app/build/outputs/apk/*/output-metadata.json` |

## Project Structure

```
HaioBypass-android/
├── build.sh                          # Build script
├── build.gradle.kts                  # Root build config
├── settings.gradle.kts               # Project settings
├── gradle.properties                 # Gradle + SDK config
├── gradle/libs.versions.toml         # Dependency versions
├── app/
│   ├── build.gradle.kts              # App module config
│   ├── proguard-rules.pro            # R8 minification rules
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/haio/bypass/
│       │   ├── config/               # Config persistence + Trojan URL parser
│       │   ├── domain/               # Domain fetch, store, router
│       │   ├── proxy/                # sing-box config, process manager
│       │   ├── service/              # VPN service, notifications, boot
│       │   └── ui/                   # Compose UI screens + theme
│       └── res/
│           └── raw/
│               ├── sing_box_arm64_v8a     # sing-box binary (ARM64)
│               └── sing_box_armeabi_v7a   # sing-box binary (ARM32)
```

## Troubleshooting

### "Android SDK Platform 34 not found"
Install platform 34 (see Prerequisites) or update `sdk.dir` in `local.properties`.

### "Plugin was not found" / Maven resolution errors
The Gradle daemon needs network access to `dl.google.com` and `repo.maven.apache.org`. If using a proxy (e.g., HaioBypass desktop), ensure it's running before building. If proxy is not available, clear Gradle caches and try without proxy:
```bash
rm -rf ~/.gradle/caches/modules-2 ~/.gradle/caches/transforms-3
./gradlew assembleDebug --no-daemon
```

### "'-' is not a valid file-based resource name"
Raw resource filenames must use underscores, not hyphens. The `res/raw/` binaries are already named correctly (`sing_box_arm64_v8a`, `sing_box_armeabi_v7a`).

### "resource style/Theme.MaterialComponents not found"
Ensure `com.google.android.material:material` dependency is in `app/build.gradle.kts`. It's already included in the current build config.

### Build is slow
First build downloads all dependencies (~200MB). Subsequent builds use Gradle's cache and are much faster (typically 30-40 seconds for incremental builds).
