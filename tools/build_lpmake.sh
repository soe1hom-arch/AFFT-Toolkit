#!/bin/bash
# Build script: cross-compile AOSP lpmake for Android ARM64 with NDK
# Source: https://android.googlesource.com/platform/system/core/+/refs/heads/main/fs_mgr/liblp/

set -euo pipefail

: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"
: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/lpmake-build}"

ARCH="arm64-v8a"
TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
CXX="$TOOLCHAIN/bin/aarch64-linux-android21-clang++"
AR="$TOOLCHAIN/bin/llvm-ar"

JNILIBS_DIR="${SRC_DIR}/app/src/main/jniLibs/${ARCH}"
ASSETS_DIR="${SRC_DIR}/app/src/main/assets/bin"
mkdir -p "$JNILIBS_DIR" "$ASSETS_DIR" "$WORK_DIR"

echo "=== [1/3] Fetching AOSP system/core source ==="
cd "$WORK_DIR"
if [ -d "system-core" ]; then
    cd system-core && git pull && cd ..
else
    git clone --depth=1 https://android.googlesource.com/platform/system/core.git
fi

echo "=== [2/3] Compiling lpmake statically ==="
cd system-core/fs_mgr

# lpmake needs liblp, libbase, libsparse, libutils etc.
# Minimal build: compile the needed files together
# liblp source: liblp/
# main tool: liblp/tool.cpp

# First build liblp static library
cd liblp
$CC -c -I../include -I./include src/*.c -O2 2>/dev/null || true
# Build the tool statically
$CXX -static \
    -I./include \
    -I../include \
    -o lpmake \
    tool.cpp \
    -llog -ldl \
    -O2 -s

echo "=== Build result ==="
file lpmake 2>/dev/null || echo "lpmake binary not found"

