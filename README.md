# TVBox

TVBox 是一个面向 Android TV / 电视盒子的影视播放应用，使用 Kotlin、Jetpack Compose 和 Media3 ExoPlayer 构建。应用重点适配遥控器操作，支持影视分类、搜索、详情、m3u8 播放、观看历史、电视直播和 OTA 更新。

当前源码版本为 `1.4.1`（`versionCode=10401`）。本版把桌面端已有的详情测速、测速缓存、片头片尾跳过带到电视端，修复进入直播后无法返回首页，补齐桌面端电视直播与平台直播的默认入口，并将 OTA 更新切换到自建的 `https://tvbox.pfac.fun:8443` 渠道；服务端部署方式见 [多平台直播服务部署说明](platform_live_server/DEPLOYMENT.md)。

> 请确保使用的影视与直播接口具备合法授权。本项目仅提供客户端能力，不内置或托管影视内容。

## Windows 桌面版

仓库同时包含独立的 Windows 桌面客户端（`desktop/`，Compose Multiplatform Desktop + libmpv），与电视端共享 `core/` 中的数据源、聚合、历史与播放策略，但不包含语音找片、扫码配置和 APK 更新等电视专属能力。

桌面前端按桌面使用习惯重新设计，而不是复用电视 UI：

- 左侧常驻导航 + 右侧内容区，窗口宽度低于 1440dp 时侧栏自动收成图标栏，低于 1120dp 时收窄内容边距。
- 首页“继续观看”直接读取观看历史并按剧名去重，推荐位与下方网格自动排除已展示内容。
- 片库的“类型 / 年份 / 地区 / 语言”统一为文字筛选，支持折叠、清除附加条件和分页加载；子类在源站不存在时跳过该源，不会退回父类混入整个大类。
- 详情页按窗口宽度自适应，播放线路可展开收起，测速结果统一按延迟毫秒升序排列；选集点击即播。
- 播放器线路菜单不显示序号，片头片尾面板贴右下控制区弹出并支持外部点击、关闭按钮和 Esc；音量限制在 0–100%，滚轮与上下键调整。
- 数据源不再手工绑定：首页可挑一个源站展示内容，点击条目或续播时若原站缺剧会自动换到可用线路。
- 清空历史等批量操作需要二次确认，单条删除提供撤销；通知区区分成功、警告与错误。

构建与打包（需要 JDK 17）：

```powershell
.\gradlew.bat :desktop:desktopTest           # 桌面单测
.\gradlew.bat :desktop:run                   # 直接运行
.\gradlew.bat :desktop:createDistributable   # 生成免安装便携版
```

便携版输出在 `desktop/build/compose/binaries/main/app/XiaoJi/`，整个目录拷到任意位置后运行 `XiaoJi.exe` 即可。若在 `XiaoJi.exe` 同目录放一个空的 `portable.flag`，配置、缓存和日志会写到旁边的 `data/` 目录，整包搬走不会丢数据；没有该文件时仍使用 `%APPDATA%\XiaoJiYingShi`。

版本号由 `desktop/build.gradle.kts` 中的 `desktopVersion` 统一提供，侧栏与设置页“关于”读取同一份生成值。

## 界面预览

![TVBox 首页界面预览](docs/screenshots/home-preview.png)

## 功能特性

- Android TV 适配：支持 `LEANBACK_LAUNCHER`，可在电视桌面启动，同时保留普通 Android 启动入口。
- 遥控器友好：方向键、确认键、返回键、菜单键和数字键均有对应交互。
- 首页影视列表：默认展示豆瓣热播剧集，支持海报、评分、分页加载、焦点高亮；点击热播卡片后才按片名从当前影视数据源查找详情与播放资源。
- 主题切换：设置页支持“默认主题”和“影院主题”；默认主题保留原有顶部快捷入口，影院主题提供左侧图标导航和 Hero 推荐布局。
- 视频接口管理：内置《影视线路地址整理.md》收录的 30 余个资源站，并保留原有扩展线路；设置页可选择首页数据源，也可手机扫码添加 MacCms 自定义接口。
- 搜索与详情：关键词搜索最多三条来源并行，结果增量去重显示；重新进入搜索页时光标位于旧关键词末尾，可用遥控器退格删除。详情页优先显示主来源，后台补齐播放源和选集。
- AI 找片：支持文字、应用内语音识别、快捷推荐词和“换一批”，可在设置页用手机扫码配置大模型、模型名和 API Key。
- 播放器：基于 Media3 ExoPlayer，仅使用其视频解码与画面窗口；TVBox 自行处理遥控器快退/快进、切集、倍速、播放暂停、自动跳下一集和手机播放手势。
- 详情测速：详情页可手动测量当前影片各条播放源，按延迟升序、带宽降序排列；结果按“剧 + 稳定源标识”缓存，重新打开直接回填，设置页可清除缓存或开启“打开详情自动测速”。
- 片头片尾：播放页“跳过”面板可在当前位置记录片头结束、片尾开始，标记按剧共用；播放时片头倒计时 3 秒、片尾倒计时 8 秒，可自动跳过或在设置里关闭。
- 画面比例：点播操作栏“画面”支持自适应、裁剪铺满和拉伸铺满，本机记忆并实时生效；播放页隐藏系统栏，退出后恢复。
- 设备模式：设置提供“电视模式”和“手机模式”，默认电视模式，本机保存并优先于系统识别；电视短剧保持横屏，手机模式保留点播转屏和普通直播手机布局。
- 观看历史：记录影片、封面、播放线路、集数、播放进度和更新时间；首页显示最近六部未看完的“继续观看”，原来源优先续播，备用线路后台补齐。
- 返回定位：首页、搜索和历史页面恢复滚动位置与影片焦点；“继续观看”卡片可用方向上返回顶部导航。
- 电视直播：支持 TVBox 文本与 M3U 直播源，主源不可用时自动尝试备用源；支持分组、左右切台、上下切换线路、数字选台，以及线路异常时自动换线。
- 平台直播：支持斗鱼、虎牙、哔哩哔哩、抖音和快手，统一提供平台、一级分类、二级分类、直播间和播放器流程，并按平台返回结果优先选择最高画质；直播间可在本机收藏，在直播入口的“收藏”卡片中直接打开。
- 播放亮屏：所有播放页在前台保持亮屏，暂停时同样生效；退出播放页或切到后台后恢复原设置。
- 播放中断：切到后台或发生音频焦点中断后暂停，返回后由用户手动恢复播放。
- OTA 更新：从自建 `tvbox.pfac.fun:8443` 渠道检查新版本，启动失败时静默并在设置页显示“更新服务器连接异常”；发现新版本自动询问，系统后台下载 APK，缺少系统下载组件时使用应用备用下载，完成后校验 SHA-256 并打开安装器。
- 内容过滤：过滤伦理、电影解说、演员、新闻资讯等不需要的分类或资源。

## 遥控器快捷键

首页：

| 按键 | 功能 |
| --- | --- |
| 数字 1 | 历史 |
| 数字 2 | 搜索 |
| 数字 3 | AI 推荐 |
| 数字 4 | 电视直播 |
| 数字 5 | 平台直播 |
| 数字 6 | 设置 |
| 方向键 | 移动焦点 |
| 确认键 | 打开当前焦点内容 |
| 下滑到加载更多 | 自动加载下一页 |

播放器：

| 按键 | 功能 |
| --- | --- |
| 确认键 | 展开控制栏，或操作当前聚焦控件 |
| 媒体播放暂停键 | 直接播放或暂停 |
| 方向左 / 右 | 控制栏关闭时每次快退 / 快进 10 秒并支持长按；控制栏打开时导航按钮，聚焦进度条时调整进度 |
| 菜单键 | 切换倍速并显示当前倍速 |
| 数字 1 | 上一集 |
| 数字 3 | 下一集 |
| 返回键 | 依次关闭选择面板、关闭操作栏、返回详情页 |

直播：

| 按键 | 功能 |
| --- | --- |
| 方向左 / 右 | 上一个 / 下一个频道，支持首尾循环 |
| 方向上 / 下 | 切换当前频道的上一条 / 下一条线路 |
| 确认 / 播放暂停 | 显示左侧频道列表 |
| 数字键 | 输入频道号，例如 `1`、`12` |
| 返回键 | 先关闭频道列表，再返回首页 |

## 安装

自 1.4.0 起，电视端 OTA 使用自建 HTTPS 更新渠道，不再依赖 GitHub Release：

- 更新清单：`https://tvbox.pfac.fun:8443/api/update.json`
- APK：由 `publish-update.ps1` 发布到同一域名的 `/api/` 目录
- 服务端只暴露清单和 APK，不支持目录浏览，部署方法见 [`deploy/tvbox-update/README.md`](deploy/tvbox-update/README.md)

通过 ADB 安装本地构建：

```powershell
adb install -r app/build/outputs/apk/release/app-release.apk
```

> **签名迁移提醒**：1.4.x 起使用新的 release 签名，与 1.3.13 及更早的 GitHub 包不一致。已经安装旧版的电视需要先卸载一次，再安装 1.4.1；此后同一签名的版本可以直接 OTA 覆盖升级，本机数据会在覆盖安装时保留（卸载会清除应用数据）。

旧版 1.3.13 仍然只认识 GitHub 的 `update.json`；根目录清单保留为旧版兼容文件，不再作为 1.4.x 的更新入口。

## OTA 更新机制

应用启动后请求：

```text
https://tvbox.pfac.fun:8443/api/update.json
```

清单格式：

```json
{
  "versionCode": 10401,
  "versionName": "1.4.1",
  "apkUrl": "https://tvbox.pfac.fun:8443/api/TVBox-v1.4.1.apk",
  "apkSha256": "e31aff213d2d2d1a96fdcd5fac5b218b25b5ccbc928c558ab863634f7fcd7ce7",
  "apkSize": 5713981,
  "force": false,
  "changelog": [
    "电视端新增详情测速与测速缓存",
    "电视端新增片头片尾跳过与自动跳过",
    "修复电视端进入直播后无法返回首页",
    "补齐桌面端电视直播和平台直播默认入口"
  ]
}
```

行为说明：

- `versionCode` 必须大于当前安装版本才会提示更新；同版本不会重复下载。
- 启动检查失败不会弹出打扰，只在“设置 → 更新”显示“更新服务器连接异常”；连接正常时显示“更新服务器连接正常”。
- 发现新版本时在打开应用后询问是否更新，用户可选择稍后；下载在后台继续，关闭提示不会取消。
- 下载完成后校验 `apkSize` 与 `apkSha256`，通过后打开系统安装器；系统缺少下载组件时自动改用应用内前台服务下载。
- `force` 预留给强制更新，当前普通更新允许稍后处理。
- `update.json` 使用 `Cache-Control: no-store`，APK 与清单只允许从 `/api/` 读取。
- 域名使用 Cloudflare DNS-only 记录、TTL 60 和家宽 8443 端口；`tvbox-ddns` 容器每 5 分钟同步公网 IP。80/443 被运营商封锁，因此地址必须保留 `:8443`。

## 本地构建

环境要求：

- JDK 17
- Android SDK
- 使用项目内 Gradle Wrapper

AI 找片配置：

- APK 默认使用打包时配置的 `TVBOX_AI_API_KEY` 和内置 Agnes 参数。
- 用户可在设置页选择 Agnes、DeepSeek、SiliconFlow 或 Qwen。
- 模型名称和 API Key 不需要用遥控器输入；点击“模型”或“API Key”按钮后，电视会弹出二维码，手机扫码填写后自动同步到电视。
- 设置页只有手机提交了 API Key 后才会覆盖 APK 内置 AI 配置；未填写时继续使用默认配置。

打包时的默认 API Key 可放在本地配置中：

```properties
TVBOX_AI_API_KEY=你的测试密钥
```

`TVBOX_AI_API_KEY` 可以放在 `local.properties`、Gradle 属性或环境变量中。`local.properties` 已被 `.gitignore` 忽略，不会上传到仓库。

视频接口配置：

- 内置视频接口保留在 APK 中，不会被用户配置覆盖。
- 设置页“视频接口”可选择当前首页、搜索和 AI 找片使用的数据源。
- 点击“添加接口”后，电视弹出二维码；手机扫码填写接口名称和 MacCms 地址，确认后自动同步到电视。
- MacCms 地址示例：`https://example.com/api.php/provide/vod`

平台直播服务配置：

- 服务端运行在电脑或服务器上，TVBox 通过 `TVBOX_PLATFORM_LIVE_SERVICE_URL` 访问统一接口。
- 本次 v1.3.3 Debug 构建使用的服务地址为 `http://20.205.10.127:8868`。
- Windows PowerShell 临时构建配置：

```powershell
$env:TVBOX_PLATFORM_LIVE_SERVICE_URL="http://20.205.10.127:8868"
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

- 该变量只在构建时注入 APK，不需要写入服务器代码；服务器 Cookie 只配置在服务器环境变量中。
- 如果服务器地址变化，必须使用新地址重新构建 APK；不要填写 `localhost` 或 `127.0.0.1`。

运行测试：

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

构建 debug APK：

```powershell
.\gradlew.bat assembleDebug --console=plain
```

构建 release APK：

```powershell
.\gradlew.bat assembleRelease --console=plain
```

release APK 输出位置：

```text
app\build\outputs\apk\release\app-release.apk
```

## Release 签名

release 签名信息从 `local.properties`、Gradle 属性或环境变量读取，密钥文件不会提交到仓库。

需要配置：

```properties
TVBOX_RELEASE_STORE_FILE=signing/xxx.jks
TVBOX_RELEASE_STORE_PASSWORD=***
TVBOX_RELEASE_KEY_ALIAS=***
TVBOX_RELEASE_KEY_PASSWORD=***
```

签名校验：

```powershell
apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk
```

## 发布新版本流程

1. 修改 `app/build.gradle.kts` 的 `versionCode` / `versionName`、`desktop/build.gradle.kts` 的 `desktopVersion`，并更新 `CHANGELOG.md`、本 README 的版本说明。
2. 运行核心、桌面和 TV 单测，构建签名 APK：

```powershell
.\gradlew.bat :core:test :desktop:desktopTest :app:testDebugUnitTest :app:assembleRelease --console=plain
```

3. 发布到自建更新目录（默认 `D:\Portable\TvBoxUpdate`）：

```powershell
pwsh -NoProfile -File deploy/tvbox-update/publish-update.ps1 `
  -Apk app/build/outputs/apk/release/app-release.apk `
  -VersionCode 10401 `
  -VersionName 1.4.1 `
  -Changelog @("电视端新增详情测速与测速缓存", "电视端新增片头片尾跳过")
```

脚本会把 APK 复制为 `api/TVBox-v<版本号>.apk`，计算 SHA-256 和大小，并以 UTF-8 无 BOM 原子写入 `api/update.json`。已安装同签名 1.4.x 的电视会在下次启动时收到更新提示。

4. 提交并推送代码：

```powershell
git add app core desktop deploy CHANGELOG.md README.md
git commit -m "Release v1.4.1"
git push origin feature/xiaoji-experience
```

> 1.4.x 的正式分发通道是 `tvbox.pfac.fun:8443`，不再需要创建 GitHub Release。若需要把 APK 上传到 GitHub Release 或其他公开渠道，必须先单独确认；`git push` 不会上传 APK。

## 项目结构

```text
app/src/main/java/com/tvbox/app
├── data        # Android 端存储适配与平台能力
├── domain      # Android 端专属领域逻辑
├── ui          # Compose 页面、播放器、直播页
└── MainActivity.kt

core/src/main/kotlin/com/tvbox/app
├── data        # 影视/直播接口、仓库、历史、测速、OTA 清单
└── domain      # 领域模型、聚合、播放策略（电视与桌面共享）

desktop/src/desktopMain/kotlin/com/tvbox/desktop
├── data        # 桌面存储、图片加载、路径
├── state       # 页面状态与动作
├── ui          # 壳层、首页/片库/搜索/追剧/历史/直播/设置/详情/播放
└── Main.kt     # 窗口创建、DPI、最大化与全屏
```

## 技术栈

- Kotlin
- Jetpack Compose
- Compose for TV 基础能力
- Media3 ExoPlayer
- Retrofit / OkHttp
- kotlinx.serialization
- Coil

## 版本记录

详细变更见 [CHANGELOG.md](CHANGELOG.md)。

## 🔗 友情链接 / Friends

- [LINUX DO](https://linux.do/) — 新的理想型社区 / A new ideal community

