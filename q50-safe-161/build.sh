#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
BT="${ANDROID_BUILD_TOOLS:-$SDK/build-tools/34.0.0}"
ANDJAR="${ANDROID_JAR:-$SDK/platforms/android-34/android.jar}"
AAPT="$BT/aapt"; ZIPALIGN="$BT/zipalign"; APKSIGNER="$BT/apksigner"; D8="$BT/d8"
BASE="${Q50_BASE:-../q50-base}"
EPK="$BASE/tools/epk_tool.py"
CERT="$BASE/keys/obu_cert.pem"
for f in "$AAPT" "$ZIPALIGN" "$APKSIGNER" "$D8" "$ANDJAR" "$EPK" "$CERT"; do [ -e "$f" ] || { echo "missing: $f" >&2; exit 2; }; done

OUT="$PWD/out"; rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex"
KEYSTORE="$PWD/q50-safe-release.ks"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias q50safe -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname 'CN=Q50 OS Safe,OU=Owner Build' >/dev/null 2>&1
fi

javac --release 8 -g -d "$OUT/classes" -classpath "$ANDJAR" src/com/q50/factory/safe/MainActivity.java
"$D8" --min-api 10 --lib "$ANDJAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
"$AAPT" package -f -M AndroidManifest.xml -I "$ANDJAR" -F "$OUT/app.unsigned.apk"
( cd "$OUT/dex" && "$AAPT" add "$OUT/app.unsigned.apk" classes.dex >/dev/null )
"$ZIPALIGN" -f -p 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --min-sdk-version 10 --v1-signing-enabled true --v2-signing-enabled false --v3-signing-enabled false --out "$OUT/Q50-OS-SAFE-1.6.1.apk" "$OUT/app.aligned.apk"
"$APKSIGNER" verify --verbose --min-sdk-version 10 "$OUT/Q50-OS-SAFE-1.6.1.apk" | tee "$OUT/APK-VERIFY.txt"
"$AAPT" dump badging "$OUT/Q50-OS-SAFE-1.6.1.apk" | grep -E 'package:|sdkVersion|targetSdkVersion|launchable-activity' | tee "$OUT/APK-BADGING.txt"
python "$EPK" --cert "$CERT" "$OUT/Q50-OS-SAFE-1.6.1.apk" "$OUT/Q50-OS-SAFE-1.6.1.epk"
python - "$OUT/Q50-OS-SAFE-1.6.1.epk" <<'PY' | tee "$OUT/EPK-VERIFY.txt"
import sys,struct,os
p=sys.argv[1]; b=open(p,'rb').read(); assert b[:4]==b'.epk'
ver,typ,blocks,keysize=struct.unpack('>HHHH',b[4:12])
assert ver==2 and typ==2 and blocks==1 and keysize>0
print('OK',os.path.basename(p),'version=',ver,'type=',typ,'blocks=',blocks,'keysize=',keysize,'bytes=',len(b))
PY
cp "$KEYSTORE" "$OUT/Q50-OS-SAFE-1.6.1-release-keystore.ks"
cat > "$OUT/INSTALL-FIRST.txt" <<'TXT'
Q50 OS SAFE 1.6.1

This build exists to replace the crashing 1.6.0 WebView host with a conservative native API-10 Activity.
It is one APK in one EPK block and intentionally targets the LOWER display, matching the public working V37 App Garage package shape.

1. In App Garage, uninstall Q50 OS Upper from 1.6.0 if it is still installed.
2. Copy only Q50-OS-SAFE-1.6.1.epk to a FAT32 USB root.
3. MENU -> App Garage -> App Garage Settings -> Install Apps via USB.
4. Install Q50 OS Safe 1.6.1.
5. Launch it while parked. It should remain open instead of blinking/crashing.
6. Leave the USB inserted and tap WRITE FIRMWARE PROBE.
7. If Q50FactoryProbe.txt appears on the USB, copy that file back to the computer and provide it for the upper-display integration pass.

This build performs no CAN writes and does not flash firmware.
TXT
sha256sum "$OUT/Q50-OS-SAFE-1.6.1.apk" "$OUT/Q50-OS-SAFE-1.6.1.epk" "$OUT/Q50-OS-SAFE-1.6.1-release-keystore.ks" > "$OUT/SHA256SUMS.txt"
ls -lh "$OUT"
