# Third-Party Notices

AFFT-Toolkit (AFFT) membundel binary biner pihak ketiga untuk
menjalankan operasi unpack/repack langsung di perangkat Android. Setiap binary
di bawah ini adalah karya pemiliknya masing-masing dan tunduk pada lisensi
sumbernya. Lisensi Apache-2.0 pada proyek ini **tidak** menaungi binary tersebut.

Semua binary disimpan di `app/src/main/jniLibs/arm64-v8a/` (sebagai
`lib<name>.so`) dan satu fallback di `app/src/main/assets/bin/`.

| Binary | Sumber | Lisensi |
|--------|--------|---------|
| `payload-dumper-go` | [ssut/payload-dumper-go](https://github.com/ssut/payload-dumper-go) | GPL-3.0 |
| `lpmake` / `lpunpack` | [AOSP system/core (liblp)](https://android.googlesource.com/platform/system/core/) | Apache-2.0 |
| `simg2img` | [AOSP system/core (libsparse)](https://android.googlesource.com/platform/system/core/) | Apache-2.0 |
| `magiskboot` | [Magisk v30.7](https://github.com/topjohnwu/Magisk/tree/e8a58776f1d7bdf852072ad0baa6eceb9a1e4aac) | GPL-3.0-or-later |
| `make_ext4fs` / `debugfs` | [AOSP system/core](https://android.googlesource.com/platform/system/core/) | Apache-2.0 |
| `mkfs.erofs` / `extract.erofs` | [erofs-utils](https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils.git) | GPL-2.0 |
| `liblzma` (via cgo) | [tukaani-project/xz](https://github.com/tukaani-project/xz) | 0BSD |
| `libzstd` (via cgo) | [facebook/zstd](https://github.com/facebook/zstd) | BSD-3-Clause |

Catatan:

- Sebagian komponen memiliki skrip build di `tools/`; skrip tersebut belum
  membuktikan asal semua binary yang dibundel. Silakan baca lisensi sumber
  sebelum mendistribusikan ulang.
- Jika Anda mendistribusikan APK yang mengandung binary GPL (mis. GPL-3.0),
  pastikan memenuhi kewajiban lisensi GPL, termasuk menyediakan source code
  yang sesuai.

## MagiskBoot v30.7

Binary `app/src/main/jniLibs/arm64-v8a/libmagiskboot.so` identik dengan
`lib/arm64-v8a/libmagiskboot.so` dalam
[APK resmi Magisk v30.7](https://github.com/topjohnwu/Magisk/releases/tag/v30.7).
SHA-256 binary: `d7440e2cd89899426e809554bf793baef9804ccbe5a52ce34a8b6242725d3c77`.

Sumber Magisk v30.7: https://github.com/topjohnwu/Magisk/tree/e8a58776f1d7bdf852072ad0baa6eceb9a1e4aac
(commit `e8a58776f1d7bdf852072ad0baa6eceb9a1e4aac`). Clone repositori dan
checkout commit tersebut, lalu jalankan `git submodule update --init --recursive`
untuk mengambil submodule yang sesuai. Petunjuk build upstream tersedia di
https://github.com/topjohnwu/Magisk/blob/v30.7/docs/build.md dan `build.py`
pada commit tersebut. Build ulang belum diverifikasi menghasilkan byte identik.

Magisk dilisensikan GPL-3.0-or-later; teks GPL-3.0 disertakan dalam APK di
`assets/legal/GPL-3.0.txt`. MagiskBoot disediakan tanpa jaminan.

## Font

Font berikut dibundel di `app/src/main/res/font/` dan tunduk pada
SIL Open Font License 1.1 (OFL-1.1):

| Font | Sumber | Lisensi |
|------|--------|---------|
| Inter (Regular, Medium, SemiBold, Bold) | [rsms/inter](https://github.com/rsms/inter) | OFL-1.1 |
| JetBrains Mono (Regular, Medium, Bold) | [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono) | OFL-1.1 |

Catatan lisensi font: kedua font dilisensikan SIL Open Font License 1.1
(OFL-1.1). Teks lengkap OFL-1.1 tersedia di
https://openfontlicense.org/ dan menyertai file .ttf sesuai jenis
binernya. Font dikirimkan dalam APK hanya dalam konteks aplikasi
(untuk keperluan tampilan) dan tidak dijual terpisah.
