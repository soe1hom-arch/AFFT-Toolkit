#!/bin/bash
set -euo pipefail

: "${1:?Pass the patched debugfs executable path}"
DEBUGFS_BIN="$1"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TEST_DIR"' EXIT

SOURCE_DIR="$TEST_DIR/source"
OUTPUT_DIR="$TEST_DIR/output"
IMAGE="$TEST_DIR/filesystem.img"
FS_CONFIG="$TEST_DIR/fs_config.txt"
mkdir -p "$SOURCE_DIR/sub" "$OUTPUT_DIR"
printf 'file contents\n' > "$SOURCE_DIR/file"
chmod 0640 "$SOURCE_DIR/file"
printf 'nested contents\n' > "$SOURCE_DIR/sub/item"
ln -s file "$SOURCE_DIR/link"
truncate -s 32M "$IMAGE"
mke2fs -q -t ext4 -F -d "$SOURCE_DIR" "$IMAGE"

AFFT_FS_CONFIG_FILE="$FS_CONFIG" \
    "$DEBUGFS_BIN" -R "rdump / $OUTPUT_DIR" "$IMAGE" >"$TEST_DIR/stdout" 2>"$TEST_DIR/stderr"
test "$(cat "$OUTPUT_DIR/file")" = "file contents"
test -L "$OUTPUT_DIR/link"
test "$(readlink "$OUTPUT_DIR/link")" = "file"
grep -Fx "/ $(stat -c %u "$SOURCE_DIR") $(stat -c %g "$SOURCE_DIR") 0755" "$FS_CONFIG"
grep -Fx "file $(stat -c %u "$SOURCE_DIR/file") $(stat -c %g "$SOURCE_DIR/file") 0640" "$FS_CONFIG"
grep -Fx "sub $(stat -c %u "$SOURCE_DIR/sub") $(stat -c %g "$SOURCE_DIR/sub") 0755" "$FS_CONFIG"
if grep -Eq '(^|/)(\.|\.\.) ' "$FS_CONFIG"; then
    echo "fs_config contains a dot entry" >&2
    exit 1
fi

SPACE_SOURCE="$TEST_DIR/space source"
SPACE_OUTPUT="$TEST_DIR/space_output"
SPACE_IMAGE="$TEST_DIR/space.img"
mkdir -p "$SPACE_SOURCE/has space" "$SPACE_OUTPUT"
truncate -s 32M "$SPACE_IMAGE"
mke2fs -q -t ext4 -F -d "$SPACE_SOURCE" "$SPACE_IMAGE"
AFFT_FS_CONFIG_FILE="$TEST_DIR/space_fs_config.txt" \
    "$DEBUGFS_BIN" -R "rdump / $SPACE_OUTPUT" "$SPACE_IMAGE" >"$TEST_DIR/space_stdout" 2>"$TEST_DIR/space_stderr"
grep -F "cannot encode whitespace in fs_config path has space" "$TEST_DIR/space_stderr"

echo "Patched debugfs fs_config extraction test passed."
