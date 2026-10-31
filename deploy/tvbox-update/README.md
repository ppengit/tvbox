# 电视端热更新服务

只托管两样东西：

- `/api/update.json`：当前最新版本、APK 地址、SHA-256、大小和更新说明；
- `/api/TVBox-v<版本>.apk`：对应版本的安装包。

客户端启动时请求 `https://tvbox.pfac.fun:8443/api/update.json`。没有新版本、网络失败或
服务器不可达时不会弹窗；设置页“更新”区域会显示“更新服务器连接异常”。

## 目录

| 文件 | 用途 |
|---|---|
| `compose.yaml` | 静态更新容器 + Cloudflare DDNS 容器 |
| `nginx.conf` | 只读静态文件规则，`update.json` 禁止缓存 |
| `ddns/ddns.py` | 每 5 分钟把 `tvbox.pfac.fun` A 记录同步到当前公网 IP |
| `Caddyfile.snippet` | 加到 DuanJu 的 Caddy 配置里，接管 `tvbox.pfac.fun:8443` |
| `publish-update.ps1` | 计算哈希/大小并原子生成 `update.json` |

## 首次部署

前提：DuanJu 的 `parrot-tv_default` 网络、`parrot-tv-caddy`、`parrot-tv-dnsproxy`
已经在运行，Cloudflare Token 位于 `D:/Portable/secrets/cf-pfac-fun.env`。

1. 在 Cloudflare 创建 `tvbox.pfac.fun` 的 A 记录（灰云 / DNS only，TTL 60），
   初始内容为当前家庭公网 IP。DNS 记录由 DDNS 容器后续维护。
2. 把 `Caddyfile.snippet` 的站点块追加到 `D:\Solution\DuanJu\deploy\caddy\Caddyfile`，
   然后执行：
   ```powershell
   docker exec parrot-tv-caddy caddy validate --config /etc/caddy/Caddyfile
   docker exec parrot-tv-caddy caddy reload --config /etc/caddy/Caddyfile
   ```
3. 启动本目录的服务：
   ```powershell
   docker compose -f D:\Solution\tvbox\deploy\tvbox-update\compose.yaml up -d --build
   ```
4. 验证：
   ```powershell
   curl.exe -fsS https://tvbox.pfac.fun:8443/api/update.json
   docker logs --tail 20 tvbox-ddns
   ```
5. 发布 APK：
   ```powershell
   .\publish-update.ps1 -Apk ..\app\build\outputs\apk\release\app-release.apk `
     -VersionCode 10401 -VersionName 1.4.1 -Changelog @("...")
   ```

> DNS 使用灰云（DNS only）+ TTL 60，客户端固定访问 `https://tvbox.pfac.fun:8443`。
> 家宽 80/443 入站被封，Cloudflare 橙云回源到 8443 需要 Origin Rule 权限；
> 当前 Token 只授予 DNS 编辑，因此沿用 DuanJu 的 8443 直连模式。

## 安全

- `CF_API_TOKEN` 只通过 `env_file` 和只读 secret 挂载进入容器，不写入镜像、仓库或日志。
- 静态容器只读挂载更新目录，不提供目录浏览，也不接受上传。
- `update.json` 不缓存；APK 文件名带版本号，允许 Cloudflare 缓存 1 小时。
