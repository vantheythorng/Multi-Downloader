#!/usr/bin/env bash
# Rebuilds the WebP libraries bundled in youtubedl-android's FFmpeg package with 16 KB ELF alignment.
#
# Why: youtubedl-android 0.18.1 ships libwebp 1.6.0 inside libffmpeg.zip.so aligned to 4 KB pages, so on
# 16 KB-page devices FFmpeg can't even start ("program alignment (4096) cannot be smaller than system page
# size (16384)"). Upstream: https://github.com/yausername/youtubedl-android/issues/365
# The app copies these over the bundled ones at runtime, only on 16 KB devices (see engine/Ffmpeg16kFix.kt).
#
# Usage: tools/webp16k/build.sh <libwebp v1.6.0 source dir> [jniLibs dir]
#   git clone --depth 1 --branch v1.6.0 https://github.com/webmproject/libwebp.git /tmp/libwebp
#   tools/webp16k/build.sh /tmp/libwebp
# Only 64-bit ABIs: 32-bit Android always uses 4 KB pages.
set -euo pipefail

SRC=${1:?path to libwebp v1.6.0 source}
OUT=${2:-app/src/main/jniLibs}
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
NDK=${NDK:-$SDK/ndk/27.1.12297006}
CMAKE_DIR=${CMAKE_DIR:-$SDK/cmake/3.22.1/bin}
STRIP=$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/llvm-strip | head -1)
LIBS="sharpyuv webp webpdecoder webpdemux webpmux"

for ABI in arm64-v8a x86_64; do
  BUILD=$(mktemp -d)
  "$CMAKE_DIR/cmake" -S "$SRC" -B "$BUILD" -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$CMAKE_DIR/ninja" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-29 \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
    -DWEBP_BUILD_ANIM_UTILS=OFF -DWEBP_BUILD_CWEBP=OFF -DWEBP_BUILD_DWEBP=OFF \
    -DWEBP_BUILD_GIF2WEBP=OFF -DWEBP_BUILD_IMG2WEBP=OFF -DWEBP_BUILD_VWEBP=OFF \
    -DWEBP_BUILD_WEBPINFO=OFF -DWEBP_BUILD_WEBPMUX=OFF -DWEBP_BUILD_EXTRAS=OFF \
    -DWEBP_BUILD_LIBWEBPMUX=ON > "$BUILD/configure.log"
  "$CMAKE_DIR/cmake" --build "$BUILD" > "$BUILD/build.log"
  mkdir -p "$OUT/$ABI"
  for LIB in $LIBS; do
    # The "md16k_" prefix keeps them apart from any real libwebp; the app copies them under their real names.
    "$STRIP" --strip-unneeded -o "$OUT/$ABI/libmd16k_$LIB.so" "$BUILD/lib$LIB.so"
  done
  echo "$ABI: built $(echo $LIBS | wc -w | tr -d ' ') libraries into $OUT/$ABI"
  rm -rf "$BUILD"
done
