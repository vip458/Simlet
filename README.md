# Simlet

<p align="center">
  <img src="brand/png/simlet-256.png" width="128" alt="Simlet">
</p>

我做的小工具：**不 Root，改的是手机「以为」你的 SIM 卡来自哪里。**

Android 会把 SIM 卡上报的国家码和运营商名交给各种应用去判断你在哪、用的是哪家运营商。Simlet 改的就是这份上报——不写卡、不动基带、不绕过任何鉴权，随时一键还原。

[![License](https://img.shields.io/badge/license-Apache--2.0-6b7280)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![API](https://img.shields.io/badge/API-26%2B-2563eb)
![Root](https://img.shields.io/badge/root-not_required-16a34a)

> 用途是解除按 SIM 地区判断的限制，或修正系统认错运营商地区导致的问题。它不会让受限的网络功能变得可用，也不改 ICCID / IMSI。

## 界面

| | | |
|:---:|:---:|:---:|
| <img src="docs/screenshots/home-light.png" width="230" alt="首页"> | <img src="docs/screenshots/home-dark.png" width="230" alt="深色模式"> | <img src="docs/screenshots/home-english.png" width="230" alt="英文界面"> |
| 首页 · 双卡与目标国家码 | 深色档 | 中英随时切 |
| <img src="docs/screenshots/region-sheet.png" width="230" alt="国家码选择"> | <img src="docs/screenshots/operator-sheet.png" width="230" alt="运营商选择"> | <img src="docs/screenshots/settings.png" width="230" alt="设置页"> |
| 国家码 · 搜索与自定义 | 运营商 · 按国家分组 | 外观与更新 |

## 你需要准备什么

Android 8.0 以上，加一个 **Shizuku 13.6.0 以上**（[下载](https://github.com/rushiranpise/Shizuku-Next)）——这是唯一的前置，不需要 Root，也不需要解锁 Bootloader。

> 我引导装的是 Shizuku-Next，社区维护的发行版，包名和 API 与官方一致；官方上游是 [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)。

## 上手

1. 装好 Shizuku 并启动，推荐「无线调试」方式。
2. 从 [Releases](https://github.com/vip458/Simlet/releases) 下载 `app-release.apk` 安装。
3. 打开 Simlet，在引导页点「请求授权」，弹窗里选允许。
4. 选卡 → 选目标国家码（比如 `JP`）→ 需要的话再改运营商名 → 点**保存**。
5. 想恢复原样：点**还原**。

| 想要的效果 | 怎么做 |
| --- | --- |
| 让手机以为卡在东京 | 卡 1 → 国家码 `JP` → 保存 |
| 状态栏显示自己的名字 | 运营商名称 → 自定义名称填 `MyCarrier` → 应用 → 保存 |
| 目录里没有的地区 | 国家码 → 更多 → 自定义代码填两位 ISO |
| 两张卡给不同地区 | 分别设置，互不影响 |
| 全部恢复 | 逐张卡点还原 |

不申请任何运行时权限；除了检查更新，不发任何网络请求；设置只存本机。安装包约 1.3 MB。

## 它是怎么工作的

改的是 `system_server` 里的运营商配置（CarrierConfig），不碰卡本身，所以可以完全还原。写入走三级回退，每一级写完都会读回来确认真正生效：

| | 通道 | 适用 |
| --- | --- | --- |
| 一 | `ICarrierConfigLoader.overrideConfig` | Android 8 – 15 |
| 二 | 借 shell 身份调 `CarrierConfigManager.overrideConfig` | Android 16+ |
| 三 | 由 shell 拉起独立进程执行通道二 | 主进程借不到权限时 |

写入一律是会话级（`persist=false`）。Android 16/17 上普通应用请求持久写入，要么被直接拒，要么「收下但不落盘」——读得到值，下次配置重载却整个丢掉，反而更难判断。所以我明确不持久，重启后由开机补写恢复你的设置。

```
app/src/main/java/com/qianxi/simlet/
├── core/     机制层：三级写入通道、shell 权限委托、卡槽读取、写后校验、开机补写
├── data/     国家目录与按国家分组的运营商预设
├── update/   检查更新、下载、拉起系统安装器
├── ui/       Compose 界面：首页 / 引导页 / 设置 / 关于 / 开源许可
└── licenses/ 开源许可清单（tools/gen-licenses.py 生成）
```

## 已知限制

- 部分定制系统（如鸿蒙）在框架层禁掉了相关接口，用不了属正常现象
- 覆盖是会话级的：系统重载配置或重启后会短暂回落到真实运营商，再由开机补写恢复
- 还原是两步——先把该卡的真实国家码写回去，再清空覆盖。因为系统只在「写入新覆盖」时重算派生出来的 `countryIso`，光清空的话应用还会读到旧值
- 状态栏运营商名跟随依赖 `setOperatorBrandOverride`，各家放行情况不一：我的小米 15（Android 17）跟随，红米 Note 14 Pro（Android 16）被系统拒绝——那种情况下国家码和配置覆盖照样生效，只是名字不变
- 个别机型主进程借不到 shell 权限，会自动落到通道三完成，写入多花几秒

## 读不到卡？

首页显示「未检测到可用 SIM 卡」时，先确认卡插好了、SIM 没被 PIN 码锁住——锁着的话系统还没有活跃订阅，任何应用都读不到它。

还不行，看提示下面那行灰色小字，那是读卡通道自己报的原因，比如 `dumpsys isub 没有输出（exit=1 ...）`，或者 `dumpsys isub 有 132 行，但没解析出卡槽映射`。**把那一行连同机型、系统版本发到下面的群里**，比只说「识别不到卡」有用得多：各家 ROM 的打印格式不一样，我没有样本就修不到那一台。

## 更新日志

### v1.0.0 · 2026-10-30

第一个公开版本。能做的事就三件：改国家码、改运营商名、一键还原。

- 双卡分别设置，写入后我会再读回来确认，没生效会直接告诉你
- 兼容 Android 8 – 17；16/17 改了权限委托，我做了三级回退
- 开机自动补写一次，不用你每天点
- 72 个按国家分组的运营商预设，也可以自己填任意名字
- 深色模式三档、中英文随时切
- 应用内检查更新，下载即装

## 反馈

QQ 群 **551772286** · [点击加群](https://qun.qq.com/universal-share/share?ac=1&authKey=izvrQCNarsbPQQG1Y8IsSeSp8%2FIz%2FIdQ0awH3sTSi%2BBSny%2F7axHzl7MEe4RoG9xX&busi_data=eyJncm91cENvZGUiOiI1NTE3NzIyODYiLCJ0b2tlbiI6IklYQ0FFSWs0YkV0TWxqSmpWbXJ4K0c1WE1CZ3ZpODJydUtPYnEwRjJ0U3BQMmxLbFhCSEx1SDdpaEtVMnpPSEUiLCJ1aW4iOiI0NTg2NTU3MzIifQ%3D%3D&data=Xmm0zFg5KfMXUs_Vz37BOSjXxNShffk2e7erhOtHAQPQy0NaCJ8P1wTDQaYjPf-0yQRMgq-sWOWpkVHdEtpTPQ&svctype=4&tempid=h5_group_info)（打不开就在 QQ 里搜群号）。提问麻烦带上机型、系统版本、Shizuku 版本，以及首页那行灰字。

发版通知我发在公众号 **知果核**，安装包一律以本仓库 Releases 为准。

<img src="app/src/main/res/drawable-nodpi/wechat_qrcode.jpg" width="160" alt="知果核公众号二维码">

## 自己编译

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 发布包（R8 混淆 + 资源压缩）
```

没有签名配置时 `assembleRelease` 也会成功，只是产出未签名包。要出可安装的发布包：

```bash
cp keystore.properties.example keystore.properties   # 填你自己的 keystore
./gradlew assembleRelease
```

`keystore.properties` 和 `*.jks` 都在 `.gitignore` 里，口令不进仓库。国内网络若拉不动 Gradle 发行包，把 `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 换成官方的 `https\://services.gradle.org/distributions/gradle-8.9-bin.zip`。

## 贡献

改动请顺手遵守仓库风格：注释用中文、只在「为什么」不显然的地方写、界面文案一律走 `strings.xml`（`values-en` 同步补）。

- 加界面图标：见 [docs/icons-reicon.md](docs/icons-reicon.md)，统一来自 [Reicon](https://reicon.dev) 子集，`python tools/gen-icons.py` 重新生成
- 加依赖后：跑 `python tools/gen-licenses.py` 重生成关于页的许可清单，别手改生成物
- 应用图标与品牌源文件在 `brand/`，改设计请连同 `app/src/main/res/` 下的启动图标一起更新

## 鸣谢

- [Shizuku](https://github.com/RikkaApps/Shizuku) — 免 Root 拿到 shell 能力的全部前提
- [Reicon](https://reicon.dev) — 界面图标（MIT）
- [Ackites Nrfr](https://github.com/Ackites/Nrfr) — 同类机制的先行者。Simlet 是独立实现，不含它的代码，但「这条路在 Android 16/17 上还走得通」最早是在它上面验证的

## 许可

Apache License 2.0 — 详见 [LICENSE](LICENSE)。应用内「设置 → 关于 Simlet → 开源许可」列出全部直接依赖及其条款。
