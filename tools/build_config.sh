#!/bin/bash
# AFFT-Toolkit native binary build configuration.
# Single source of truth for pinned upstream versions.
# Every helper binary shipped in the APK is compiled from the sources below
# with the Android NDK — no prebuilt binaries are committed to this repo.
# See tools/README.md and F-DROID.md.
#
# Usage: source this file from the other tools/build_*.sh scripts.
# Env: NDK_DIR (required), SRC_DIR (repo root, defaults to parent of tools/),
# WORK_DIR (default /tmp/afft-native-build), ARCH (default arm64-v8a),
# ANDROID_API (default 21).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SRC_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export SRC_DIR
NDK_DIR="${NDK_DIR:-}"
require_ndk() {
    if [ -z "$NDK_DIR" ] || [ ! -d "$NDK_DIR" ]; then
        echo "ERROR: Must set NDK_DIR to Android NDK path." >&2
        return 1
    fi
    if [ ! -x "$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang" ]; then
        echo "ERROR: NDK clang not found at $TOOLCHAIN." >&2
        return 1
    fi
}

ARCH="${ARCH:-arm64-v8a}"
ANDROID_API="${ANDROID_API:-21}"
WORK_DIR="${WORK_DIR:-/tmp/afft-native-build}"

TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
export CC="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang"
export CXX="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang++"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"

JNILIBS_DIR="${SRC_DIR}/app/src/main/jniLibs/${ARCH}"
DEPS_PREFIX="$WORK_DIR/deps"
mkdir -p "$JNILIBS_DIR" "$WORK_DIR" "$DEPS_PREFIX"

# ---- Pinned upstream versions (keep in sync with THIRD_PARTY_NOTICES.md) ----
# payload-dumper-go (GPL-3.0)
PAYLOAD_DUMPER_GO_URL="https://github.com/ssut/payload-dumper-go.git"
PAYLOAD_DUMPER_GO_REF="2.1.0"
# Magisk (GPL-3.0): v25.1 = last pure-C++ magiskboot (ndk-build only, no Rust),
# supports boot/vendor_boot header v4. Newer Magisk needs the Rust toolchain.
MAGISK_URL="https://github.com/topjohnwu/Magisk.git"
MAGISK_REF="v25.1"
# AOSP (Apache-2.0): same snapshot tag for core + extras + libbase.
AOSP_REF="android-14.0.0_r1"
AOSP_CORE_URL="https://android.googlesource.com/platform/system/core"
AOSP_EXTRAS_URL="https://android.googlesource.com/platform/system/extras"
AOSP_BASE_URL="https://android.googlesource.com/platform/system/libbase"
# erofs-utils (GPL-2.0): provides mkfs.erofs + dump.erofs (shipped as extract.erofs)
EROFS_UTILS_URL="https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils.git"
EROFS_UTILS_REF="v1.9.1"
# e2fsprogs (GPL-2.0): provides debugfs + mke2fs (replaces deprecated make_ext4fs,
# which was removed from AOSP before Android 9 and has no maintained source).
E2FSPROGS_URL="https://github.com/tytso/e2fsprogs.git"
E2FSPROGS_REF="v1.47.2"
# Static C deps (reused by payload-dumper-go CGO build and erofs-utils)
XZ_VER="5.4.6"
ZSTD_REF="v1.5.5"
LZ4_REF="v1.9.4"
GOZSTD_VER="v1.21.1"

# Install a built file as lib<name>.so so it lands in nativeLibraryDir and
# stays executable on Android 14+ (see BinaryManager.getNativeLibBinaryPath).
install_jnilib() {
    local src="$1"
    local name="$2"
    cp -f "$src" "$JNILIBS_DIR/lib${name}.so"
    chmod 755 "$JNILIBS_DIR/lib${name}.so"
    echo "installed: $JNILIBS_DIR/lib${name}.so ($(du -h "$JNILIBS_DIR/lib${name}.so" | cut -f1))"
}

# Clone (or refresh) an upstream repo at a pinned ref.
fetch_ref() {
    local url="$1"
    local dir="$2"
    local ref="$3"
    if [ -d "$dir/.git" ]; then
        git -C "$dir" fetch --depth=1 origin "$ref" 2>/dev/null || \
            git -C "$dir" fetch --tags --depth=1 origin "$ref"
    else
        rm -rf "$dir"
        git clone --depth=1 --branch "$ref" "$url" "$dir"
    fi
    git -C "$dir" checkout -q "$ref"
}

# Assert that expected source files exist; fail loudly otherwise.
require_files() {
    local missing=0
    for f in "$@"; do
        if [ ! -f "$f" ]; then
            echo "ERROR: expected source file not found: $f" >&2
            missing=1
        fi
    done
    if [ "$missing" -ne 0 ]; then
        echo "ERROR: upstream layout changed — update the pinned ref or the file list." >&2
        return 1
    fi
}
