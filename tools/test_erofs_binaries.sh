#!/bin/bash
set -euo pipefail

: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/afft-erofs-test}"

mkdir -p "$WORK_DIR"
test_dir="$(mktemp -d "$WORK_DIR/roundtrip.XXXXXX")"
mkdir -p "$test_dir/input/nested"
printf 'EROFS round-trip test\n' > "$test_dir/input/nested/file.txt"
head -c 131072 /dev/zero > "$test_dir/input/compressible.bin"
touch "$test_dir/input/empty"
ln -s nested/file.txt "$test_dir/input/link"

bin="$SRC_DIR/app/src/main/jniLibs/arm64-v8a"
for compression in lz4hc,9 none; do
    image="$test_dir/$compression.img"
    output="$test_dir/$compression"
    mkdir -p "$output"
    qemu-aarch64 "$bin/libmkfs.erofs.so" -z "$compression" -C 4096 "$image" "$test_dir/input"
    qemu-aarch64 "$bin/libextract.erofs.so" -i "$image" -x -o "$output" -f
    cmp "$test_dir/input/nested/file.txt" "$output/nested/file.txt"
    cmp "$test_dir/input/compressible.bin" "$output/compressible.bin"
    test -f "$output/empty" && test ! -s "$output/empty"
    test "$(readlink "$output/link")" = 'nested/file.txt'
done
