# 🐎 黑马点评（DianPing）项目说明

## 📌 项目简介
黑马点评是一款仿照“大众点评”的本地生活服务平台，支持用户浏览商户信息、发布笔记、点赞评论、领取优惠券等功能。该项目主要用于学习前端页面搭建、后端接口开发及 Redis 缓存优化实战。

---

## ⚙️ 技术栈

### 🖥 前端
- HTML / CSS / JavaScript
- Vue.js + Element UI（轻量级组件库）

### 🔧 后端
- Spring Boot
- MyBatis Plus
- Redis（缓存优化）
- MySQL
- Lombok（简化 Java Bean）
- Hutool（工具类封装）

---

## 🗂️ 项目结构

```
dianping/
├── dianping-applet/        # 前端页面（H5 移动端）
│   ├── html/               # 页面文件（HTML+Vue）
│   ├── css/                # 样式文件
│   ├── imgs/               # 图片资源
│   └── ...
├── dianping-backend/       # 后端 Spring Boot 项目（Java）
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/       # Java 源码
│   │   │   └── resources/  # 配置文件和静态资源
│   │   └── test/
│   └── pom.xml             # Maven 依赖配置
├── docs/                   # 文档资料与数据库脚本
│   ├── hmdp.sql            # 数据库脚本
│   └── ... 
└── README.md               # 项目说明文档
```


---

## 💡 页面功能说明

| 页面名称         | 路径                             | 功能描述                                   |
|------------------|----------------------------------|--------------------------------------------|
| 首页           | `index.html`                    | 展示推荐商户分类、热门笔记                 |
| 商户详情页     | `shop-detail.html`              | 查看商户详细信息、优惠券、营业时间等       |
| 笔记编辑页     | [blog-edit.html](file://D:\Code\study\JavaCode\dianping\dianping-applet\html\hmdp\blog-edit.html)                | 用户发布图文笔记                           |
| 个人主页       | [info.html](file://D:\Code\study\JavaCode\dianping\dianping-applet\html\hmdp\info.html)                     | 展示用户基本信息、积分、关注状态           |
| 个人资料编辑页 | [info-edit.html](file://D:\Code\study\JavaCode\dianping\dianping-applet\html\hmdp\info-edit.html)                | 修改头像、昵称、城市、生日等信息           |
| 其他用户主页   | [other-info.html](file://D:\Code\study\JavaCode\dianping\dianping-applet\html\hmdp\other-info.html)               | 查看其他用户主页及关注操作                 |
| 登录页         | [login2.html](file://D:\Code\study\JavaCode\dianping\dianping-applet\html\hmdp\login2.html)                  | 使用手机号密码登录                         |

---

## 🚀 运行方式

### 🌐 前端运行（纯静态页面）
1. 打开 [nginx.conf](file://D:\Code\study\JavaCode\dianping\dianping-applet\conf\nginx.conf) 配置；
2. 启动 Nginx 或使用 VSCode Live Server 插件直接打开 HTML 页面即可预览。

### 🛠 后端运行（Java）
1. 导入 `dianping-backend` 到 IDE（如 IntelliJ IDEA）；
2. 配置 `application.yml` 中的数据库连接；
3. 运行主类 `DianpingApplication.java` 启动服务。

---

## 🗃️ 数据库初始化

导入 `docs/hmdp.sql` 到 MySQL 数据库中：
```sql
source docs/hmdp.sql;
```


---

## ⚠️ 注意事项

- 确保已安装 Node.js、NPM、JDK 1.8 及以上；
- 使用 Redis 的部分需要启动 Redis 服务；
- 静态资源路径需根据实际部署环境调整。

---

## 📚 参考资料

- [Element UI 官方文档](https://element.eleme.io/#/zh-CN)
- [Vue.js 官方文档](https://v3.cn.vuejs.org/)
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)