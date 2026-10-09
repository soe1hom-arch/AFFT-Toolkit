# F-Droid submission guide (AFFT-Toolkit)

## Kenapa ditolak kemarin

F-Droid `main` repo mewajibkan **build from source**. Repo ini sebelumnya
me-commit 10 binary prebuilt:

- `app/src/main/jniLibs/arm64-v8a/lib*.so` (9 file: payload-dumper-go,
  magiskboot, lpmake, lpunpack, simg2img, mkfs.erofs, extract.erofs,
  debugfs, make_ext4fs)
- `app/src/main/assets/bin/payload-dumper-go` (duplikat)

Itu yang di-flag scanner (`Binaries found`). Sejak PR ini, semuanya
diperbaiki seperti di bawah.

## Apa yang sudah diubah (Opsi A)

1. **Binary di-untrack dari git** (`git rm --cached`) + di-ignore
   (`.gitignore`). Yang tertinggal di repo hanya placeholder `.gitkeep`.
2. **Semua 9 helper dibangun dari source yang di-pin** — `tools/build_all.sh`
   dengan versi di `tools/build_config.sh`. Cek cepat tanpa NDK:
   `tools/build_all.sh --check` (10/10 ref terverifikasi upstream).
3. **`make_ext4fs` diganti `mke2fs`** (e2fsprogs). Alasannya: `make_ext4fs`
   sudah dihapus AOSP sebelum Android 9 — diverifikasi hilang di tag
   android-9/10/11/14 dan semua branch LineageOS; tidak ada sumber upstream
   yang maintained. Pengganti resminya `mke2fs -t ext4 -d <dir>` (dipakai
   AOSP sendiri sejak 2019). Perubahan kode hanya di `BinaryManager`,
   `AFFTService.repackExt4`, dan kredit di About.
4. **Gradle otomatis verify/build native** (`ensureNativeBinaries` di
   `app/build.gradle.kts`, jalan di `preBuild`). Tanpa NDK → build gagal
   dengan pesan jelas (bukan APK bisu tanpa helper).
5. **`versionCode` bisa di-pin** via env `AFFT_VERSION_CODE` (default tetap
   skema tanggal `YYMMDDnn`). Wajib di-pin untuk build F-Droid supaya
   reproducible.
6. **Contoh recipe** di `fdroid/com.afft.app.yml` + CI (`.github/workflows`)
   yang install NDK r27 + Go + autotools lalu build dari source.

## Cara submit ke F-Droid

1. Pastikan tag release sudah di-push (mis. `v2.3.0`) dan CI hijau —
   F-Droid hanya build dari tag.
2. Fork https://gitlab.com/fdroid/fdroiddata, tambahkan file
   `metadata/com.afft.app.yml` dari contoh `fdroid/com.afft.app.yml`
   di repo ini. Sesuaikan `versionName`/`versionCode`/`commit` dengan tag
   terbaru. `versionCode` = nilai `AFFT_VERSION_CODE` yang dipakai build
   (skema tanggal: `YYMMDD01`, mis. 9 Okt 2026 → `26100901`).
3. Jalankan `fdroid lint com.afft.app`, perbaiki warning yang muncul.
4. Buka Merge Request ke fdroiddata. Bot akan build dan menjalankan
   scan binary — sekarang harus bersih karena tidak ada file biner
   yang ter-track (`tools/verify_binaries.sh` membuktikannya).
5. Jawab review dengan menunjuk ke `tools/README.md` +
   `THIRD_PARTY_NOTICES.md` (semua sumber + lisensi + versi pin terdokumentasi).

## Catatan lisensi buat reviewer

- Kode aplikasi: Apache-2.0. Helper: GPL-3.0 (payload-dumper-go, magiskboot),
  GPL-2.0 (erofs-utils, e2fsprogs), Apache-2.0 (AOSP), 0BSD/BSD (xz/zstd/lz4).
  Semua FOSS dan boleh didistribusikan F-Droid; kewajiban GPL (menyediakan
  source) terpenuhi karena recipe build dari source yang di-pin.
- `dump.erofs` (erofs-utils) dikemas dengan nama `extract.erofs` sesuai
  nama yang diharapkan aplikasi — binary-nya sama, hanya nama file.
- Magisk di-pin ke **v25.1**: rilis terakhir dengan `magiskboot` murni C++
  (tanpa Rust toolchain) dan sudah mendukung header boot/vendor_boot v4.
  Naik ke magiskboot yang lebih baru berarti membangun via `build.py`
  Magisk dengan NDK + Rust + protobuf.
