# Changelog

## [2.3.0] — 2026-08-14

### Added
- **Navigasi baru (Tools Hub)** — sealed-class routes, back stack Android, deep link antar-tool (`afft://home`, `afft://files`, `afft://tools/{toolId}`)
- **UI profesional** — header konsisten (ScreenHeader), alur bernomor (01/02/03), dialog seragam (AppDialog) di semua layar tool & File Manager
- **Repack dari folder bebas** — folder sumber repack bisa dipilih dari mana saja via browser folder bawaan atau pemilih folder sistem (SAF); daftar partisi & `file_contexts` ikut di-refresh dari folder kustom
- **Riwayat operasi persisten** — history tersimpan per proyek (`history.json`); extract/repack payload/super/filesystem/boot kini tercatat di riwayat proyek; user bisa menghapus riwayat dari sheet Operation History
- **Resume point** — `lastTool`/`lastFile`/`lastStep` tersimpan di metadata; Home menampilkan **Proyek Terbaru** untuk melanjutkan proyek dari tempat terakhir
- **Resume banner (deteksi otomatis)** — Home otomatis mendeteksi proyek dengan titik lanjut dan menampilkan banner **Lanjutkan** untuk membuka tool terakhir
- **Klaim fitur diselaraskan** — Payload = analyze & unpack (repack `payload.bin` tidak didukung), daftar 7 tipe boot dilengkapi di UI
- **Komponen UI reusable** — `AppDialog`, `DialogOptionCard`, `CheckableOptionRow`, `RepackSourceCard`, `SafTree`

### Changed
- **Service split** — ekstrak `LogManager` & `StorageManager` dari `AFFTService`
- Semua dialog (hapus, salin, pindah, buat folder, rename, properti, partisi, browser file) memakai desain seragam

### Testing
- 135 unit test hijau (parsers, engine, workspace, coordinator, AFFTService, sparse, shell)

## [2.2.0] — 2026-08-08

### Added
- **Workspace Engine** — operasi firmware berbasis proyek (create/open/close/rename/delete, recent projects, metadata, history, events, thread-safe persistence)
- **Firmware Analysis Engine** — framework parser tunggal (FirmwareParser) dengan health score, validation & rekomendasi
- **Parser Payload / Boot / Super / Filesystem** — analisis metadata-only (aman untuk image 8–10+ GB) + FirmwareInspector & dashboard (StatusPanel, WorkspaceCard, QuickMetrics)
- **SHA-256 payload on-demand** — metadata tampil instan; hash dihitung lazy/opsional (analisis 8 GB: 9,3 s → 105 ms)
- **Backup policy** — data kerja, workspace & binary dikecualikan dari Android backup (cloud & device transfer)
- Toggle **pencatatan log** (log recording) di sidebar
- CI menjalankan **unit test** otomatis (121 test) di GitHub Actions
- **UI premium**: ikon single-tone + tint dari tema, font custom (Inter & JetBrains Mono, OFL-1.1)
- **Preset tema**: 6 preset (AFFT Green, Midnight Cyan, Amber Solar, Violet Nebula, Cherry Red, Dark Gray Premium) + mode System/Dark/Light + dynamic color, tersimpan permanen
- **Pengaturan Bahasa**: default English, bisa switch ke Bahasa Indonesia; dialog About & semua dialog info mengikuti bahasa global
- **Metadata interaktif**: nilai panjang/krusial dibuka bottom sheet (copy/share / open folder)
- **Dialog About**: Developer, Fitur, Kredit Pihak Ketiga, Lisensi & Atribusi jadi halaman terpisah; dokumen lisensi dibundel di dalam APK (Apache-2.0, OFL-1.1, third-party notice)

### Changed
- **Bump version 2.1.1 → 2.2.0**
- **License MIT → Apache-2.0**
- Rebrand ke **AFFT Toolkit** (launcher, top bar, sidebar drawer, About, monitor)
- File Manager dirombak menjadi **AFFT Manager** (create folder, rename, properties, quick-location chips, layout lebih ringkas)
- About dialog didesain ulang (logo, tagline, halaman terpisah: Pengembang / Fitur / Kredit / Lisensi, bahasa global, kredit liblzma/libzstd)
- Batas manifest payload diturunkan 256 MB → 16 MB (cegah alokasi memori berlebih dari header nakal/corrupt)
- Klasifikasi error lebih jelas: status **INVALID_HEADER** (magic/header salah); file terpotong → **CORRUPTED_METADATA** (sebelumnya jatuh ke "Parser Failure" generik)

### Fixed
- Sinkronisasi state debug mode setelah aktivitas dibuat ulang
- Pipeline log dibuat thread-safe (logLock), duplikasi log dihilangkan
- Sparse image rusak/non-sparse ditolak dengan validasi terstruktur (tanpa crash)
- Operasi panjang (copy/extract/convert) respect coroutine cancellation — proses di-destroy, output parsial dibersihkan
- WorkspaceEngine thread-safe (synchronized lock) — history/metadata tidak korup saat operasi paralel

### Testing
- 121 unit test hijau (parsers, engine, workspace, coordinator, AFFTService, sparse, shell)
- Regression & performance verification: seluruh analisis metadata ms-level; stress 30× open/analyze/close stabil

## [2.1.1] — 2026-07-05

### Fixed
- **11GB copy crash**: Replaced blocking `File.copyTo()` with chunked streaming on IO dispatcher
- **Repack optimization**: Skip simg2img for RAW partitions (lpunpack output is already raw)
- **Removed -S flag from lpmake**: Direct raw output, safe for flashing

### Changed
- Replaced lucky-arch with **lpunpack/lpmake/simg2img** static AOSP binaries
- Bump version 2.1.0 → 2.1.1

## [2.1.0] — 2026-07-04

- Super unpack/repack with lucky-arch
- Boot repack + AVB signing
- File Manager upgrade (search, sort, rename, properties)
- Full-screen About dialog with binary credits
- Fixed ANR, duplicate logs, boot auto-detect

## [2.0.3] — 2026-06-28

- **Critical fix**: Rebuilt payload-dumper-go as static binary (CGO_ENABLED=0)
- Logs Viewer + auto-cleanup
- ShellExecutor fallback chain (direct → linker64 → sh -c)

## [2.0.2] — 2026-06-27

- Sidebar drawer console log
- Safety check for clean (canonicalPath)
- Export via renameTo (instant) + fallback copy

## [2.0.1] — 2026-06-26

- Auto-detect input files
- EROFS support
- ELF dynamic/static detection

## [2.0.0] — 2026-06-25

- Initial release
