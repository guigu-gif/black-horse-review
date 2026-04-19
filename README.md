# 黑马点评（升级版）

仿大众点评平台，基于黑马程序员原版项目二次开发，在原版基础上独立扩展多个功能模块，并集成 AI 能力。

---

## 技术栈

| 层 | 技术 | 用途 |
|----|------|------|
| 后端框架 | Spring Boot 2.x + MyBatis-Plus | Web 层、ORM |
| 关系型数据库 | MySQL 8 | 持久化存储 |
| 缓存 | Redis 6 | 缓存、分布式锁、GEO、BitMap、ZSet |
| 分布式锁 | Redisson | 看门狗自动续期，解决单机锁的集群问题 |
| 向量数据库 | Qdrant | 存储店铺向量，语义搜索相似度检索 |
| AI 接口 | 智谱 AI（bigmodel.cn） | Embedding（文字→向量）+ GLM-4-Flash（推荐理由生成） |
| 前端 | HTML / CSS / JavaScript（原生） | 无框架，手写原生 JS |
| 服务器 | Nginx | 静态资源服务 + 反向代理（`/api` → 后端 8081） |

---

## 功能模块总览

### 一、用户系统
| 功能 | 技术要点 |
|------|---------|
| 手机号登录 / 注册 | 验证码 → Redis 存储（TTL 2分钟） |
| Token 鉴权 | Redis 存 Token，双拦截器（刷新 + 登录校验） |
| 个人主页 | 查用户信息、博客列表 |

**双拦截器原理：**
```
所有请求 → RefreshTokenInterceptor（只刷新 TTL，不拦截）
             ↓
需鉴权路径 → LoginInterceptor（检查 UserHolder，无用户则 401）
```
解决集群下 Session 不共享的问题，Token 存 Redis 所有节点都能读。

---

### 二、商铺缓存（Redis Cache Aside Pattern）
| 问题 | 解法 | 核心思路 |
|------|------|---------|
| 缓存穿透 | 缓存空对象 | 查不到也缓存 null，TTL 2分钟 |
| 缓存击穿 | 互斥锁（Redis SETNX） | 只允许一个线程重建缓存，其他线程等待 |
| 缓存雪崩 | 随机 TTL | 过期时间错开，避免大量缓存同时失效 |

---

### 三、秒杀抢购（高并发）
```
用户请求 → Lua 脚本（Redis 原子判断：库存 + 一人一单）→ 立即返回排队结果
                                                   ↓ 异步
                                      阻塞队列 → 后台线程 → 写 MySQL
```
- Lua 脚本保证"判断"和"扣减"原子操作，彻底解决超卖
- 写库异步化，数据库压力降低，用户响应快
- 分布式锁演进：`synchronized` → Redis SETNX → **Redisson**（看门狗续期，解决锁超时问题）

---

### 四、探店笔记 / 博客
| 功能 | 技术要点 |
|------|---------|
| 发布博客 | MySQL 存内容，图片上传 |
| 点赞（每人只能点一次） | Redis Set：`blog:liked:{blogId}` 存已点赞用户 id |
| 点赞排行榜 | Redis ZSet：score 用时间戳，取 Top5 |
| Feed 流（关注列表） | 推模型：发博客 → 写入所有粉丝的 ZSet；拉取时用游标翻页 |
| 博客收藏 ⭐ | `tb_blog_bookmark` 表，独立设计实现 |

---

### 五、评论系统 ⭐（独立设计实现）

两张表，主副评论分离：

```
tb_comment        → 主评论（直接评论博客）
tb_comment_reply  → 副评论（回复主评论）
```

| 功能 | 接口 |
|------|------|
| 查主评论（分页） | `GET /blog/{blogId}/comments?page=1&size=10` |
| 展开副评论 | `GET /comment/{commentId}/replies` |
| 发主评论 | `POST /comment` |
| 发副评论 | `POST /comment/{commentId}/reply` |
| 删主评论 | `DELETE /comment/{commentId}` |
| 删副评论 | `DELETE /comment/reply/{replyId}` |
| 点赞评论 | `PUT /comment/{commentId}/like` |
| 点赞副评论 | `PUT /comment/reply/{replyId}/like` |

点赞关系存 Redis Set：`comment:liked:{commentId}`

---

### 六、好友关注 / 共同关注
| 功能 | 技术要点 |
|------|---------|
| 关注 / 取关 | MySQL 存关系，Redis Set 同步 |
| 共同关注 | Redis Set 交集（`SINTERSTORE`） |
| 关注 Feed 流 | ZSet 推模型，ZSet 游标翻页 |

---

### 七、附近商户
- Redis GEO 存储店铺经纬度
- 按距离排序查询，返回距离信息
- 无 GPS 时降级查 MySQL

---

### 八、用户签到
- Redis BitMap 存月签到记录（每个 bit = 一天）
- 连续签到统计：从今天向前找最后一个 0

---

### 九、商铺收藏 ⭐（独立实现）
- 表：`tb_shop_favorite`（userId + shopId，唯一索引防重复）
- 接口：收藏/取消收藏、收藏列表、被收藏数量

---

### 十、AI 语义搜索
```
初始化：店铺名+地址+类型 → 智谱 Embedding API → 1024维向量 → 存 Qdrant
搜索时：用户输入 → 同样转向量 → Qdrant 余弦相似度 → 返回最相近的店铺
```
搜"便宜好吃的火锅"能匹配"铜锅涮羊肉"，突破关键词限制。

---

### 十一、帮我选（AI 逆向推荐）⭐
解决"不知道吃什么"的决策疲劳场景。

```
用户点击"帮我选"（需登录）
   ↓
后端：查高分店铺 → 推导标签（类型/价格区间/营业状态）
   ↓
过滤用户排除的标签
   ↓
调用 GLM-4-Flash：批量生成推荐理由（结合时间段/用户说明）
   ↓
返回：店铺信息 + 标签 + AI推荐理由
```

用户可以：
- 点标签排除（如"[辣]"）→ 自动重推
- 点"不想去"→ 整张卡片排除
- 输入补充说明（"不吃辣 预算30以内"）→ AI 理解后推荐

---

## 项目结构

```
black-horse-review/
├── dianping-backend/                    # 后端（Spring Boot，端口 8081）
│   └── src/main/java/com/hmdp/
│       ├── controller/                  # 接口层（12个Controller）
│       │   ├── UserController           # 登录、用户信息
│       │   ├── ShopController           # 店铺查询、AI语义搜索
│       │   ├── BlogController           # 探店笔记、Feed流
│       │   ├── CommentController        # 评论（主+副+点赞）
│       │   ├── RecommendController      # AI帮我选推荐
│       │   ├── FollowController         # 关注/共同关注
│       │   ├── VoucherController        # 优惠券管理
│       │   ├── VoucherOrderController   # 秒杀下单
│       │   ├── ShopFavoriteController   # 商铺收藏
│       │   ├── BlogBookmarkController   # 博客收藏
│       │   ├── ShopTypeController       # 商铺分类
│       │   └── UploadController         # 图片上传
│       ├── service/                     # 业务层
│       │   └── impl/
│       │       ├── EmbeddingService     # 智谱AI Embedding
│       │       ├── QdrantService        # 向量数据库操作
│       │       └── RecommendServiceImpl # 帮我选推荐逻辑
│       ├── entity/                      # 数据库实体
│       ├── dto/                         # 请求/响应DTO
│       ├── mapper/                      # MyBatis-Plus Mapper
│       ├── config/                      # MVC配置、Redisson配置
│       └── utils/                       # RedisIdWorker、拦截器、常量
│
├── dianping-applet/                     # 前端（Nginx，端口 8080）
│   ├── html/hmdp/
│   │   ├── index.html        # 首页（商铺浏览 + 三模式搜索 + 帮我选）
│   │   ├── blog.html         # 探店笔记列表
│   │   ├── blog-detail.html  # 笔记详情 + 评论区
│   │   ├── shop-detail.html  # 店铺详情 + 秒杀入口
│   │   ├── login.html        # 登录
│   │   ├── profile.html      # 个人主页
│   │   ├── favorites.html    # 收藏夹
│   │   ├── follow.html       # 关注动态
│   │   └── voucher-orders.html # 我的订单
│   └── conf/nginx.conf
│
├── docs/
│   └── 资料/hmdp.sql         # 数据库初始化脚本
└── dianping-backend/src/main/resources/
    ├── db/comment-spec.sql   # 评论表建表脚本
    └── seckill.lua           # 秒杀 Lua 脚本
```

---

## 数据库表

| 表名 | 说明 |
|------|------|
| `tb_user` | 用户基本信息 |
| `tb_user_info` | 用户详细信息 |
| `tb_shop` | 商铺信息（含经纬度、营业时间） |
| `tb_shop_type` | 商铺分类 |
| `tb_shop_favorite` | 商铺收藏关系 |
| `tb_blog` | 探店笔记 |
| `tb_blog_bookmark` | 博客收藏关系 |
| `tb_comment` | 主评论 |
| `tb_comment_reply` | 副评论 |
| `tb_follow` | 关注关系 |
| `tb_voucher` | 优惠券 |
| `tb_seckill_voucher` | 秒杀券（库存、时间） |
| `tb_voucher_order` | 秒杀订单 |

---

## Redis Key 说明

| Key 模式 | 类型 | 用途 |
|---------|------|------|
| `login:code:{phone}` | String | 验证码，TTL 2分钟 |
| `login:token:{token}` | Hash | 用户信息，TTL 36000s |
| `cache:shop:{id}` | String | 店铺缓存，TTL 30min |
| `lock:shop:{id}` | String | 缓存重建互斥锁 |
| `seckill:stock:{voucherId}` | String | 秒杀库存 |
| `blog:liked:{blogId}` | Set | 博客点赞用户集合 |
| `comment:liked:{commentId}` | Set | 评论点赞用户集合 |
| `feed:{userId}` | ZSet | 收件箱 Feed 流，score=时间戳 |
| `shop:geo:{typeId}` | GEO | 店铺地理位置 |
| `sign:{userId}{yyyyMM}` | BitMap | 月签到记录 |

---

## 快速启动

**1. 初始化数据库**

```sql
-- 执行（顺序不能乱）
docs/资料/hmdp.sql
dianping-backend/src/main/resources/db/comment-spec.sql
```

**2. 配置应用**

复制 `application-example.yaml` → `application.yaml`，填写：
- MySQL 密码
- 智谱 AI API Key（语义搜索 + 帮我选需要）

**3. 启动服务**

```
MySQL  :3306   → 确保运行
Redis  :6379   → 确保运行
Qdrant :6333   → 可选（仅语义搜索需要）
Nginx  :8080   → 双击 start-nginx-safe.bat
Spring :8081   → IDEA 运行 HmDianPingApplication
```

**4. 初始化向量数据**（首次，使用 AI 搜索功能时）

运行测试类 `HmDianPingApplicationTests.loadShopVectors()`

**5. 访问**

```
http://localhost:8080
```

---

## 端口总览

| 服务 | 端口 |
|------|------|
| 前端 Nginx | 8080 |
| 后端 Spring Boot | 8081 |
| MySQL | 3306 |
| Redis | 6379 |
| Qdrant（可选） | 6333 |
