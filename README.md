
<p align="center">
  <img src="screenshots/logo.png" width="420" alt="AFFT Toolkit Logo">
</p>

<h1 align="center">AFFT Toolkit</h1>

<p align="center">
  <strong>Android Firmware Full Toolkit</strong><br>
  Analyze, unpack, modify, and repack Android firmware directly on your Android device.
  <br>
  No PC required. No Termux required. Fully on-device.
</p>

<p align="center">
  <a href="https://github.com/soe1hom-arch/AFFT-Toolkit/releases/latest"><img src="https://img.shields.io/github/v/release/soe1hom-arch/AFFT-Toolkit?style=flat-square&label=Release&color=blue&v=2.3.0" alt="Release"></a>
  <a href="https://github.com/soe1hom-arch/AFFT-Toolkit/actions"><img src="https://img.shields.io/github/actions/workflow/status/soe1hom-arch/AFFT-Toolkit/build.yml?style=flat-square&label=Build" alt="Build"></a>
  <a href="https://github.com/soe1hom-arch/AFFT-Toolkit/releases"><img src="https://img.shields.io/github/downloads/soe1hom-arch/AFFT-Toolkit/total?style=flat-square&label=Downloads&color=success" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen?style=flat-square" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/ARM64-only-red?style=flat-square" alt="ARM64 only">
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square" alt="License">
</p>

<p align="center">
  <a href="https://soe1hom-arch.github.io/AFFT-Toolkit/">🌐 Website</a> ·
  <a href="https://github.com/soe1hom-arch/AFFT-Toolkit/releases/latest">📥 Download</a> ·
  <a href="CHANGELOG.md">📋 Changelog</a> ·
  <a href="https://github.com/soe1hom-arch/AFFT-Toolkit/discussions">💬 Discussions</a>
</p>

---

## 📖 Overview

AFFT Toolkit (Android Firmware Full Toolkit) is an Android application for analyzing, unpacking, modifying, and repacking supported Android firmware images directly on Android devices.

It brings multiple firmware-processing utilities into one interface, allowing users to work with supported firmware formats, inspect metadata, manage workspace projects, review operation logs, and organize processing results without requiring a PC or a separate Termux environment.

AFFT Toolkit is intended for firmware enthusiasts, Android developers, and advanced users who understand the risks associated with firmware modification.

> **Important:** Successful analysis, extraction, or repacking does not guarantee that the resulting firmware is compatible with a device or safe to flash.

---

## ⚠️ Disclaimer & Safety Warning

**Firmware modification and flashing involve risks. Use AFFT Toolkit at your own risk.**

Incorrect or incompatible firmware operations can cause serious problems, including:

- **Device bricking:** A device may become unbootable or permanently unusable.
- **Bootloops and boot failures:** Modified or incompatible images may prevent Android from starting.
- **Firmware corruption:** Incorrect processing, modification, or repacking may produce damaged or unusable firmware images.
- **Data loss:** Firmware operations, recovery procedures, or flashing may erase personal files and device data.
- **Device incompatibility:** Firmware images may not be compatible with a particular device model, hardware revision, region, partition layout, or Android version.
- **Interrupted operations:** Insufficient storage, unexpected errors, damaged source files, or interrupted processing may result in incomplete output.

### Developer Liability

AFFT Toolkit is provided **"AS IS" and "AS AVAILABLE," without warranties of any kind**, to the maximum extent permitted by applicable law.

The developer and contributors are not responsible for device bricking, bootloops, firmware corruption, failed flashing, data loss, loss of device functionality, or other damages arising from the use, misuse, or inability to use AFFT Toolkit, to the extent permitted by applicable law.

Users are solely responsible for selecting firmware files, performing modifications, verifying generated images, and deciding whether to flash or apply the resulting files to a device.

**AFFT Toolkit does not guarantee that any generated or modified firmware image will work correctly on your device.**

Nothing in this disclaimer excludes or limits liability that cannot legally be excluded or limited under applicable law.

### Safety Recommendations

Before performing firmware-related operations:

1. **Back up your data.** Create and verify an independent backup of important files and device data.
2. **Keep the original firmware.** Never overwrite your only copy of the original image.
3. **Verify compatibility.** Check the exact device model, hardware variant, firmware version, and partition requirements.
4. **Check processing results.** Review logs, validation results, file sizes, and checksums where available.
5. **Maintain sufficient storage.** Large firmware operations may require substantial free space.
6. **Avoid interruptions.** Keep the device powered and avoid interrupting operations while files are being processed.
7. **Understand recovery procedures.** Learn the appropriate recovery method for your device before attempting to flash modified images.
8. **Do not flash blindly.** A successful repack does not prove that the resulting image is bootable or safe to flash.

If you do not understand the potential risks or cannot recover your device from a failed flash, do not flash modified firmware.

---

## 📥 Download

[⬇️ **Download the Latest APK**](https://github.com/soe1hom-arch/AFFT-Toolkit/releases/latest)

### Installation

1. Download the APK from the official GitHub Releases page.
2. Open the APK and allow installation from the relevant source if Android requests it.
3. Launch AFFT Toolkit.
4. Grant **Manage All Files** access when required.
5. Select a tool, choose a supported firmware file, and start the desired operation.

Download releases from trusted sources. Review release notes and verify published checksums when available.

---

## 🎯 Features

| Feature | Description |
|---------|-------------|
| **Payload Dumper** | Analyze and unpack `payload.bin` OTA firmware using the payload-dumper-go engine, with on-demand SHA-256 calculation. |
| **Super Image** | Unpack and repack `super.img` using sparse-aware processing with `lpunpack` and `lpmake`. |
| **Filesystem** | Extract and repack supported EROFS and ext4 images using available filesystem utilities. |
| **Boot Family** | Unpack and repack supported boot image formats, including `vendor_boot`, using magiskboot. |
| **Firmware Analysis** | Analyze supported payload, boot, super, and filesystem formats with validation and recommendations. |
| **Firmware Inspector** | Review project status, analysis results, health indicators, validation findings, and operation history. |
| **Workspace Projects** | Organize operations into projects with structured directories and chronological history. |
| **Tools Hub & Navigation** | Navigate between tools using structured routes, Android back-stack support, and `afft://` deep links. |
| **Repack Anywhere** | Select repack source folders using the built-in browser or Android's system file picker. |
| **Professional UI** | Consistent headers, numbered steps, and unified dialogs across supported screens. |
| **Live Status** | Monitor operation status through animated status cards on the Home screen and operation screens. |
| **Interactive Metadata** | Inspect long paths, hashes, fingerprints, and other metadata through interactive detail sheets. |
| **AFFT Manager** | Browse, search, sort, copy, move, delete, rename, import, and manage files and folders. |

---

## 📸 Preview

<table border="0" cellpadding="6" align="center">
  <tr>
    <td><img src="screenshots/main.png" width="220" alt="Home / Dashboard"></td>
    <td><img src="screenshots/payload.png" width="220" alt="Payload Dumper"></td>
    <td><img src="screenshots/super.png" width="220" alt="Super Image"></td>
  </tr>
  <tr>
    <td><img src="screenshots/boot.png" width="220" alt="Boot Image"></td>
    <td><img src="screenshots/filesystem.png" width="220" alt="Filesystem"></td>
    <td><img src="screenshots/filemanager.png" width="220" alt="AFFT Manager"></td>
  </tr>
  <tr align="center">
    <td colspan="3"><img src="screenshots/console.png" width="400" alt="Console Output"></td>
  </tr>
</table>

---

## 🗂 Workspace Projects

Every firmware operation belongs to a **Workspace Project**, helping keep related files, logs, and outputs organized.

Default workspace location:

```text
/storage/emulated/0/Android/data/com.afft.app/files/workspace/
└── <ProjectName>/
    ├── metadata.json
    ├── logs/
    ├── output/
    ├── temp/
    ├── cache/
    ├── payload/
    ├── boot/
    ├── super/
    ├── filesystem/
    ├── apk/
    └── reports/
```

| Directory | Purpose |
|-----------|---------|
| `metadata.json` | Project information, device details, firmware type, status, and health information. |
| `logs/` | Operation logs and diagnostic information. |
| `output/` | Extracted, processed, or repacked results. |
| `temp/` | Temporary working files. |
| `cache/` | Cached processing data. |
| `payload/` | Payload-related working files. |
| `boot/` | Boot image working files. |
| `super/` | Super image working files. |
| `filesystem/` | Filesystem image working files. |
| `apk/` | APK-related working files. |
| `reports/` | Analysis reports. |

Workspace projects are designed to persist across application restarts and configuration changes. Missing directories are recreated automatically when required.

**Note:** Workspace persistence is not a substitute for an independent backup. Store important source files and results separately.

---

## 📋 Requirements

| Requirement | Specification |
|-------------|---------------|
| **Android** | 8.0+ (API 26) |
| **Architecture** | ARM64 only |
| **RAM** | 4 GB minimum; 6 GB+ recommended |
| **Storage** | At least 10 GB of free space recommended for large firmware files |
| **Permissions** | `MANAGE_EXTERNAL_STORAGE`, foreground service, and `POST_NOTIFICATIONS` where required |

Actual resource requirements depend on firmware size, image format, device capabilities, and the operation being performed.

---

## 🔐 Privacy & Backup

AFFT Toolkit excludes its working data from Android Auto Backup and device-transfer backups through the configured backup rules.

The excluded data includes:

- `afft_work/` — input files, temporary data, logs, extraction files, and repack results.
- `workspace/` — firmware workspace projects.
- Deployed binaries — binaries intended to be re-extracted from application assets.

These exclusions are configured through:

- `res/xml/data_extraction_rules.xml` for Android 12 and later.
- `res/xml/backup_rules.xml` for older Android versions.

Firmware files and workspace data are excluded from the configured Android automatic backup process.

**Important:** Backup exclusions do not protect data against every form of loss. Maintain independent backups of important files.

---

## 🙏 Credits

AFFT Toolkit uses or incorporates third-party tools and libraries for firmware processing and application presentation.

| Component | Source |
|-----------|--------|
| **payload-dumper-go** | [ssut/payload-dumper-go](https://github.com/ssut/payload-dumper-go) |
| **lpmake / lpunpack** | [AOSP liblp](https://android.googlesource.com/platform/system/core/) |
| **magiskboot** | [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk) |
| **simg2img** | [Android Open Source Project](https://android.googlesource.com/platform/system/core/) |
| **mkfs.erofs / extract.erofs** | [erofs-utils](https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils.git) |
| **make_ext4fs / debugfs** | [Android Open Source Project](https://android.googlesource.com/platform/system/core/) |
| **liblzma / libzstd** | [Tukaani Project — XZ](https://github.com/tukaani-project/xz) / [Facebook Zstandard](https://github.com/facebook/zstd) |
| **Inter** | [rsms/inter](https://github.com/rsms/inter) — OFL-1.1 |
| **JetBrains Mono** | [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono) — OFL-1.1 |

Third-party components remain subject to their respective licenses and terms. See the relevant upstream projects and repository notices for details.

---

## 👨‍💻 Developer

**Wandi / Soehom (Lucky Man)**

AFFT Toolkit is an independent Android firmware utility project.

- [Report an Issue](https://github.com/soe1hom-arch/AFFT-Toolkit/issues)
- [GitHub Discussions](https://github.com/soe1hom-arch/AFFT-Toolkit/discussions)
- [Source Repository](https://github.com/soe1hom-arch/AFFT-Toolkit)

When reporting an issue, include the application version, Android version, device architecture, relevant logs, and steps to reproduce the problem. Remove personal or sensitive information before sharing logs.

---

## 📄 License

AFFT Toolkit is distributed under the **Apache License 2.0**.

Copyright © 2026 Wandi (Soehom-arch).

See [LICENSE](LICENSE) and [NOTICE](NOTICE) for applicable license terms and notices. Third-party binaries, libraries, and assets remain subject to their respective licenses.

---

<p align="center">
  <strong>AFFT Toolkit</strong><br>
  Android firmware tools, directly on your device.
</p>
