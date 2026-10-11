# bluetooth

基于 Xposed/LSPosed 的低功耗蓝牙调试工具：发现设备、识别厂商，并向 `com.android.bluetooth`
注入模拟广播，让任意扫描器都能看到你配置的设备。

> 其他语言：[English](README.md) · 简体中文

## 功能特性

- **规则驱动的扫描注入** —— 类候选 + 完整方法签名 + 类型化参数映射，自动适配不同 AOSP 版本
  （Android 7.0+）；类名或签名变动时，更新规则即可，无需改应用。
- **两种构建 flavor**
  - `modern` —— LibXposed API **102+**（较新 LSPosed）
  - `legacy` —— 经典 Xposed / 旧版 LSPosed / EdXposed（API **< 102**）
- BLE 外围广播、WebDAV 同步、应用内检查更新（APK + hook 规则）。

## 安装

1. 从 [Releases](https://github.com/ShadowSmallBaby/bluetooth/releases) 下载：
   - LSPosed API ≥ 102 用 `bluetooth-modern.apk`；若提示「该模块需要更新的 Xposed 版本 102」，
     改用 `bluetooth-legacy.apk`。两者功能相同，**二选一**安装。
2. 在 LSPosed 启用模块。作用域勾选 **com.android.bluetooth**；**legacy 包**还需勾选
   **本应用自身**（用于激活检测），modern 包不需要。
3. 打开应用，选择要模拟的设备（或手动填 MAC / 广播数据），开启模拟。
4. 关开一次蓝牙让 Hook 载入，再用任意扫描器查看。

### WebDAV 同步

- **发送端**：填写 WebDAV 账号，选择按 MAC 或公司名，启用同步并作为发送端。
- **接收端**：填写 WebDAV 账号，启用同步。

## 构建

需要 JDK 17。

```bash
./gradlew assembleModernRelease assembleLegacyRelease
```

CI（`.github/workflows/build.yml`）会构建两个 flavor；推送 `v*` tag 会把它们连同
`hook_rules.json`、`update-manual.json` 一起发布到 GitHub Release。

## 协议

GPL-3.0

## 致谢

Fork 自 [AnkioTomas/bluetooth](https://github.com/AnkioTomas/bluetooth)（最初由 dreamncn 开发）。
本 Fork 不再跟进上游，以上内容均描述本项目。
