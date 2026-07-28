# Smart Bookstore · 便携 Nginx（Windows）

本目录是智慧书城前端的 **本地演示包**：内置 Nginx Windows 版 + 已构建的静态页面，并把 `/api` 反代到本机后端。

## 快速使用

1. 先启动后端（`8081`，`spring.profiles.active=local`）
2. 双击 `start.bat`（推荐），或在本目录执行 `nginx.exe`
3. 浏览器打开：<http://localhost:8088>
4. 停止：双击 `stop.bat`

## 目录说明

| 路径 | 说明 |
| --- | --- |
| `conf/nginx.conf` | 监听 `8088`，`root html`，`/api/` → `127.0.0.1:8081` |
| `html/` | 前端构建产物（SPA） |
| `start.bat` / `stop.bat` | 启动 / 停止 |
| `update-frontend.bat` | 在上级前端工程 `npm run build` 后同步到 `html/` 并 reload |
| `logs/` | 运行日志（勿提交敏感内容） |

## 注意

- **必须在本目录启动** `nginx.exe`，否则找不到相对路径下的 `conf/`、`html/`
- 后端未启动时，页面可打开，但接口会 502
- QQ 登录 / 签到回调请把后端配置改为 `http://localhost:8088/...`（见仓库 [docs/前端部署指南.md](../docs/前端部署指南.md)）

更完整的部署与生产 Nginx 说明见仓库文档：[前端部署指南](../docs/前端部署指南.md)。
