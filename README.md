# bluetooth

Low-energy Bluetooth debugging tool with Xposed/LSPosed-based scan simulation: discover
devices, identify vendors, and inject simulated advertisements into `com.android.bluetooth`
so any scanner app sees the device you configured.

> Read this in other languages: English · [简体中文](README.zh_CN.md)

## Features

- **Rule-driven scan injection** — class candidates + full method signatures + typed argument
  mapping adapt to different AOSP versions (Android 7.0+), so a renamed class or changed
  signature can be handled by updating rules instead of the app.
- **Two build flavors**
  - `modern` — LibXposed API **102+** (newer LSPosed)
  - `legacy` — classic Xposed / older LSPosed / EdXposed (API **< 102**)
- BLE peripheral advertising, WebDAV sync, and in-app update check (APK + hook rules).

## Install

1. Download from [Releases](https://github.com/ShadowSmallBaby/bluetooth/releases):
   - `bluetooth-modern.apk` for LSPosed API ≥ 102. If it reports *"module requires Xposed API
     102"*, use `bluetooth-legacy.apk` instead. The two are functionally identical — install only one.
2. Enable the module in LSPosed. Scope: check **com.android.bluetooth**; the **legacy** build
   additionally needs **this app itself** (for activation detection). The modern build does not.
3. Open the app, pick a device to emulate (or enter MAC / advertisement data), enable simulation.
4. Toggle Bluetooth off/on so the hook reloads, then scan from any app.

### WebDAV sync

- **Sender**: enter your WebDAV account, choose MAC or company name, enable sync as sender.
- **Receiver**: enter your WebDAV account, enable sync.

## Build

Requires JDK 17.

```bash
./gradlew assembleModernRelease assembleLegacyRelease
```

CI (`.github/workflows/build.yml`) builds both flavors; pushing a `v*` tag publishes them to a
GitHub Release together with `hook_rules.json` and `update-manual.json`.

## License

GPL-3.0

## Credits

Forked from [AnkioTomas/bluetooth](https://github.com/AnkioTomas/bluetooth) (originally by
dreamncn). This fork no longer tracks upstream; everything above describes this project.
