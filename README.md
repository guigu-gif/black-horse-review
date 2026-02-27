# 黑马点评（升级版）

基于黑马程序员点评项目二次开发，在原版基础上新增 AI 语义搜索、异步秒杀、完整订单链路等功能。

---

## 技术栈

**后端**
- Spring Boot + MyBatis-Plus + MySQL
- Redis（缓存、分布式锁、GEO、BitMap、ZSet）
- Redisson（分布式锁）
- Qdrant（向量数据库，语义搜索）
- 智谱 AI Embedding API（文字转向量）

**前端**
- HTML / CSS / JavaScript（原生）
- Nginx（静态资源服务）

---

## 功能模块

| 模块 | 核心技术 | 说明 |
|------|---------|------|
| 用户登录 | Redis Token | 手机号登录，双拦截器刷新 Token |
| 商铺缓存 | Redis Cache Aside | 缓存穿透（空对象）、缓存击穿（互斥锁）、缓存雪崩（随机TTL） |
| AI 语义搜索 | Qdrant + 智谱 Embedding | 搜"火锅涮肉"能找到"铜锅涮羊肉"，语义理解而非关键词匹配 |
| 秒杀抢购 | Lua 脚本 + 阻塞队列 | Redis 原子判断库存和一人一单，异步写库，高并发下不超卖 |
| 优惠券核销 | 订单状态机 | 普通券直接领取，秒杀券支付后获得核销码，商家扫码核销 |
| 探店笔记 | MySQL + Redis | 发布、点赞（ZSet排行榜）、搜索（多关键词相关度排序） |
| 好友关注 | Redis Set | 关注、取关、共同关注，Feed 流推送（ZSet 游标翻页） |
| 附近商户 | Redis GEO | 按距离排序，GeoHash 编码 |
| 用户签到 | Redis BitMap | 月签到记录，连续签到统计 |

---

## 项目结构

```
black-horse-review/
├── dianping-applet/          # 前端（H5 静态页面 + Nginx）
│   ├── html/hmdp/            # 页面文件
│   └── conf/nginx.conf       # Nginx 配置
├── dianping-backend/         # 后端 Spring Boot
│   ├── src/main/java/com/hmdp/
│   │   ├── controller/       # 接口层
│   │   ├── service/          # 业务层
│   │   └── utils/            # 工具类（RedisIdWorker、拦截器等）
│   └── src/main/resources/
│       ├── application-example.yaml  # 配置模板
│       └── seckill.lua               # 秒杀 Lua 脚本
└── docs/资料/hmdp.sql         # 数据库初始化脚本
```

---

## 快速启动

**1. 初始化数据库**
```sql
source docs/资料/hmdp.sql
```

**2. 配置应用**
```bash
cp dianping-backend/src/main/resources/application-example.yaml \
   dianping-backend/src/main/resources/application.yaml
# 填写 MySQL 密码、智谱 AI API Key
```

**3. 启动依赖服务**
- Redis（建议 5.0+）
- Qdrant（本地启动，默认端口 6333）
- Nginx（使用项目提供的 start-nginx-safe.bat）

**4. 运行后端**

IDEA 打开 `dianping-backend`，运行 `HmDianPingApplication.java`

**5. 初始化向量数据**（仅首次）

运行测试类 `HmDianPingApplicationTests` 中的 `loadShopVectors()`

---

## 亮点说明

**异步秒杀流程**
```
用户请求 → Lua脚本(Redis原子判断) → 立即返回"抢到了"
                                  ↓ 异步
                          阻塞队列 → 后台线程 → 写MySQL
```
判断和写库彻底分离，用户无感知，数据库压力大幅降低。

**AI 语义搜索**
```
商铺描述 → 智谱Embedding → 1024维向量 → Qdrant存储
用户搜索 → 同样转向量 → Qdrant余弦相似度检索 → 返回相关商铺
```
