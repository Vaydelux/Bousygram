#!/usr/bin/env bash
# Isolated qualification build: never installs into app/src/main/jniLibs.
set -euo pipefail
ndk="$(realpath "${1:?NDK r28c root required}")"
output="$(realpath -m "${2:?New output directory required}")"
[[ ! -e "$output" ]] || { echo "Output already exists" >&2; exit 1; }
grep -Eq '^Pkg.Revision[[:space:]]*=[[:space:]]*28\.2\.13676358[[:space:]]*$' "$ndk/source.properties"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
[[ -x "$toolchain/llvm-readelf" ]]
version=8.1.3
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
mkdir -m 700 "$scratch/gnupg"
export GNUPGHOME="$scratch/gnupg"
archive="ffmpeg-$version.tar.xz"
curl --fail --silent --show-error --location --proto '=https' --retry 3 --max-time 180 "https://ffmpeg.org/releases/$archive" -o "$scratch/$archive"
curl --fail --silent --show-error --location --proto '=https' --retry 3 --max-time 60 "https://ffmpeg.org/releases/$archive.asc" -o "$scratch/$archive.asc"
curl --fail --silent --show-error --location --proto '=https' --max-time 60 https://ffmpeg.org/ffmpeg-devel.asc -o "$scratch/key.asc"
gpg --batch --import "$scratch/key.asc"
fingerprint="$(gpg --batch --with-colons --fingerprint | awk -F: '$1=="pub"{primary=1;next} primary&&$1=="fpr"{print $10;primary=0}')"
[[ "$fingerprint" == FCF986EA15E6E293A5644F10B4322F04D67658D8 ]]
gpg --batch --verify "$scratch/$archive.asc" "$scratch/$archive"
tar -xJf "$scratch/$archive" -C "$scratch"
mkdir -p "$output"
sha256sum "$scratch/$archive" | awk '{print $1 "  ffmpeg-8.1.3.tar.xz"}' > "$output/SOURCE.sha256"
cp "$ndk/source.properties" "$output/NDK.properties"
cp "$scratch/ffmpeg-$version/COPYING.LGPLv2.1" "$output/"
for abi in arm64-v8a armeabi-v7a; do
    case "$abi" in
        arm64-v8a) arch=aarch64; triple=aarch64-linux-android ;;
        armeabi-v7a) arch=arm; triple=armv7a-linux-androideabi ;;
    esac
    cc="$toolchain/${triple}26-clang"
    prefix="$scratch/install-$abi"
    mkdir "$scratch/build-$abi" "$output/$abi"
    (
        cd "$scratch/build-$abi"
        "$scratch/ffmpeg-$version/configure" \
            --prefix="$prefix" --target-os=android --arch="$arch" --enable-cross-compile \
            --cc="$cc" --ar="$toolchain/llvm-ar" --nm="$toolchain/llvm-nm" \
            --ranlib="$toolchain/llvm-ranlib" --strip="$toolchain/llvm-strip" \
            --disable-autodetect --disable-everything --disable-network --disable-programs --disable-doc \
            --disable-avdevice --disable-avfilter --disable-swscale --disable-swresample \
            --disable-gpl --disable-nonfree --disable-asm --enable-pic --enable-static --disable-shared \
            --enable-avformat --enable-avcodec --enable-avutil \
            --enable-demuxer=matroska,mpegts --enable-muxer=mp4 \
            --enable-parser=h264,aac --enable-decoder=h264,aac \
            --enable-bsf=aac_adtstoasc,extract_extradata
        make -j2
        make install
        cp config.h config_components.h ffbuild/config.mak "$output/$abi/"
    )
    "$cc" -std=c11 -O2 -Wall -Wextra -Werror -fPIC -fvisibility=hidden \
        -I"$prefix/include" -c "$root/native_cast_remux.c" -o "$output/$abi/native_cast_remux.o"
    "$cc" -shared -Wl,--no-undefined -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384 \
        -Wl,-soname,libbousygram_cast_remux.so \
        "$output/$abi/native_cast_remux.o" -L"$prefix/lib" \
        -Wl,--start-group -lavformat -lavcodec -lavutil -Wl,--end-group \
        -lm -lz -ldl -latomic -o "$output/$abi/libbousygram_cast_remux.so"
    "$toolchain/llvm-readelf" -h -l -d --wide "$output/$abi/libbousygram_cast_remux.so" > "$output/$abi/ELF.txt"
    "$toolchain/llvm-nm" -D --defined-only "$output/$abi/libbousygram_cast_remux.so" > "$output/$abi/EXPORTS.txt"
    grep -q 'Java_com_vaydelux_bousygram_core_cast_NativeCastRemuxBridge_run$' "$output/$abi/EXPORTS.txt"
    awk '$1=="LOAD" {seen++; if ($NF !~ /^0x[4-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F]+$/ && $NF != "0x10000") bad=1} END {exit !(seen && !bad)}' "$output/$abi/ELF.txt"
    case "$abi" in
        arm64-v8a) grep -Eq 'Machine:.*AArch64' "$output/$abi/ELF.txt" ;;
        armeabi-v7a) grep -Eq 'Machine:.*ARM' "$output/$abi/ELF.txt" ;;
    esac
    (cd "$output/$abi" && sha256sum libbousygram_cast_remux.so native_cast_remux.o > SHA256SUMS)
done
echo "Native qualification binaries built; not approved for packaging."
