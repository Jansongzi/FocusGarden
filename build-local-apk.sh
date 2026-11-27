#!/bin/sh
set -eu

cd "$(dirname "$0")"
project="$(pwd)"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
tools="$sdk/build-tools/35.0.0"
android="$sdk/platforms/android-35/android.jar"
key="${FOCUS_GARDEN_KEYSTORE:-$HOME/.local/share/focus-garden-dev/focus-garden-test.jks}"
out="$project/app/build/outputs/apk/local/focus-garden.apk"
version_code="$(sed -n 's/^versionCode=//p' version.properties)"
version_name="$(sed -n 's/^versionName=//p' version.properties)"
if [ ! -x "$tools/aapt2" ] || [ ! -f "$android" ]; then
    printf '%s\n' "需要 Android SDK Platform 35 和 Build Tools 35.0.0，请设置 ANDROID_SDK_ROOT。" >&2
    exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

work="$(mktemp -d "${TMPDIR:-/tmp}/focus-garden.XXXXXXXX")"
trap 'rm -r "$work"' EXIT HUP INT TERM
mkdir -p "$work/gen" "$work/classes" "$work/dex" "$(dirname "$out")" "$(dirname "$key")"

cache="${XDG_CACHE_HOME:-$HOME/.cache}/focus-garden"
zxing="$cache/zxing-core-3.5.4.jar"
zxing_sha="71de5d89341b5fcf5dd89da7f44e84d825d0e084cdf3ec77c9abe26b0f0ceb13"
verify_zxing() {
    [ "$(shasum -a 256 "$1" | cut -d ' ' -f 1)" = "$zxing_sha" ]
}
if [ ! -f "$zxing" ] || ! verify_zxing "$zxing"; then
    curl -fsSL --retry 2 'https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.4/core-3.5.4.jar' \
        -o "$work/zxing.jar"
    if ! verify_zxing "$work/zxing.jar"; then
        printf '%s\n' "ZXing 校验失败。" >&2
        exit 1
    fi
    mkdir -p "$cache"
    mv -f "$work/zxing.jar" "$zxing"
fi

sed 's/<manifest /<manifest package="com.focusgarden.app" /' \
    app/src/main/AndroidManifest.xml > "$work/AndroidManifest.xml"
"$tools/aapt2" compile --dir app/src/main/res -o "$work/resources.zip"
"$tools/aapt2" link --manifest "$work/AndroidManifest.xml" -I "$android" \
    --java "$work/gen" --min-sdk-version 27 --target-sdk-version 35 \
    --version-code "$version_code" --version-name "$version_name" -o "$work/unsigned.apk" "$work/resources.zip"
find app/src/main/java "$work/gen" -name '*.java' -print0 |
    xargs -0 javac --release 17 -cp "$android:$zxing" -d "$work/classes"
jar cf "$work/classes.jar" -C "$work/classes" .
"$tools/d8" --lib "$android" --min-api 27 --output "$work/dex" "$work/classes.jar" "$zxing"
(cd "$work/dex" && zip -q -0 -j "$work/unsigned.apk" classes*.dex)
"$tools/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"

# 本地测试签名，私钥不随项目或 APK 分发。
if [ ! -f "$key" ]; then
    keytool -genkeypair -keystore "$key" -storepass android -alias testkey \
        -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=FocusGarden Local Test'
fi
"$tools/apksigner" sign --ks "$key" --ks-key-alias testkey \
    --ks-pass pass:android --key-pass pass:android --out "$work/signed.apk" "$work/aligned.apk"
"$tools/apksigner" verify "$work/signed.apk"
cp "$work/signed.apk" "$out"
printf '%s\n' "$out"
