#!/bin/bash
# Build script: cross-compile payload-dumper-go with CGO + liblzma + libzstd for Android ARM64

set -euo pipefail

: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"
: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/payload-dumper-build}"

ARCH="arm64-v8a"
TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
AR="$TOOLCHAIN/bin/llvm-ar"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
PAYLOAD_REPO="https://github.com/ssut/payload-dumper-go.git"

JNILIBS_DIR="${SRC_DIR}/app/src/main/jniLibs/${ARCH}"
ASSETS_DIR="${SRC_DIR}/app/src/main/assets/bin"
mkdir -p "$JNILIBS_DIR" "$ASSETS_DIR" "$WORK_DIR"

export GOMODCACHE="${WORK_DIR}/go-mod-cache"
mkdir -p "$GOMODCACHE"

echo "=== [1/6] Building native libraries ==="
cd "$WORK_DIR"

# --- liblzma.a ---
LZMA_VER="5.4.6"
if [ ! -f "$WORK_DIR/xz-install/lib/liblzma.a" ]; then
    echo "--- Building liblzma.a (xz $LZMA_VER) ---"
    [ -f "xz-${LZMA_VER}.tar.xz" ] || curl -sLo "xz-${LZMA_VER}.tar.xz" \
        "https://github.com/tukaani-project/xz/releases/download/v${LZMA_VER}/xz-${LZMA_VER}.tar.xz"
    rm -rf "xz-${LZMA_VER}"
    tar -xf "xz-${LZMA_VER}.tar.xz"
    cd "xz-${LZMA_VER}"
    ./configure --host=aarch64-linux-android \
        --prefix="$WORK_DIR/xz-install" CC="$CC" AR="$AR" \
        --enable-static --disable-shared \
        --disable-xz --disable-xzdec --disable-lzmadec \
        --disable-lzmainfo --disable-lzma-links \
        --disable-scripts --disable-doc --with-pic
    make -j$(nproc)
    make install
    echo "=== liblzma.a built ==="
else
    echo "=== liblzma.a cached ==="
fi

# --- libzstd.a ---
if [ ! -f "$WORK_DIR/zstd-install/lib/libzstd.a" ]; then
    echo "--- Building libzstd.a ---"
    rm -rf zstd
    git clone --depth=1 --branch v1.5.5 https://github.com/facebook/zstd.git
    cd zstd
    make clean 2>/dev/null || true
    CC="$CC" AR="$AR" RANLIB="$RANLIB" make -C lib libzstd.a -j$(nproc)
    mkdir -p "$WORK_DIR/zstd-install/lib"
    cp lib/libzstd.a "$WORK_DIR/zstd-install/lib/"
    echo "=== libzstd.a built ==="
else
    echo "=== libzstd.a cached ==="
fi

echo "=== [2/6] Fetching payload-dumper-go ==="
cd "$WORK_DIR"
if [ -d "payload-dumper-go" ]; then
    cd payload-dumper-go && git pull && cd ..
else
    git clone --depth=1 "$PAYLOAD_REPO"
fi

echo "=== [3/6] Applying CGO patch (liblzma) ==="
cd payload-dumper-go
sed -i '/"github.com\/ulikunitz\/xz"/d' payload.go 2>/dev/null || true
sed -i 's/xz\.NewReader(teeReader)/newXzReader(teeReader)/g' payload.go 2>/dev/null || true
go mod edit -droprequire github.com/ulikunitz/xz 2>/dev/null || true

# Only copy cgo_lzma.go if not already present (idempotent)
if [ ! -f cgo_lzma.go ]; then
    cp "$SRC_DIR/tools/cgo_lzma.go" ./
fi

echo "=== [4/6] go mod tidy ==="
go mod tidy 2>&1

echo "=== [4b/6] Patching gozstd (replace Linux .a with Android .a) ==="
GOZSTD_DIR="$GOMODCACHE/github.com/valyala/gozstd@v1.21.1"
if [ -d "$GOZSTD_DIR" ]; then
    echo "Found gozstd at $GOZSTD_DIR"
    chmod -R u+w "$GOZSTD_DIR" 2>/dev/null || true
    rm -f "$GOZSTD_DIR/libzstd_linux_arm64.a"
    cp "$WORK_DIR/zstd-install/lib/libzstd.a" "$GOZSTD_DIR/libzstd_linux_arm64.a"
    echo "=== gozstd patched ==="
    ls -la "$GOZSTD_DIR/libzstd_"*.a
else
    echo "WARNING: gozstd directory not found at $GOZSTD_DIR"
    echo "Searching in $GOMODCACHE..."
    find "$GOMODCACHE" -name "gozstd*" -type d -maxdepth 4 2>/dev/null || true
fi

echo "=== [5/6] Compiling with CGO ==="
export GO111MODULE=on
export CGO_ENABLED=1
export GOOS=android
export GOARCH=arm64
export CC="$CC"
export CGO_CFLAGS="-I$WORK_DIR/xz-install/include -O2"
export CGO_LDFLAGS="-L$WORK_DIR/xz-install/lib -l:liblzma.a -L$WORK_DIR/zstd-install/lib -l:libzstd.a"

go build -ldflags='-s -w' -o libpayload-dumper-go.so .

echo "=== [6/6] Copying binary ==="
file libpayload-dumper-go.so
cp libpayload-dumper-go.so "$JNILIBS_DIR/libpayload-dumper-go.so"
cp libpayload-dumper-go.so "$ASSETS_DIR/payload-dumper-go"
chmod 755 "$JNILIBS_DIR/libpayload-dumper-go.so" "$ASSETS_DIR/payload-dumper-go"

echo "=== DONE ==="
ls -lh "$JNILIBS_DIR/libpayload-dumper-go.so"
ls -lh "$ASSETS_DIR/payload-dumper-go"
