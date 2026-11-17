#!/bin/bash
set -euo pipefail

: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"
: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/afft-erofs-build}"

EROFS_COMMIT="4f5d6efc225cf90064a8196b1412692fb89e1e69"
mkdir -p "$WORK_DIR"
EROFS_DIR="$(mktemp -d "$WORK_DIR/erofs-source.XXXXXX")"
git -C "$EROFS_DIR" init
git -C "$EROFS_DIR" remote add origin https://github.com/sekaiacg/erofs-tools.git
git -C "$EROFS_DIR" fetch --depth=1 origin "$EROFS_COMMIT"
git -C "$EROFS_DIR" checkout --detach FETCH_HEAD
test "$(git -C "$EROFS_DIR" rev-parse HEAD)" = "$EROFS_COMMIT"
git -C "$EROFS_DIR" submodule update --init --recursive --depth=1
if git -C "$EROFS_DIR" submodule status --recursive | grep -qE '^[-+U]'; then
    echo "ERROR: EROFS submodule checkout does not match pinned revisions." >&2
    exit 1
fi
git -C "$EROFS_DIR" apply "$SRC_DIR/tools/erofs-no-fuse.patch"

cmake -S "$EROFS_DIR/build/cmake" -B "$EROFS_DIR/out" \
    -DCMAKE_BUILD_TYPE=Release \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_STL=none \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
    -DANDROID_USE_LEGACY_TOOLCHAIN_FILE=OFF \
    -DENABLE_FULL_LTO=ON \
    -DMAX_BLOCK_SIZE=4096
cmake --build "$EROFS_DIR/out" --parallel "$(nproc)" --target mkfs.erofs extract.erofs

for target in mkfs.erofs extract.erofs; do
    link_file="$EROFS_DIR/out/erofs-tools/CMakeFiles/$target.dir/link.txt"
    if grep -Fq 'fuse_static' "$link_file"; then
        echo "ERROR: $target links libfuse unexpectedly." >&2
        exit 1
    fi
    binary="$EROFS_DIR/out/erofs-tools/$target"
    readelf -h "$binary" | grep -q 'AArch64'
    if readelf -d "$binary" | grep -q '(NEEDED)'; then
        echo "ERROR: $target has unexpected shared-library dependencies." >&2
        exit 1
    fi
    install -m 755 "$binary" "$SRC_DIR/app/src/main/jniLibs/arm64-v8a/lib$target.so"
done
