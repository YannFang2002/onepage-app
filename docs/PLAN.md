# OnePage 手机 App — 方案 v0.2（已实施）

> 目标：手机端（Android）直接管理 OnePage 阅读器——连接、改参数、管理文件、装字体。
> 代码位置：本仓库根目录（Kotlin + Compose 工程，可直接 `gradlew assembleRelease`）
> 固件参照：OnePage 阅读器固件（ESP32-C61，firmware 0.1.1）
> 交付物：签名的 release APK，输出到 `app/build/outputs/apk/release/`

**已确认的边界（2026-10-20）**：只做 Android；UI 要现代化；V1 不改任何设备端代码；APK 落到 `outputs`。

---

## 1. 结论先行

1. **App 不需要发明协议**。固件的 Web 服务器（`src/network/CrossPointWebServer.cpp`，端口 80）已经把"设置下发 / 文件管理 / 字体管理 / OPDS / Wi-Fi 凭据"全部做成了 JSON API，且 **设置接口是 schema 驱动的**——`GET /api/settings` 返回每个字段的类型、取值范围、选项文案，App 可以写**一个通用表单渲染器**覆盖全部 50 个设置项，固件以后加设置项 App 不用改。
2. **v1 只做局域网（Wi-Fi）**。BLE 目前只用于"外接翻页器"（`src/bluetooth/BleHidHost.cpp`，HID Host 方向），**没有 GATT server**，手机连不上；且 BLE 吞吐（约 20–80 KB/s）传 EPUB 不现实。BLE 留作 v1.5 的"配网/状态通道"，需要先改固件。
3. **技术栈：Android 原生 Kotlin + Jetpack Compose**（单平台）。本机已有 JDK 17、SDK 34、AGP 8.5.2 与 Compose 缓存，零额外工具链成本；Flutter 需再装 ~1 GB SDK，且 Android-only 场景没有收益。见 §4。
4. **v1 尽量零固件改动**。现有 API 已够用；固件侧只建议 3 个"锦上添花"改动（§9），不阻塞 App 开发。

---

## 2. 现状盘点

### 2.1 固件已有的能力（这是 App 的功能清单来源）

`src/network/CrossPointWebServer.cpp` 注册的路由：

| 能力 | 路由 |
|---|---|
| 设备状态 | `GET /api/status` → `{version, ip, mode, rssi, freeHeap, uptime, device}` |
| 设置（读） | `GET /api/settings` → `[{key, name, category, type, value, options/min/max/step}]` |
| 设置（写） | `POST /api/settings`，body 为局部 JSON `{"key": value}`，写后立即 `SETTINGS.saveToFile()` 落盘 |
| 文件列表 | `GET /api/files?path=/` → `[{name, size, isDirectory, isEpub}]` |
| 文件下载 | `GET /download?path=/a.epub` |
| 文件上传（小） | `POST /upload?path=/`，multipart，字段名 `file` |
| 文件上传（大，推荐） | WebSocket `:81`，`START:<name>:<size>:<path>` → `READY` → 二进制帧 → `PROGRESS:x:y` → `DONE` |
| 目录/改名/移动/删除 | `POST /mkdir {name,path}`、`/rename {path,name}`、`/move {path,dest}`、`/delete {path}` |
| 字体 | `GET /api/fonts`、`POST /api/fonts/upload`（multipart：`family`+`file`）、`POST /api/fonts/delete` |
| OPDS 源 | `GET/POST /api/opds`、`POST /api/opds/delete` |
| Wi-Fi 凭据 | `GET/POST /api/wifi`、`POST /api/wifi/delete`（密码只回 `hasPassword`，不回明文） |
| 设备自带网页 | `/`、`/settings`、`/files`、`/fonts`、`/js/jszip.min.js` |
| WebDAV | `WebDAVHandler` 挂在根路径，设备同时是个 WebDAV 服务端 |

设置项共 **50 个**，分 4 大类（DISPLAY / READER / CONTROLS / SYSTEM）外加若干字符串型（KOReader 账号等）；类型只有 `toggle` / `enum` / `value(min,max,step)` / `string` 四种——App 端一个 `SettingField` 抽象即可全覆盖。

### 2.2 连接方式（"文件传输功能"里的那几种）

`src/activities/network/CrossPointWebServerActivity.cpp`：

| 模式 | 实现 | 手机侧影响 |
|---|---|---|
| **热点（AP）** | SSID `CrossPoint-Reader`，**开放网络无密码**，channel 1，最多 4 连接，`192.168.4.1`（ESP 默认），带 DNS 劫持做 captive portal | 手机需"加入该 Wi-Fi 并绑定 socket"；iOS 需 Hotspot Configuration 权限 |
| **局域网（STA）** | 连接家里的路由器，`WiFi.localIP()` | 最顺路径，手机与设备同一网段即可 |
| **mDNS** | `crosspoint.local`（AP 模式注册） | ⚠️ OnePage C61 构建里 **mDNS 被 stub 掉**（`ONEPAGE_C61` 分支，见 `CrossPointWebServerActivity.cpp:4-12`），不可依赖 |
| **UDP 发现** | 端口 `8134`，收到 `"hello"` 回 `crosspoint (on <hostname>);81` | ✅ 跨平台可用，作为自动发现的**唯一**可靠手段 |

⚠️ 设备退出 Web 服务时会 `silentRestart()`（重启），连接必然断开——App 需要重连状态机。

### 2.3 `onepage-reader-web` 的真实能力（用户点名参照的仓库）

| 页面 | 实际是什么 | 与 App 的关系 |
|---|---|---|
| `/` 首页 + `/log` 构建日志 | 静态 Astro 站 | 不做 |
| 模拟器 | CrossPoint 固件编译成 WASM，在浏览器里跑 | 不做（PC 端体验） |
| `/fonts` **cpfont 字体工具** | 纯前端 TTF/OTF → `.cpfont`（FreeType-WASM 光栅化 + HarfBuzz 子集化 + JSZip 打包） | v1 只做"已生成 `.cpfont` 的上传/删除/列表"；转换功能用 **WebView 内嵌该工具**的方式零成本复用（v2） |
| `/firmware` **刷机** | esptool-js over Web Serial（USB 串口） | 手机端走不通（iOS 无 USB 主机；Android 需 OTG + 串口专有实现）→ 走固件自带 OTA |

**结论**：该仓库里没有"连设备"的代码。用户要的"设置参数 / 管理文件"，能力其实在**设备自带的 Web UI**（`/settings`、`/files`）。所以 v1 范围 = 把设备 Web UI 的能力原生重做，而不是照搬 web 站。

---

## 3. 范围

### v1 必做（对齐设备现有能力）

1. **连接**：**扫码连接**（扫设备 Wi-Fi 码自动加入热点、扫网址码直接连）+ UDP 自动发现 + 手动 IP + 保存多台设备
2. **状态**：版本 / IP / 模式（AP/STA）/ 信号 / 空闲堆 / 运行时长
3. **设置**：4 大类共 50 项，schema 驱动表单，改动即时写回，失败回滚 + 提示"已落盘"
4. **文件**：目录浏览、上传（WS 分片 + 进度/速度）、下载、新建目录、改名、移动、删除、EPUB 标记
5. **字体**：已装字体家族/字号列表、上传 `.cpfont`、删除
6. **OPDS 源**：增删改查
7. **Wi-Fi 凭据**：设备保存的 SSID 列表增删（密码只显示"已设置"）
8. 中英双语、深色模式、全程可用的错误态与空态

### 扫码（v1.1 追加）

设备 `CrossPointWebServerActivity::renderServerRunning` 在 AP 模式画两个码：

- `WIFI:S:CrossPoint-Reader;;`（开放网络，固件省略 `T:`/`P:`）→ 解析后走 `WifiNetworkSpecifier` 加入，取 LinkProperties 网关（回落 `192.168.4.1`）再连 `/api/status`
- `http://192.168.4.1/`（C61 无 mDNS）→ 直接按 host/port 连接

扫码实现：CameraX `ImageAnalysis` + ZXing 本地解码（不引 ML Kit / Play Services）；解析器 `QrPayloadParser` 支持标准 `WIFI:T:WPA;S:…;P:…;;`、转义字符、URL、裸 IP，脏码一律拒绝而不是猜。**限制**：Android 10+ 加入热点必然弹一次系统确认框，这是第三方 App 唯一合规路径，无法静默。

### v1 不做（明确写死，避免范围蔓延）

- BLE 直连（固件无 GATT server，需先改固件）
- 手机端固件刷机 / USB 串口
- 阅读进度、书签、书籍内容预览（设备端也没有对应 API）
- 云端账号、多设备同步、远程穿透（公网）
- 字体格式转换（v2 用 WebView 内嵌现成工具）

---

## 4. 技术选型（已定）

| 方案 | 优势 | 劣势 | 判断 |
|---|---|---|---|
| **Kotlin + Jetpack Compose**（采用） | 本机 SDK/AGP/Kotlin/Compose 全部就绪；UI 控制力最强；APK ~8–16 MB；OkHttp 原生支持 HTTP/WS | 只有 Android | ✅ 采用 |
| Flutter | 双端单代码库 | 本机未安装（~1 GB 工具链）；Android-only 无收益 | ❌ 本轮不采用 |
| React Native / Capacitor | 复用 Web 经验 | BLE/热点仍需原生模块，套壳无意义 | ❌ |
| Kotlin View 体系（XML） | 成熟 | 复杂动态表单更繁琐，动效弱 | ❌ |

**实际技术栈**：Kotlin 1.9.24 / AGP 8.5.2 / Compose BOM 2024.06 / Material 3、OkHttp 4.12（HTTP + WebSocket）、Compose 预览截图测试（`updateDebugScreenshotTest`）用于无设备视觉验收。
**平台**：Android 8.0+（minSdk 26），targetSdk 34。iOS 不在本轮范围。

---

## 5. 架构

```
┌──────────────── App (Flutter) ────────────────┐
│ UI 层      connect / home / settings / files  │
│            fonts / opds / wifi / about        │
│ 状态层      Riverpod providers（连接、任务）    │
│ 仓储层      DeviceRepo  SettingsRepo          │
│            FilesRepo   FontsRepo  OpdsRepo    │
│ 传输层      DeviceClient (dio + 拦截器)        │
│            WsUploader（START/PROGRESS/DONE）   │
│            Discovery（UDP 8134 / mDNS 兜底）   │
│ 模型层      DeviceStatus SettingItem FileEntry │
│            FontFamily OpdsServer WifiNetwork   │
│ 原生通道    HotspotJoiner（Android/iOS）       │
└───────────────────────────────────────────────┘
                    │ HTTP :80  WS :81  UDP :8134
┌──────────────── 设备（ESP32-C61）─────────────┐
│ CrossPointWebServer + WebDAV + SD 卡          │
└───────────────────────────────────────────────┘
```

```
onepage-app/
├─ docs/PLAN.md                 # 本文档
├─ tools/mock-device/           # Node 模拟设备（无硬件开发/CI 用）
│   ├─ server.mjs               # 复刻全部 /api/* 的 JSON 形状
│   └─ fixtures/                # 50 项设置快照、文件树、字体表
├─ app/                         # Flutter 工程
│   ├─ lib/
│   │   ├─ main.dart
│   │   ├─ app/{router.dart,theme.dart,shell.dart}
│   │   ├─ core/
│   │   │   ├─ net/{device_client.dart,ws_uploader.dart,discovery.dart,endpoints.dart}
│   │   │   ├─ models/{device_status.dart,setting_item.dart,file_entry.dart,
│   │   │   │          font_family.dart,opds_server.dart,wifi_network.dart}
│   │   │   └─ repo/{device_repo.dart,settings_repo.dart,files_repo.dart,
│   │   │            fonts_repo.dart,opds_repo.dart,wifi_repo.dart}
│   │   ├─ features/…           # 每页一个目录：view + controller
│   │   └─ l10n/{app_en.arb,app_zh.arb}
│   └─ test/                    # 单元 + Widget 测试（跑 mock fixture）
└─ README.md
```

**关键设计**：

- **`SettingItem` 单一模型**：`{key, nameZh/nameEn?, category, type, value, options[], min, max, step}` → `SettingField` 工厂渲染成 Switch / SegmentedButton / Slider / TextField。字段 key 稳定，App 端维护 `key → 本地文案` 覆盖表，缺省回退设备返回的 `name`（设备语言）。
- **写回策略**：面板内改动 → 防抖 300ms → `POST /api/settings` 只发变更键 → 成功则更新本地缓存，失败回滚并提示。设置项落盘在设备侧完成，App 不做本地持久化（避免双份真相）。
- **上传队列**：单并发（设备只有 1 个 WS 上传槽，`ERROR:Upload already in progress`），4–8 KB 分片，60 KB 或 200ms 报进度，断线指数退避重连（重连后必须重新 `START`，设备端不续传）。
- **发现**：`255.255.255.255:8134` 发 `hello`，解析 `crosspoint (on <host>);<wsPort>` 得到 host + WS 端口；同网段多设备时按 IP+hostname 去重并允许用户命名。AP 模式额外把 `192.168.4.1` 作为兜底候选。
- **连接状态机**：`disconnected → discovering → connecting → ready → degraded(超时) → disconnected`，任何请求 3s 超时即转 degraded 并后台重探。

---

## 6. 协议契约（v1 冻结）

| # | 场景 | 请求 | 关键约束 |
|---|---|---|---|
| 1 | 发现 | UDP `:8134` 发 `hello` | 5s 无应答视为无设备；广播在部分安卓 ROM 上需 `CHANGE_WIFI_MULTICAST_STATE` 权限 |
| 2 | 状态 | `GET /api/status` | 轮询间隔 ≥5s（设备主循环还要渲染 e-ink） |
| 3 | 读设置 | `GET /api/settings` | 响应是**流式**拼出来的数组，客户端按完整 JSON 解析即可 |
| 4 | 写设置 | `POST /api/settings` `{key:value}` | 只发变更键；`enum` 发下标；`value` 必须落在 `min..max`；返回纯文本 `Applied N setting(s)` |
| 5 | 列目录 | `GET /api/files?path=/x` | 隐藏 `.` 开头项（受 `showHiddenFiles` 影响） |
| 6 | 上传 | WS `START:name:size:path` → `READY` → bin → `PROGRESS:r:t` → `DONE` | 单并发；重名文件设备端直接覆盖；完成后设备自动清 EPUB 缓存 |
| 7 | 小文件上传 | `POST /upload?path=/`，multipart `file` | 仅用于 <1 MB（如 `.cpfont`），大文件一律走 WS |
| 8 | 下载 | `GET /download?path=…` | **无 Range/断点续传**，断了只能重下 |
| 9 | 目录操作 | `/mkdir` `/rename` `/move` `/delete` | 全部 `application/x-www-form-urlencoded` 或 multipart；参数名见 §2.1 |
| 10 | 字体 | `GET /api/fonts`、`POST /api/fonts/upload` | 上传需 `family` 字段，文件名须 `^[A-Za-z0-9_-]+\.cpfont$`，最多 `maxFamilies` |
| 11 | OPDS/Wi-Fi | `/api/opds*`、`/api/wifi*` | 密码只写不读；更新时省略 `password` 字段表示"保持不变" |

**统一错误处理**：设备 400 时 body 是纯文本原因（如 `Missing path`、`Invalid JSON`）；App 直接展示原文 + 中文兜底。

---

## 7. 里程碑

| 阶段 | 内容 | 状态 |
|---|---|---|
| M0 | 协议冻结 + `tools/mock-device` 复刻全部接口 | ✅ 完成 |
| M1 | 工程骨架、设计系统、发现/手输 IP/多设备、设备状态页 | ✅ 完成 |
| M2 | 设置页（schema 驱动表单 + 分类 + 即时保存 + 失败回滚） | ✅ 完成 |
| M3 | 文件管理（浏览/WS 上传队列/下载/新建/改名/移动/删除） | ✅ 完成 |
| M4 | 字体 / OPDS / Wi-Fi 三页 | ✅ 完成 |
| M5 | 深色模式、空态/错误态、断线重连、Preview 截图验收 | ✅ 完成 |
| M6 | 签名打包、APK 输出到 `outputs`、README | ✅ 完成 |
| M7 | v1.0.2 检查更新：Gitee 镜像优先 / GitHub 兜底，进应用即检查 | ✅ 完成 |
| M8 | v1.0.3 未连接可看关于/更新、断开不再崩溃、热点加入失败可诊断可续连 | ✅ 完成 |

**未做（明确留到 v2）**：热点一键加入的原生流程（当前引导用户到系统 Wi-Fi 设置）、断点续传、阅读进度、BLE。
**验收方式**：本机无可用模拟器（缺硬件加速驱动），UI 通过 Compose Preview 截图测试（`gradlew updateDebugScreenshotTest`）逐屏渲染检查；功能路径对照固件源码静态核对 + `tools/mock-device` 可随时联调。

---

## 8. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| **热点加入的平台差异** | Android 需 `WifiNetworkSpecifier` 绑定 socket（否则请求走蜂窝网）；iOS 需 `NEHotspotConfiguration` 权限，且用户拒绝后无替代方案 | v1 以**局域网优先**；热点做成"引导页 + 深链到系统 Wi-Fi 设置"兜底；原生通道抽象成 `HotspotJoiner` 接口 |
| **mDNS 在 C61 上是空实现** | 不能靠 `crosspoint.local` | 只用 UDP 发现 + 手动 IP；文档写清 |
| **设备 API 无任何鉴权** | 同网段任何人可改设置/删文件 | App 内不做"安全"假象；预留 `Authorization` 头注入点，待固件加 token 后一键启用 |
| **单线程 WebServer** | 并发请求会互相排队；上传时设备卡顿 | App 全局串行化请求（一个队列），上传期间只做 WS |
| **设备退出即重启** | 连接强制断开、偶发误判 | 状态机 + 指数退避重连；错误文案区分"设备已关闭网页服务" |
| **下载无断点续传** | 大文件失败重来 | 提示剩余空间、分文件类型建议用 WS 上传；下载失败给"重试"而非"续传" |
| **AP 开放网络 + 4 连接上限** | 多人抢连 | UI 提示"同一时间仅一台手机操作"；不做后台常连 |
| **BLE 期待落空** | 用户以为能蓝牙传书 | 明确标注 v1 仅 Wi-Fi；v1.5 若做 BLE，只做配网/状态（§9） |

---

## 9. 建议的固件侧改动（均不阻塞 v1）

1. **`/api/status` 增加 `mac` / `hostname`**：多设备同网段时用于稳定去重（现在只有 IP）。
2. **`GET/POST /api/settings/export|import`**：`JsonSettingsIO` 已经有完整读写实现，暴露成两个路由就能让 App 做"配置备份/恢复/一键换机"。
3. **HTTP Token 鉴权**：可在 `begin()` 里校验一个 header，token 由设备屏幕显示（二维码已有 `QrUtils`）。
4. **BLE GATT 配网服务**（v1.5）：`{SSID,PSK}` 写入 + 状态特征值；仅在 `BleHidHost` 空闲时启动，注意 380 KB RAM 与 C61 的 NimBLE 组件裁剪。
5. **SSE/WebSocket 事件流**（可选）：`/api/events` 推送上传完成、电量、设置变化，替代轮询。

---

## 10. 待确认

1. **范围口径**：本文按"设备自带 Web UI 的能力"定义 v1（设置/文件/字体/OPDS/Wi-Fi）。若你指的是 `onepage-reader-web` 站上的"字体转换 + 刷机"，那 v1 会变成另外两件事，需要重排优先级。
2. **平台**：Android 先发、iOS 后补，是否可接受？
3. **技术栈**：Flutter 是否有异议（团队是否更熟 React/TS）？
4. **固件改动窗口**：§9 的 1–3 项能否排进固件版本？能的话 App 可以做"配置备份/鉴权"，否则 v1 不做。
5. **设备命名**：App 里显示 "OnePage" 还是 "CrossPoint"（当前固件 AP SSID 与 `/api/status` 里仍是 CrossPoint 字样）。

---

## 11. v1.0.2 追加：检查更新（已实施）

- **数据源**：Gitee `Yann_Fang/onepage-app` 优先，GitHub `YannFang2002/onepage-app` 兜底；两者都读「latest release」，不带 token。Gitee 仓库为手动同步的镜像。
- **时机**：`UpdateViewModel.autoCheck()` 在 `AppRoot` 首次组合时触发（每次进应用一次）；命中新版本先弹确认框，「更多 → 软件更新」可重查。
- **判定**：`tag_name` 去掉 `v` / `release-` / `-beta` 装饰后，按数字段与 `BuildConfig.VERSION_NAME` 比较；不依赖 `versionCode`，也不用 release 说明做逻辑判断。
- **下载**：只把 APK 的 `browser_download_url` 交给系统浏览器；不申请 `REQUEST_INSTALL_PACKAGES`，不静默安装，不为「省一步」牺牲合规与可追溯性。
- **失败面**：两个源都失败时合并成一行中文错误，只显示在更新页；绝不打断「连接设备」主流程。
