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
    "$DEBUGFS_BIN" -R "rdump / $OUTPUT_DIR" "$IMAGE" >"$TEST_DIR/stdout" 2>"$TEST_DIR/stderr" || {
        cat "$TEST_DIR/stdout" "$TEST_DIR/stderr" >&2
        echo "debugfs failed extracting the basic test image" >&2
        exit 1
    }
if [ "$(cat "$OUTPUT_DIR/file" 2>/dev/null || true)" != "file contents" ]; then
    echo "extracted file contents are missing or incorrect" >&2
    cat "$TEST_DIR/stdout" "$TEST_DIR/stderr" >&2
    exit 1
fi
if [ ! -L "$OUTPUT_DIR/link" ] || [ "$(readlink "$OUTPUT_DIR/link")" != "file" ]; then
    echo "extracted symlink is missing or incorrect" >&2
    exit 1
fi
grep -Fx "/ $(stat -c %u "$SOURCE_DIR") $(stat -c %g "$SOURCE_DIR") 0755" "$FS_CONFIG" ||
    { echo "fs_config is missing the root entry" >&2; exit 1; }
grep -Fx "file $(stat -c %u "$SOURCE_DIR/file") $(stat -c %g "$SOURCE_DIR/file") 0640" "$FS_CONFIG" ||
    { echo "fs_config did not preserve file UID/GID/mode" >&2; exit 1; }
grep -Fx "sub $(stat -c %u "$SOURCE_DIR/sub") $(stat -c %g "$SOURCE_DIR/sub") 0755" "$FS_CONFIG" ||
    { echo "fs_config did not preserve directory UID/GID/mode" >&2; exit 1; }
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
    "$DEBUGFS_BIN" -R "rdump / $SPACE_OUTPUT" "$SPACE_IMAGE" >"$TEST_DIR/space_stdout" 2>"$TEST_DIR/space_stderr" || {
        cat "$TEST_DIR/space_stdout" "$TEST_DIR/space_stderr" >&2
        echo "debugfs failed extracting the whitespace-path image" >&2
        exit 1
    }
grep -F "cannot encode whitespace in fs_config path has space" "$TEST_DIR/space_stderr" ||
    { cat "$TEST_DIR/space_stdout" "$TEST_DIR/space_stderr" >&2; echo "whitespace path was not rejected" >&2; exit 1; }

echo "Patched debugfs fs_config extraction test passed."
