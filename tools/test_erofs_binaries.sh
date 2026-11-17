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
for target in mkfs.erofs extract.erofs; do
    git -C "$SRC_DIR" show "HEAD:app/src/main/jniLibs/arm64-v8a/lib$target.so" > "$test_dir/old-$target"
    chmod 755 "$test_dir/old-$target"
done
for compression in lz4hc,9 none; do
    args=()
    if [ "$compression" != none ]; then
        args=(-z "$compression")
    fi
    for maker in old new; do
        image_dir="$test_dir/$compression-$maker"
        mkdir -p "$image_dir"
        image="$image_dir/system.img"
        mkfs="$test_dir/old-mkfs.erofs"
        if [ "$maker" = new ]; then mkfs="$bin/libmkfs.erofs.so"; fi
        qemu-aarch64 "$mkfs" "${args[@]}" -C 4096 "$image" "$test_dir/input"
        for extractor in old new; do
            extract="$test_dir/old-extract.erofs"
            if [ "$extractor" = new ]; then extract="$bin/libextract.erofs.so"; fi
            output="$image_dir/$extractor"
            mkdir -p "$output"
            qemu-aarch64 "$extract" -i "$image" -x -o "$output" -f
            extracted="$output/system"
            cmp "$test_dir/input/nested/file.txt" "$extracted/nested/file.txt"
            cmp "$test_dir/input/compressible.bin" "$extracted/compressible.bin"
            test -f "$extracted/empty" && test ! -s "$extracted/empty"
            test "$(readlink "$extracted/link")" = 'nested/file.txt'
        done
    done
done
