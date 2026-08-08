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
| `magiskboot` | [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk) | GPL-3.0 |
| `make_ext4fs` / `debugfs` | [AOSP system/core](https://android.googlesource.com/platform/system/core/) | Apache-2.0 |
| `mkfs.erofs` / `extract.erofs` | [erofs-utils](https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils.git) | GPL-2.0 |
| `liblzma` (via cgo) | [tukaani-project/xz](https://github.com/tukaani-project/xz) | 0BSD |
| `libzstd` (via cgo) | [facebook/zstd](https://github.com/facebook/zstd) | BSD-3-Clause |

Catatan:

- Skrip build di `tools/` menentukan bagaimana binary dibangun dari sumber
  masing-masing. Silakan baca lisensi sumber sebelum mendistribusikan ulang.
- Jika Anda mendistribusikan APK yang mengandung binary GPL (mis. GPL-3.0),
  pastikan memenuhi kewajiban lisensi GPL, termasuk menyediakan source code
  yang sesuai.

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
