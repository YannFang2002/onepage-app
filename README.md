# OnePage App

Android 手机端伴侣应用：连接 OnePage 阅读器，改设置、管文件、装字体。

- **单平台**：Android 8.0+（minSdk 26），Kotlin + Jetpack Compose，Material 3
- **零固件改动**：只调用设备已有的 HTTP / WebSocket 接口（与设备自带网页版同一套）
- **连接方式**：局域网（同路由器）/ 设备热点（`CrossPoint-Reader`，默认 `192.168.4.1`）/ UDP 自动发现（端口 8134）

## 功能（v1）

| 模块 | 能力 |
|---|---|
| 连接 | **扫码连接**（Wi-Fi 码自动加入热点 / 网址码直接连）、UDP 广播自动发现、手动输入 IP、保存多台设备、重命名、断开 |
| 设备 | 版本 / 型号 / IP / 模式 / 信号 / 空闲内存 / 运行时长，每 6 秒刷新 |
| 设置 | 设备返回的 schema 驱动，共 4 大类 50 项：开关、枚举、滑杆、文本；改动即写回，失败回滚 |
| 文件 | 浏览、上传（WebSocket 分片 + 进度）、下载、新建目录、重命名、移动、删除 |
| 字体 | 字体家族列表（字号 / 文件数 / 体积）、上传 `.cpfont`、删除家族 |
| OPDS | 订阅源增删 |
| Wi-Fi | 设备保存的 SSID 增删（密码只写不读） |

不做：BLE 直连（固件无 GATT 服务）、手机端刷机、阅读进度同步。

## 构建

```bash
# 需要 JDK 17 + Android SDK 34
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 正式包（keystore.properties 已在本地生成）
./gradlew testDebugUnitTest  # 协议解析单元测试（13 个用例，无需设备）
./gradlew updateDebugScreenshotTest   # 生成 Preview 截图，逐屏视觉验收
```

产物：`app/build/outputs/apk/…`，当前发布用的包已复制到
`D:\PROJECT\02.GoodCode\OnePage\outputs\OnePage-1.0.0.apk`（签名版）与 `…-debug.apk`。

`local.properties` 指向本机 SDK；`keystore.properties` + `onepage-release.jks` 用于签名，均已在 `.gitignore` 中。

## 测试

- `ParsersTest`（JVM，13 例）：用固件真实 JSON 形状验证 status / settings（四种类型）/ files / fonts / opds / wifi / UDP 发现应答的解析，含缺字段、布尔值、字符串数字、脏数据等边界。
- `DeviceApiTest`（JVM + MockWebServer，11 例）：验证发出去的真实 HTTP 请求——路径、query 编码、表单字段名、JSON body、Content-Type、multipart 字段、错误码映射（含设备纯文本 / JSON 两种错误格式）。
- `updateDebugScreenshotTest`：11 张 Preview 截图（含深色模式），作为 UI 回归基线。

## 扫码连接

设备「文件传输」页在 AP 模式下会画两个二维码（`CrossPointWebServerActivity::renderServerRunning`）：

| 码 | 内容 | App 行为 |
|---|---|---|
| Wi-Fi 码 | `WIFI:S:CrossPoint-Reader;;`（固件为开放网络，省略 `T:`/`P:`） | 系统弹窗确认后加入热点，取网关地址（回落 `192.168.4.1`）并连接 |
| 网址码 | `http://192.168.4.1/`（C61 无 mDNS，直接编 IP） | 直接连该地址 |

解码走 **ZXing + CameraX 本地识别**（不用 ML Kit，避免依赖 Google Play 服务，国产机型也能用）；图像不出本机。
Android 10+ 用 `WifiNetworkSpecifier` 加入热点——系统会弹一次「连接到此网络」确认，这是第三方 App 唯一的合规路径，无法完全静默。

## 无硬件开发

```bash
node tools/mock-device/server.mjs --port 8080
```

模拟器里手动连接 `10.0.2.2:8080`；局域网内手机连 `http://<电脑IP>:8080`。
mock 服务复刻了固件的全部 JSON 形状（含 25 个设置项、文件树、字体表、OPDS、Wi-Fi）。

## 设备端接口（对照 `CrossPointWebServer.cpp`）

| 用途 | 请求 |
|---|---|
| 状态 | `GET /api/status` |
| 读设置 | `GET /api/settings` → `[{key,name,category,type,value,options,min,max,step}]` |
| 写设置 | `POST /api/settings` `{key: value}`（局部更新，设备落盘） |
| 列目录 | `GET /api/files?path=/` |
| 上传（大） | WS `:81` `START:<name>:<size>:<path>` → `READY` → 二进制帧 → `PROGRESS:x:y` → `DONE` |
| 上传（小） | `POST /upload?path=/`（multipart，字段 `file`） |
| 下载 | `GET /download?path=...`（无 Range，不支持续传） |
| 目录操作 | `POST /mkdir {name,path}`、`/rename {path,name}`、`/move {path,dest}`、`/delete {path}` |
| 字体 | `GET /api/fonts`、`POST /api/fonts/upload`（`family`+`file`）、`POST /api/fonts/delete` `{family}` |
| OPDS | `GET/POST /api/opds`、`POST /api/opds/delete` `{index}` |
| Wi-Fi | `GET/POST /api/wifi`、`POST /api/wifi/delete` `{index}` |
| 发现 | UDP `8134` 发 `hello` → `crosspoint (on <host>);<wsPort>` |

## 结构

```
app/src/main/java/com/onepage/companion/
├─ core/model/     数据模型（status / settings / files / fonts / opds / wifi）
├─ core/net/       DeviceApi（OkHttp）、WsUploader（上传状态机）、Discovery（UDP）
├─ core/data/      DeviceStore（已保存设备）
├─ ui/theme/       调色板、字体阶梯、形状
├─ ui/components/  卡片、按钮、输入框、弹层、进度条
├─ ui/screens/     connect / home / settings / files / more(fonts|opds|wifi|info)
└─ ui/MainShell    底部导航 + 页面切换
```

## 已知边界

- 设备接口无鉴权，同一网络内任何客户端都能改设置；App 不做假的“安全”提示。
- 设备退出网页服务会重启，连接会断，App 有 6 秒轮询与错误态提示。
- 下载不支持断点续传，大文件失败需重下。
- 设置项名称由设备按自身语言返回，App 直接展示。
