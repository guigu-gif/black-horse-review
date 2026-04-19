# 黑马点评项目启动说明

> 最后更新：2026-04-09

---

## 快速启动

推荐直接用项目根目录下的脚本：

- 启动：`start-project.bat`
- 停止：`stop-project.bat`

启动脚本会检查并尝试启动：

- MySQL
- Redis
- Qdrant
- Nginx

启动完成后：

- 前端地址：`http://localhost:8080`
- 后端地址：`http://localhost:8081`

如果后端没有自动运行，请在 IDEA 中启动 `HmDianPingApplication`。

---

## 脚本说明

### `start-project.bat`

项目总启动入口。

作用：

- 调用 `scripts/start.js`
- 检查 MySQL 是否已运行
- 启动 Redis、Qdrant、Nginx
- 启动后停留窗口，方便看日志

### `stop-project.bat`

项目总停止入口。

作用：

- 调用 `scripts/stop.js`
- 停止 Spring Boot
- 停止 Nginx
- 停止 Redis
- 停止 Qdrant
- 停止后停留窗口，避免闪退

### `start-nginx-safe.bat`

兼容旧入口，内部也会调用 `scripts/start.js`。

### `stop-nginx.bat`

兼容旧入口，内部也会调用 `scripts/stop.js`。

---

## 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| Nginx | 8080 | 前端入口 |
| Spring Boot | 8081 | 后端接口 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| Qdrant | 6333 | 向量检索 |

---

## 常见问题

### 1. 首页打不开

先确认：

- `Nginx` 是否启动
- 8080 端口是否被占用

检查命令：

```bash
tasklist | findstr nginx.exe
netstat -ano | findstr 8080
```

### 2. 后端接口报错

先确认：

- Spring Boot 是否已启动
- Redis 是否已启动
- MySQL 是否已启动

检查命令：

```bash
netstat -ano | findstr 8081
netstat -ano | findstr 6379
sc query MySQL80
```

### 3. 停止脚本以前会闪退，现在怎么用

现在直接双击：

- `stop-project.bat`

窗口会保留，不会再一闪而过。

### 4. 图片 404

这次已经补了首页常见缺失图片的占位文件。

如果后续还有新的图片 404，优先检查：

- 数据库里的图片路径是否真实存在
- `dianping-applet/html/hmdp/imgs/` 下是否有对应文件

---

## 推荐操作流程

### 日常启动

1. 双击 `start-project.bat`
2. 确认前端服务正常
3. 如果后端没起来，在 IDEA 里启动 `HmDianPingApplication`
4. 打开 `http://localhost:8080`

### 日常停止

1. 双击 `stop-project.bat`
2. 等待脚本输出完成
3. 确认 Redis / Qdrant / Nginx / Spring Boot 已停止

---

## 备注

如果你后面继续调整脚本逻辑，优先改这两个 JS 文件：

- `scripts/start.js`
- `scripts/stop.js`

批处理文件现在只是入口壳，不建议再把主要逻辑重新塞回 `.bat` 里。
