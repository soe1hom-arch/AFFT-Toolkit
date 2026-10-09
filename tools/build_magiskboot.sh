#!/bin/bash
# Build magiskboot from Magisk for Android ARM64 using ndk-build.
# Source: https://github.com/topjohnwu/Magisk (GPL-3.0)
# Pinned ref: see tools/build_config.sh (MAGISK_REF).
# v25.1 is the last upstream with a pure-C++ magiskboot (no Rust toolchain
# needed) and it already supports boot/vendor_boot header v4.
# Output: app/src/main/jniLibs/<arch>/libmagiskboot.so
#
# Env: NDK_DIR (required), SRC_DIR (repo root). See tools/build_config.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export SRC_DIR
# shellcheck source=build_config.sh
source "$SCRIPT_DIR/build_config.sh"
require_ndk

command -v "$NDK_DIR/ndk-build" >/dev/null 2>&1 || {
    echo "ERROR: ndk-build not found in NDK_DIR ($NDK_DIR)." >&2
    exit 1
}

MAGISK_WORK="$WORK_DIR/magisk"
mkdir -p "$MAGISK_WORK"

echo "=== [1/3] Fetch Magisk @ $MAGISK_REF ==="
fetch_ref "$MAGISK_URL" "$MAGISK_WORK/magisk" "$MAGISK_REF"
cd "$MAGISK_WORK/magisk"

echo "=== [2/3] Assert magiskboot sources (pure C++, no Rust) ==="
require_files \
    native/jni/boot/main.cpp \
    native/jni/boot/bootimg.cpp \
    native/jni/boot/cpio.cpp \
    native/jni/boot/dtb.cpp \
    native/jni/Android.mk \
    native/jni/Application.mk
if git ls-tree -r --name-only HEAD -- native | grep -qE '\.rs$'; then
    echo "ERROR: pinned Magisk ref contains Rust sources (needs cargo toolchain)." >&2
    echo "Pin an older MAGISK_REF with pure-C++ magiskboot (e.g. v25.1)." >&2
    exit 1
fi

echo "=== [3/3] ndk-build magiskboot (arm64-v8a) ==="
cd native
"$NDK_DIR/ndk-build" -j"$(nproc)" B_BOOT=1 APP_ABI=arm64-v8a magiskboot
MAGISKBOOT_BIN="libs/arm64-v8a/magiskboot"
require_files "$MAGISKBOOT_BIN"
file "$MAGISKBOOT_BIN"
install_jnilib "$MAGISKBOOT_BIN" magiskboot

echo "=== magiskboot DONE ==="
