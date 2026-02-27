# 黑马点评项目启动说明

> 最后更新：2026-02-16

---

## 🚀 快速启动（推荐）

### 方法1：一键启动脚本

双击运行项目根目录的 `start-project.bat`

脚本会自动：
- ✅ 检查MySQL服务（端口3306）
- ✅ 检查Redis服务（端口6379）
- ✅ 启动Nginx前端（端口8080）
- 💡 提示你在IDEA中启动后端

启动后访问：http://localhost:8080

---

### 方法2：IDEA启动（现已修复）

现在可以安全使用IDEA的 **"Start All Services"** 了！

已修复问题：
- ✅ 不会重复启动nginx
- ✅ 自动检查nginx是否已运行
- ✅ 避免端口冲突

启动步骤：
1. 在IDEA中点击运行配置下拉菜单
2. 选择 **"Start All Services"**
3. 等待启动完成
4. 访问 http://localhost:8080

---

## 📋 启动前置条件

### 必须启动的服务
1. **MySQL** - 数据库服务（端口3306）
2. **Redis** - 缓存服务（端口6379）

### 检查服务状态

```bash
# 检查MySQL
sc query MySQL80

# 检查Redis
netstat -ano | findstr 6379

# 检查nginx
tasklist | findstr nginx.exe
```

---

## 🛠️ 脚本说明

### start-project.bat
**功能**：一键启动完整项目
- 检查MySQL/Redis服务
- 启动nginx（带检查）
- 提示启动后端

**使用**：双击运行

---

### start-nginx-safe.bat
**功能**：安全启动nginx
- 检查nginx是否已运行
- 如果未运行，启动nginx
- 如果已运行，跳过启动

**使用**：
```bash
# 手动执行
start-nginx-safe.bat

# 或在IDEA中通过"Start Nginx"运行
```

---

### stop-nginx.bat
**功能**：优雅停止nginx
- 先尝试优雅停止（nginx -s quit）
- 如果失败，强制结束进程

**使用**：
```bash
# 手动执行
stop-nginx.bat

# 或在IDEA中通过"Stop Nginx"运行
```

---

## 🔧 IDEA运行配置说明

### Start All Services（复合配置）
- 自动执行 Start Nginx
- 自动启动 HmDianPingApplication
- **现已修复重复启动问题**

### Start Nginx
- 调用 `start-nginx-safe.bat`
- 工作目录：项目根目录
- **会检查nginx是否已运行**

### Stop Nginx
- 调用 `stop-nginx.bat`
- 优雅停止nginx进程

### HmDianPingApplication
- Spring Boot启动配置
- 端口：8081

---

## ❌ 常见问题

### Q1: 提示"店铺加载失败"
**原因**：多个nginx进程冲突

**解决**：
```bash
# 停止所有nginx
stop-nginx.bat

# 重新启动
start-nginx-safe.bat
```

---

### Q2: 端口8080被占用
**检查**：
```bash
netstat -ano | findstr 8080
```

**解决**：
- 如果是nginx占用：运行 `stop-nginx.bat`
- 如果是其他程序：找到进程ID并结束

---

### Q3: MySQL或Redis未启动
**MySQL**：
```bash
net start MySQL80
```

**Redis**：
- 手动启动redis-server.exe
- 或安装为Windows服务

---

### Q4: 后端启动失败
**检查**：
1. MySQL是否在运行
2. Redis是否在运行
3. application.yaml配置是否正确
4. 端口8081是否被占用

---

## 📊 端口占用情况

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 (nginx) | 8080 | 访问入口 |
| 后端 (Spring Boot) | 8081 | API服务 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |

---

## 🎯 推荐启动流程

### 每天第一次启动
1. 开机后，MySQL和Redis自动启动（如果配置了自启动）
2. 双击 `start-project.bat`（检查环境+启动nginx）
3. 在IDEA中启动 `HmDianPingApplication`
4. 访问 http://localhost:8080

### 后续开发（nginx已启动）
- 直接在IDEA中运行 `HmDianPingApplication` 即可
- 或使用 `Start All Services`（会自动跳过nginx启动）

### 关机前
- 不需要手动停止任何服务
- 关机会自动结束所有进程

---

## 📝 修改记录

### 2026-02-16
- ✅ 创建 `start-nginx-safe.bat` - 安全启动脚本
- ✅ 创建 `stop-nginx.bat` - 优雅停止脚本
- ✅ 创建 `start-project.bat` - 一键启动脚本
- ✅ 修改IDEA的Start_Nginx.xml - 使用安全脚本
- ✅ 修改IDEA的Stop_Nginx.xml - 使用优雅停止
- ✅ 修复"重复启动nginx导致端口冲突"问题

---

**如有问题，请查看 docs/疑问记录.md**
