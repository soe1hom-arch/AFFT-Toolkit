#!/bin/bash
# Build the Apache-2.0 payload-dumper-go source and its native dependencies
# for Android ARM64. This replaces the unpinned legacy recipe.

set -euo pipefail

: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"
: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/payload-dumper-build}"

ARCH="arm64-v8a"
PAYLOAD_COMMIT="a51234eaead276ff3d8b8c4c439c51c7f46a96a8"
XZ_VER="5.4.6"
XZ_SHA256="b92d4e3a438affcf13362a1305cd9d94ed47ddda22e456a42791e630a5644f5c"
ZSTD_COMMIT="63779c798237346c2b245c546c40b72a5a5913fe"
GOZSTD_VER="v1.21.1"
TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
AR="$TOOLCHAIN/bin/llvm-ar"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
JNILIBS_DIR="${SRC_DIR}/app/src/main/jniLibs/${ARCH}"
ASSETS_DIR="${SRC_DIR}/app/src/main/assets/bin"
mkdir -p "$JNILIBS_DIR" "$ASSETS_DIR" "$WORK_DIR"

if [ "$(go version)" != "go version go1.22.12 linux/amd64" ]; then
    echo "ERROR: use Go 1.22.12 linux/amd64 to match the bundled binary toolchain." >&2
    exit 1
fi

export GOMODCACHE="${WORK_DIR}/go-mod-cache"
mkdir -p "$GOMODCACHE"

echo "=== Build liblzma (XZ $XZ_VER) ==="
if [ ! -f "$WORK_DIR/xz-install/lib/liblzma.a" ]; then
    cd "$WORK_DIR"
    curl --fail --location --silent --show-error \
        "https://github.com/tukaani-project/xz/releases/download/v${XZ_VER}/xz-${XZ_VER}.tar.xz" \
        -o "xz-${XZ_VER}.tar.xz"
    echo "$XZ_SHA256  xz-${XZ_VER}.tar.xz" | sha256sum --check
    tar -xf "xz-${XZ_VER}.tar.xz"
    cd "xz-${XZ_VER}"
    ./configure --host=aarch64-linux-android \
        --prefix="$WORK_DIR/xz-install" CC="$CC" AR="$AR" \
        --enable-static --disable-shared \
        --disable-xz --disable-xzdec --disable-lzmadec \
        --disable-lzmainfo --disable-lzma-links \
        --disable-scripts --disable-doc --with-pic
    make -j"$(nproc)"
    make install
fi

echo "=== Build libzstd @ $ZSTD_COMMIT ==="
if [ ! -f "$WORK_DIR/zstd-install/lib/libzstd.a" ]; then
    cd "$WORK_DIR"
    if [ ! -d zstd/.git ]; then
        git clone https://github.com/facebook/zstd.git zstd
    fi
    git -C zstd checkout --detach "$ZSTD_COMMIT"
    if [ "$(git -C zstd rev-parse HEAD)" != "$ZSTD_COMMIT" ]; then
        echo "ERROR: zstd source revision mismatch." >&2
        exit 1
    fi
    CC="$CC" AR="$AR" RANLIB="$RANLIB" \
        make -C zstd/lib -j"$(nproc)" libzstd.a
    mkdir -p "$WORK_DIR/zstd-install/lib"
    cp zstd/lib/libzstd.a "$WORK_DIR/zstd-install/lib/"
fi

echo "=== Fetch payload-dumper-go @ $PAYLOAD_COMMIT ==="
PAYLOAD_DIR="$WORK_DIR/payload-dumper-go-$PAYLOAD_COMMIT"
if [ ! -d "$PAYLOAD_DIR/.git" ]; then
    git clone https://github.com/ssut/payload-dumper-go.git "$PAYLOAD_DIR"
fi
git -C "$PAYLOAD_DIR" fetch --depth=1 origin "$PAYLOAD_COMMIT"
git -C "$PAYLOAD_DIR" checkout --detach "$PAYLOAD_COMMIT"
if [ -n "$(git -C "$PAYLOAD_DIR" status --porcelain)" ]; then
    echo "ERROR: payload-dumper-go source tree is modified." >&2
    exit 1
fi

echo "=== Prepare pinned gozstd native archive ==="
GOZSTD_DIR="$GOMODCACHE/github.com/valyala/gozstd@$GOZSTD_VER"
mkdir -p "$(dirname "$GOZSTD_DIR")"
if [ ! -d "$GOZSTD_DIR" ]; then
    (cd "$PAYLOAD_DIR" && GOMODCACHE="$GOMODCACHE" go mod download "github.com/valyala/gozstd@$GOZSTD_VER")
fi
GOZSTD_DIR="$GOMODCACHE/github.com/valyala/gozstd@$GOZSTD_VER"
cp "$WORK_DIR/zstd-install/lib/libzstd.a" "$GOZSTD_DIR/libzstd_linux_arm64.a"

echo "=== Compile for Android ARM64 ==="
cd "$PAYLOAD_DIR"
export CGO_ENABLED=1
export GOOS=android
export GOARCH=arm64
export CC
export CGO_CFLAGS="-I$WORK_DIR/xz-install/include -O2"
export CGO_LDFLAGS="-L$WORK_DIR/xz-install/lib -l:liblzma.a"
go build -mod=readonly -ldflags='-s -w' -o payload-dumper-go .

file payload-dumper-go
install -m 755 payload-dumper-go "$JNILIBS_DIR/libpayload-dumper-go.so"
install -m 755 payload-dumper-go "$ASSETS_DIR/payload-dumper-go"
echo "=== payload-dumper-go DONE ==="
