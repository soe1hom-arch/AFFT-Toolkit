# Third-Party Notices

AFFT-Toolkit (AFFT) membundel binary pihak ketiga untuk
menjalankan operasi unpack/repack langsung di perangkat Android. Setiap binary
di bawah ini adalah karya pemiliknya masing-masing dan tunduk pada lisensi
sumbernya. Lisensi Apache-2.0 pada proyek ini **tidak** menaungi binary tersebut.

Semua binary disimpan di `app/src/main/jniLibs/arm64-v8a/` (sebagai
`lib<name>.so`) dan satu fallback di `app/src/main/assets/bin/`.

| Binary | Sumber | Lisensi |
|--------|--------|---------|
| `payload-dumper-go` | [ssut/payload-dumper-go](https://github.com/ssut/payload-dumper-go/tree/a51234eaead276ff3d8b8c4c439c51c7f46a96a8) | Apache-2.0 upstream; binary lama tidak terbukti dibangun dari source bersih, CI rebuild |
| `lpmake` / `lpunpack` | [AOSP system/core (liblp)](https://android.googlesource.com/platform/system/core/) | Apache-2.0 |
| `simg2img` | [AOSP system/core (libsparse)](https://android.googlesource.com/platform/system/core/) | Apache-2.0 |
| `magiskboot` | [Magisk v30.7](https://github.com/topjohnwu/Magisk/tree/e8a58776f1d7bdf852072ad0baa6eceb9a1e4aac) | GPL-3.0-or-later |
| `make_ext4fs` | Binary identik dengan [rendiix/make_ext4fs](https://github.com/rendiix/make_ext4fs/tree/4b805b4ff0129fb8df553247c1edce2fecfada6e) | Apache-2.0 pada proyek; dependensi memiliki lisensi tersendiri |
| `debugfs` | [e2fsprogs v1.47.0](https://github.com/tytso/e2fsprogs/tree/25ad8a431331b4d1d444a70b6079456cc612ac40) | GPL-2.0; LGPL-2.0, BSD-style, dan MIT-style untuk library terkait |
| `mkfs.erofs` / `extract.erofs` | [sekaiacg/erofs-tools](https://github.com/sekaiacg/erofs-tools/tree/4f5d6efc225cf90064a8196b1412692fb89e1e69); CI membangun ulang | GPL-2.0-or-later dan lisensi komponen terkait; lihat catatan di bawah |
| `liblzma` / `libzstd` (via cgo) | [XZ 5.4.6](https://github.com/tukaani-project/xz/tree/v5.4.6) / [zstd 1.5.5](https://github.com/facebook/zstd/tree/v1.5.5) | Domain publik / BSD-3-Clause; binary lama belum terbukti cocok dengan versi ini |

Catatan:

- Sebagian komponen memiliki skrip build di `tools/`; skrip tersebut belum
  membuktikan asal semua binary yang dibundel. Silakan baca lisensi sumber
  sebelum mendistribusikan ulang.
- Jika Anda mendistribusikan APK yang mengandung binary GPL (mis. GPL-3.0),
  pastikan memenuhi kewajiban lisensi GPL, termasuk menyediakan source code
  yang sesuai.
- Workflow CI membangun `debugfs` dari e2fsprogs v1.47.0 dengan
  `tools/build_licensed_binaries.sh`. Build pada branch validasi telah
  berhasil, tetapi fungsi binary pada perangkat Android belum diuji. Binary
  lama di repositori belum terbukti berasal dari source tersebut.

## EROFS Tools

Binary lama `libmkfs.erofs.so` dan `libextract.erofs.so` yang tersimpan di
repositori identik dengan
`mkfs.erofs` dan `extract.erofs` dalam
[rilis Android arm64 v1.8.10-251217](https://github.com/sekaiacg/erofs-tools/releases/tag/v1.8.10-251217).
SHA-256 masing-masing:

- `mkfs.erofs`: `e724b80d259d6c2947b9374a668eb5a09e5a8e4400ba6708aa5605a52e6adfb8`
- `extract.erofs`: `13fa27c5626766607e8ce5c58b1ba1710709cea732d8d380a57449db39b2eae0`

Tag rilis menunjuk ke commit `7274417816e3adfe0cd6d4a8ff194ec5f41268f4`,
tetapi [workflow rilis upstream](https://github.com/sekaiacg/erofs-tools/actions/runs/20300795535)
yang berjalan saat aset diterbitkan menggunakan commit
[`4f5d6efc225cf90064a8196b1412692fb89e1e69`](https://github.com/sekaiacg/erofs-tools/tree/4f5d6efc225cf90064a8196b1412692fb89e1e69);
kedua commit berbeda. Untuk memperoleh source yang menjadi input workflow
tersebut, checkout commit terakhir ini dan jalankan
`git submodule update --init --recursive`. Skrip build, patch (termasuk
patch `libselinux`), dan revisi submodule ada dalam source. Menurut
[COPYING upstream](https://github.com/sekaiacg/erofs-tools/blob/4f5d6efc225cf90064a8196b1412692fb89e1e69/COPYING),
sebagian file library tersedia dengan pilihan GPL-2.0-or-later atau Apache-2.0,
sementara file lain umumnya GPL-2.0-or-later kecuali dinyatakan lain. Teks
GPL-2.0 disertakan di `assets/legal/GPL-2.0.txt`. Dependensi dan hasil build
belum diverifikasi lengkap; kecocokan binary rilis tidak membuktikan build ulang
identik. Log build upstream telah kedaluwarsa sehingga rincian build dan cache
tidak dapat dipastikan dari log.

Workflow CI sekarang membangun kedua executable dari commit tersebut dan
submodule yang dipin menggunakan
[`tools/build_erofs.sh`](https://github.com/soe1hom-arch/AFFT-Toolkit/blob/56e7cfffa14693fb8e996e2747ecf10b13160d46/tools/build_erofs.sh).
Perubahan build disediakan sebagai
[`tools/erofs-no-fuse.patch`](https://github.com/soe1hom-arch/AFFT-Toolkit/blob/56e7cfffa14693fb8e996e2747ecf10b13160d46/tools/erofs-no-fuse.patch);
source upstream juga mengandung patch `libselinux` yang diterapkan saat build.
Patch AFFT menghilangkan libfuse dari link kedua executable, dan skrip
memeriksa perintah link. Build dan pengujian round-trip sederhana dengan ARM64
QEMU berhasil; ini bukan pengujian pada perangkat Android. Build Gradle langsung
tanpa skrip tersebut masih menggunakan binary lama di repositori.

Revisi semua submodule yang terdaftar pada commit build ditemukan di repositori
publik; untuk submodule AOSP, commit juga ditemukan di mirror GitHub. File
`assets/legal/EROFS-DEPENDENCIES.txt` menyertakan lisensi dan notis dari
revisi input build, termasuk lz4, XZ, zstd, zlib, PCRE2, libselinux, libc++,
xxHash, komponen AOSP, dan header UUID e2fsprogs.

Konfigurasi upstream untuk binary lama mencantumkan **libfuse sebagai library statis**.
Direktori `lib` dan `include` libfuse tersedia dengan LGPL-2.1; teks LGPL-2.1
disertakan di `assets/legal/LGPL-2.1.txt`. Penelusuran statis source
`mkfs`, `extract`, `lib`, dan `include` pada commit build tidak menemukan
pemanggilan fungsi `fuse_*` atau include header FUSE. Ini bukan bukti isi
binary yang di-strip atau seluruh input build yang sebenarnya.
Jika masuk, pemenuhan kewajiban LGPL untuk penggantian library/relink harus
dipastikan; source saja dan notis ini bukan bukti kewajiban itu telah dipenuhi.
Kewajiban distribusi binary lama tetap perlu diverifikasi; jangan anggap
kepatuhannya sudah lengkap.

Binary EROFS lama dan build CI menargetkan Android API 31, sesuai upstream;
kompatibilitasnya dengan Android API 26–30 yang didukung aplikasi belum
diverifikasi.

## make_ext4fs

Binary `libmake_ext4fs.so` identik dengan
[`make_ext4fs-arm64-v8a`](https://github.com/rendiix/make_ext4fs/blob/4b805b4ff0129fb8df553247c1edce2fecfada6e/prebuilt_binary/make_ext4fs-arm64-v8a)
yang dipublikasikan dalam repositori `rendiix/make_ext4fs`. SHA-256:
`479404776297664fe176782865c732412ceb920cb1e09b7a31a585df38475618`.
Repositori tersebut memuat source dan skrip build; lisensi utamanya
Apache-2.0, tetapi juga memuat library lain termasuk zlib dan libselinux.
Notis terkait tersedia di `assets/legal/MAKE-EXT4FS-NOTICES.txt`.
Kecocokan binary belum membuktikan bahwa source dan skrip pada commit itu
persis digunakan untuk membangunnya; build ulang belum diverifikasi.
Binary ini memiliki penanda NDK Android API 30; kompatibilitas pada Android
API 26–29 belum diverifikasi.

## debugfs

`libdebugfs.so` yang saat ini tersimpan di repositori memiliki SHA-256
`ce04079ba2c99963e069b8cebc358109cf33e558c391af06921a99d7355fa035`
dan memuat penanda e2fsprogs 1.47.0. [Notis upstream](https://github.com/tytso/e2fsprogs/blob/25ad8a431331b4d1d444a70b6079456cc612ac40/NOTICE)
menyatakan utilitas berlisensi GPL-2.0, `lib/ext2fs` dan `lib/e2p` LGPL-2.0,
`lib/uuid` BSD-style, serta `lib/et` dan `lib/ss` MIT-style. Teks lisensi dan
notis terkait ada di `assets/legal/`. Binary lama belum terbukti berasal dari
source upstream. Workflow build membangunnya ulang dari commit
`25ad8a431331b4d1d444a70b6079456cc612ac40` sebelum membuat APK, tetapi
binary hasilnya belum diuji pada perangkat Android; jangan mendistribusikan
binary lama yang tersimpan dengan mengandalkan notis itu saja.

## payload-dumper-go

Metadata binary menunjukkan modul upstream pada commit
[`a51234eaead276ff3d8b8c4c439c51c7f46a96a8`](https://github.com/ssut/payload-dumper-go/tree/a51234eaead276ff3d8b8c4c439c51c7f46a96a8)
dan `vcs.modified=true`. Lisensi upstream pada commit tersebut adalah
Apache-2.0. Binary mencatat Go 1.22.12, `CGO_ENABLED=1`, path library
liblzma/zstd, serta versi modul Go dalam metadata. Versi library native
tidak tercatat di metadata dan tidak dapat dipastikan dari skrip lama. Binary
tidak dapat dibuktikan berasal dari source bersih pada commit tersebut.
`tools/build_payload_dumper.sh` mem-pin source ke revisi tersebut dan
membangun ulang dari working tree bersih, dengan XZ 5.4.6 dan zstd 1.5.5
sebagai pilihan untuk build baru. Arsip zstd baru dipasang pada salinan lokal
modul Go `gozstd`, tanpa mengubah cache modul Go.

Notis lisensi untuk dependensi Go disertakan di
`assets/legal/PAYLOAD-GO-DEPENDENCIES.txt`; lisensi XZ dan zstd ada di
`assets/legal/NATIVE-COMPRESSION-LICENSES.txt`. Binary lama yang tersimpan
belum terbukti dapat direproduksi. Workflow dikonfigurasi untuk membuat binary
baru dari source yang dipin sebelum mengemas APK. Build pada branch validasi
berhasil, tetapi binary hasilnya belum diuji pada perangkat Android.

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
