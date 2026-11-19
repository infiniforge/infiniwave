#!/usr/bin/env sh
set -eu
cd -- "$(dirname -- "$0")"

git submodule update --init --recursive
python3 scripts/check-dependency-pins.py

INFINIWAVE_SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$INFINIWAVE_SDK" ] && [ -f local.properties ]; then
    INFINIWAVE_SDK=$(sed -n 's/^[[:space:]]*sdk.dir[[:space:]]*[:=][[:space:]]*//p' local.properties | head -n 1)
fi
if [ -z "$INFINIWAVE_SDK" ]; then
    for INFINIWAVE_SDK_CANDIDATE in "$HOME/android-sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
        if [ -d "$INFINIWAVE_SDK_CANDIDATE/platforms" ]; then
            INFINIWAVE_SDK=$INFINIWAVE_SDK_CANDIDATE
            break
        fi
    done
fi
if [ -n "$INFINIWAVE_SDK" ]; then
    set -- "-Dandroid.home=$INFINIWAVE_SDK" "$@"
fi
if [ -n "${TERMUX_VERSION:-}" ]; then
    INFINIWAVE_AAPT2=$(command -v aapt2 || true)
    if [ -z "$INFINIWAVE_AAPT2" ]; then
        echo "Termux requires aapt2. Install it with: pkg install aapt2" >&2
        exit 1
    fi
    set -- "-Pandroid.aapt2FromMavenOverride=$INFINIWAVE_AAPT2" "$@"
fi
./gradlew :editor-core:test :libinfinisynth:synth-core:test assembleDebug testDebugUnitTest lintDebug -PportableOnly=true "$@"

set -- build/outputs/apk/debug/*.apk
if [ ! -f "$1" ]; then
    echo "No debug APK was produced." >&2
    exit 1
fi
for INFINIWAVE_APK do
    cp -f "$INFINIWAVE_APK" .
done
