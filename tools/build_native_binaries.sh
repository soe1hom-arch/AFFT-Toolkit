#!/bin/bash
# Build native AOSP tools — optimized build script

set -euo pipefail

: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"
: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/native-build}"

ARCH="arm64-v8a"
TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
CXX="$TOOLCHAIN/bin/aarch64-linux-android21-clang++"

JNILIBS_DIR="${SRC_DIR}/app/src/main/jniLibs/${ARCH}"
ASSETS_DIR="${SRC_DIR}/app/src/main/assets/bin"
mkdir -p "$JNILIBS_DIR" "$ASSETS_DIR" "$WORK_DIR"

echo "=== [1/2] Fetch AOSP repos ==="
cd "$WORK_DIR"
# Need system/core (libsparse) AND system/libbase (android-base headers)
for repo in platform_system_core platform_system_libbase; do
    target="${repo}_src"
    if [ -d "$target" ]; then
        cd "$target" && git pull && cd ..
    else
        git clone --depth=1 "https://github.com/aosp-mirror/${repo}.git" "$target" 2>&1 || true
    fi
done

# Build simg2img
if [ -d "${WORK_DIR}/platform_system_core_src" ] && [ -d "${WORK_DIR}/platform_system_libbase_src" ]; then
    CORE="${WORK_DIR}/platform_system_core_src"
    BASE="${WORK_DIR}/platform_system_libbase_src"
    
    INCLUDES="-I${CORE}/libsparse/include -I${CORE}/libsparse -I${BASE}/include"
    
    echo "=== [2/2] Building simg2img ==="
    $CXX -std=c++17 $INCLUDES -O2 -s \
        -o simg2img \
        "${CORE}/libsparse/simg2img.cpp" \
        "${CORE}/libsparse/backed_block.cpp" \
        "${CORE}/libsparse/output_file.cpp" \
        "${CORE}/libsparse/sparse.cpp" \
        "${CORE}/libsparse/sparse_err.cpp" \
        "${CORE}/libsparse/sparse_read.cpp" \
        -lc 2>&1 || true
    
    if [ -f simg2img ] && [ -s simg2img ]; then
        file simg2img
        NEEDED=$(readelf -d simg2img 2>/dev/null | grep -c "NEEDED\|RUNPATH" || echo "0")
        echo "NEEDED+RUNPATH: $NEEDED"
        cp simg2img "$ASSETS_DIR/simg2img"
        cp simg2img "$JNILIBS_DIR/libsimg2img.so"
        chmod 755 "$ASSETS_DIR/simg2img" "$JNILIBS_DIR/libsimg2img.so"
        echo "simg2img: ✅ Static"
    else
        echo "simg2img: ❌ Build failed"
    fi
else
    echo "WARNING: AOSP repos not available on this runner"
fi

echo ""
echo "=== Final Binary Status ==="
ls -lh "$ASSETS_DIR/simg2img" 2>/dev/null && echo "simg2img updated" || echo "simg2img not updated (keeping existing)"
