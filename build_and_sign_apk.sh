#!/bin/bash

# Build + sign the single SpaceKai release APK.
#
# Produces ONE universal, signed, installable APK named:
#   SpaceKai-v<version-name>.apk
# (no per-ABI splits, no debug/unsigned/aligned artifacts in the output)
# plus a SHA256SUMS.txt containing only the final APK.
#
# NOTE: the release workflow builds the FULL variant only, so the public
# release ships exactly one APK. A --foss build appends "-foss" to the name
# so it never clobbers the full one during local builds.

set -e

# Default variables
BUILD_TYPE="release"
BUILD_VARIANT="full"
KEYSTORE_PATH="./simpmusic.jks"
# Read passwords from environment variables or use default (for backward compatibility)
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD}"
KEY_ALIAS="${KEY_ALIAS}"
KEY_PASSWORD="${KEY_PASSWORD}"

# Check if KEY_PASSWORD is set
if [ -z "$KEY_PASSWORD" ]; then
  echo "Error: KEY_PASSWORD environment variable must be set"
  exit 1
fi

if [ -z "$KEYSTORE_PASSWORD" ]; then
  echo "Error: KEYSTORE_PASSWORD environment variable must be set"
  exit 1
fi

if [ -z "$KEY_ALIAS" ]; then
  echo "Error: KEY_ALIAS environment variable must be set"
  exit 1
fi

# Parse command line arguments
print_usage() {
  echo "Usage: $0 [options]"
  echo "Options:"
  echo "  --release          Build in release mode (default)"
  echo "  --debug            Build in debug mode"
  echo "  --full             Build full with Sentry (default)"
  echo "  --foss             Build foss, compatibility with F-Droid, no Sentry"
  echo "  -h, --help         Show this help message"
  echo ""
  echo "Environment variables:"
  echo "  KEYSTORE_PASSWORD  Password for the keystore"
  echo "  KEY_PASSWORD       Password for the key (required, no default)"
  echo "  KEY_ALIAS          Alias for the key (required, no default)"
  exit 0
}

while [[ "$#" -gt 0 ]]; do
  case $1 in
    --full) BUILD_VARIANT="full" ;;
    --foss) BUILD_VARIANT="foss" ;;
    --release) BUILD_TYPE="release" ;;
    --debug) BUILD_TYPE="debug" ;;
    -h|--help) print_usage ;;
    *) echo "Unknown parameter: $1"; print_usage ;;
  esac
  shift
done

# Version from the version catalog — drives the APK filename.
VERSION_NAME=$(grep '^version-name' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)
if [ -z "$VERSION_NAME" ]; then
  echo "Error: could not read version-name from gradle/libs.versions.toml"
  exit 1
fi

# Set derived variables based on selected options
APK_OUTPUT_DIR="./androidApp/build/outputs/apk/$BUILD_TYPE"
SIGNED_APK_OUTPUT_DIR="./androidApp/build/outputs/apk/$BUILD_TYPE"

# Android build-tools path
BUILD_TOOLS_PATH="$ANDROID_HOME/build-tools/$(ls $ANDROID_HOME/build-tools | sort | tail -n 1)"
APKSIGNER="$BUILD_TOOLS_PATH/apksigner"
ZIPALIGN="$BUILD_TOOLS_PATH/zipalign"

# Single final release APK (full = the public release). A foss build gets a
# "-foss" suffix so it never overwrites the full one locally.
if [ "$BUILD_VARIANT" == "foss" ]; then
  FINAL_APK="SpaceKai-v${VERSION_NAME}-foss.apk"
else
  FINAL_APK="SpaceKai-v${VERSION_NAME}.apk"
fi

# Create output directory for signed APKs
mkdir -p "$SIGNED_APK_OUTPUT_DIR"

# Log the start of the process
echo "===================="
echo "Building APK Process"
echo "===================="
echo "Build Type: $BUILD_TYPE"
echo "Variant:    $BUILD_VARIANT"
echo "Version:    $VERSION_NAME"
echo "Final APK:  $FINAL_APK"
echo "===================="

# Step 1: Clean the project
echo "[Step 1] Cleaning the project..."
./gradlew clean --no-configuration-cache
echo "Project cleaned successfully."

# Step 2: Build the APK
echo "[Step 2] Building APK..."
./gradlew androidApp:assemble"$BUILD_TYPE"
echo "APK built successfully."

# Step 3: Locate the built APK (expect exactly one universal APK)
APK_PATHS=$(find "$APK_OUTPUT_DIR" -name "*.apk")
if [ -z "$APK_PATHS" ]; then
  echo "Error: APKs not found in $APK_OUTPUT_DIR"
  exit 1
fi
COUNT=$(echo "$APK_PATHS" | wc -l | tr -d ' ')
if [ "$COUNT" -ne 1 ]; then
  echo "Error: expected exactly 1 universal APK, found $COUNT. Refusing to sign a split release."
  exit 1
fi
APK_PATH="$APK_PATHS"
echo "Built APK: $APK_PATH"

# Step 4: Align
ALIGNED_APK_PATH="$SIGNED_APK_OUTPUT_DIR/aligned-${FINAL_APK}"
echo "[Step 4] Aligning the APK: $APK_PATH..."
if [ ! -f "$ZIPALIGN" ]; then
  echo "Error: zipalign tool not found in Android SDK."
  exit 1
fi
"$ZIPALIGN" -v 4 "$APK_PATH" "$ALIGNED_APK_PATH"
echo "APK aligned and saved to: $ALIGNED_APK_PATH"

# Step 5: Sign
SIGNED_APK_PATH="$SIGNED_APK_OUTPUT_DIR/$FINAL_APK"
echo "[Step 5] Signing the APK: $ALIGNED_APK_PATH..."
"$APKSIGNER" sign \
  --alignment-preserved \
  --ks "$KEYSTORE_PATH" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass pass:"$KEYSTORE_PASSWORD" \
  --key-pass pass:"$KEY_PASSWORD" \
  --out "$SIGNED_APK_PATH" \
  "$ALIGNED_APK_PATH"
echo "APK signed successfully: $SIGNED_APK_PATH"

# Step 6: Verify signature
echo "[Step 6] Verifying the signed APK: $SIGNED_APK_PATH..."
"$APKSIGNER" verify --verbose "$SIGNED_APK_PATH"
echo "Signed APK verified successfully: $SIGNED_APK_PATH"

# Step 7: Clean up temporary files
echo "[Step 7] Cleaning up temporary files..."
cd "$SIGNED_APK_OUTPUT_DIR"
rm -f *.idsig
rm -f *aligned*
rm -f *unsigned*
rm -f SimpMusic-*.apk 2>/dev/null || true

# Step 8: SHA-256 checksum of the final APK only
echo "[Step 8] Generating SHA256SUMS.txt..."
sha256sum "$FINAL_APK" > SHA256SUMS.txt
echo "SHA256SUMS.txt written:"
cat SHA256SUMS.txt

# Completion message
echo "===================="
echo "Process Completed Successfully!"
echo "Signed APK: $SIGNED_APK_PATH"
echo "SHA256:     $SIGNED_APK_OUTPUT_DIR/SHA256SUMS.txt"
echo "===================="
