# Third-Party Notices

AFFT-Toolkit (AFFT) membundel binary helper pihak ketiga untuk
menjalankan operasi unpack/repack langsung di perangkat Android. Setiap binary
di bawah ini adalah karya pemiliknya masing-masing dan tunduk pada lisensi
sumbernya. Lisensi Apache-2.0 pada proyek ini **tidak** menaungi binary tersebut.

Semua binary **dibangun dari source** saat build (`tools/build_all.sh`,
versi yang di-pin di `tools/build_config.sh`) dan dikemas sebagai
`lib<name>.so` di `app/src/main/jniLibs/arm64-v8a/`. Tidak ada binary
prebuilt yang di-commit ke repo ini (lihat `F-DROID.md`).

| Binary | Sumber (versi pin) | Lisensi |
|--------|--------------------|---------|
| `payload-dumper-go` | [ssut/payload-dumper-go](https://github.com/ssut/payload-dumper-go) @ 2.1.0 | GPL-3.0 |
| `lpmake` / `lpunpack` | [AOSP system/extras + system/core (liblp)](https://android.googlesource.com/platform/system/extras) @ android-14.0.0_r1 | Apache-2.0 |
| `simg2img` | [AOSP system/core (libsparse)](https://android.googlesource.com/platform/system/core/) @ android-14.0.0_r1 | Apache-2.0 |
| `magiskboot` | [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk) @ v25.1 (pure-C++, dukung boot/vendor_boot v4) | GPL-3.0 |
| `mke2fs` / `debugfs` | [e2fsprogs](https://github.com/tytso/e2fsprogs) @ v1.47.2 | GPL-2.0 |
| `mkfs.erofs` / `extract.erofs` | [erofs-utils](https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils.git) @ v1.9.1 (`dump.erofs` dikemas sebagai `extract.erofs`) | GPL-2.0 |
| `liblzma` (via cgo) | [tukaani-project/xz](https://github.com/tukaani-project/xz) @ 5.4.6 | 0BSD |
| `libzstd` (via cgo) | [facebook/zstd](https://github.com/facebook/zstd) @ v1.5.5 | BSD-3-Clause |
| `liblz4` (untuk mkfs.erofs) | [lz4/lz4](https://github.com/lz4/lz4) @ v1.9.4 | BSD-2-Clause |

Catatan:

- `mke2fs` menggantikan `make_ext4fs` yang deprecated: `make_ext4fs` sudah
  dihapus dari AOSP sebelum Android 9 dan tidak memiliki sumber upstream
  yang maintained.
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
