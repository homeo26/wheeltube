#!/usr/bin/env bash
# Build & sign WheelTube APK with the Android SDK command-line tools only
# (no Gradle, no Android Studio). Output: build/wheeltube.apk
#
# Override defaults with env vars:
#   SDK=/path/to/android/sdk  BT_VER=36.0.0  PLATFORM=android-36  ./build.sh

set -euo pipefail
cd "$(dirname "$0")"

SDK="${SDK:-$HOME/Library/Android/sdk}"
BT_VER="${BT_VER:-36.0.0}"
PLATFORM="${PLATFORM:-android-36}"

BT="$SDK/build-tools/$BT_VER"
AJ="$SDK/platforms/$PLATFORM/android.jar"

[ -d "$BT" ] || { echo "Build-tools not found at $BT — set BT_VER or SDK"; exit 1; }
[ -f "$AJ" ] || { echo "android.jar not found at $AJ — set PLATFORM or SDK"; exit 1; }

echo "Using SDK=$SDK"
echo "       build-tools=$BT_VER"
echo "       platform=$PLATFORM"
echo

mkdir -p build/{gen,classes,dex}

echo "[1/6] aapt2 compile resources"
"$BT/aapt2" compile --dir res -o build/res-compiled.zip

echo "[2/6] aapt2 link → unsigned APK + R.java"
"$BT/aapt2" link \
  -I "$AJ" \
  --manifest AndroidManifest.xml \
  --java build/gen \
  --target-sdk-version 28 --min-sdk-version 28 \
  -o build/unsigned-noclasses.apk \
  build/res-compiled.zip

echo "[3/6] javac"
javac -source 1.8 -target 1.8 -Xlint:-options -bootclasspath "$AJ" \
  -d build/classes \
  build/gen/com/homeo/wheeltube/R.java \
  src/com/homeo/wheeltube/MainActivity.java \
  src/com/homeo/wheeltube/WheelMediaBrowserService.java

echo "[4/6] d8 → classes.dex"
"$BT/d8" --min-api 28 --output build/dex $(find build/classes -name "*.class")

echo "[5/6] package APK"
cp build/unsigned-noclasses.apk build/unsigned.apk
( cd build/dex && zip -q ../unsigned.apk classes.dex )

if [ ! -f build/debug.keystore ]; then
  echo "  [keystore] generating one-time debug keystore"
  keytool -genkeypair -keystore build/debug.keystore \
    -storepass android -keypass android \
    -alias k -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=wheeltube,O=local,C=US" 2>/dev/null
fi

echo "[6/6] zipalign + apksigner"
"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign \
  --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias k --min-sdk-version 28 \
  --out build/wheeltube.apk build/aligned.apk

echo
echo "Built: $(pwd)/build/wheeltube.apk ($(wc -c < build/wheeltube.apk | tr -d ' ') bytes)"
echo
echo "Install on a phone with AA:"
echo "  adb install -r build/wheeltube.apk"
