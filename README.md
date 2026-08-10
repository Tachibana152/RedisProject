# 黑马点评（hm-dianping）项目文档

> 基于 Spring Boot 2.7 实现的"黑马点评"商家点评平台后端服务。
> 覆盖登录鉴权、商户缓存、优惠券秒杀、关注推送、附近商户、签到等典型业务场景，
> 是学习 Redis 在实际业务中应用的完整示例项目。

---

## 目录

- [1. 项目简介](#1-项目简介)
- [2. 技术栈](#2-技术栈)
- [3. 环境要求](#3-环境要求)
- [4. 快速启动](#4-快速启动)
- [5. 项目结构](#5-项目结构)
- [6. 核心功能与实现](#6-核心功能与实现)
- [7. 秒杀流程详解](#7-秒杀流程详解)
- [8. 接口一览](#8-接口一览)
- [9. Redis Key 设计](#9-redis-key-设计)
- [10. 数据库设计](#10-数据库设计)
- [11. 常见问题与注意事项](#11-常见问题与注意事项)

---

## 1. 项目简介

本项目是经典的"黑马点评"项目，模拟一个类似大众点评的商户点评平台。
前端页面由 `nginx` 托管（`forward/nginx-1.18.0/html/hmdp`），后端提供 RESTful API。

项目重点不在于业务本身，而在于 **Redis 在实际高并发场景下的综合应用**：

| 业务场景         | Redis 应用                                  |
| ---------------- | ------------------------------------------- |
| 短信登录         | 验证码、Token 存储                          |
| 商户查询         | 缓存穿透 / 击穿 / 雪崩 解决方案             |
| 优惠券秒杀       | Lua 脚本原子操作、分布式锁、Stream 消息队列 |
| 达人探店（博客） | 点赞（ZSet）、关注推送 Feed 流（ZSet）      |
| 附近商户         | GEO 地理位置检索                            |
| 用户签到         | BitMap 位图                                 |
| 全局 ID 生成     | 时间戳 + 自增序列                           |

---

## 2. 技术栈

| 分类     | 技术                                             | 版本          |
| -------- | ------------------------------------------------ | ------------- |
| 框架     | Spring Boot                                      | 2.7.18        |
| ORM      | MyBatis-Plus                                     | 3.5.5         |
| 数据库   | MySQL（`mysql-connector-java`）                  | 5.1.47        |
| 缓存     | Spring Data Redis（Lettuce + commons-pool2）     | Boot 2.7 内置 |
| 分布式锁 | Redisson                                         | 3.13.6        |
| 工具库   | Hutool                                           | 5.7.17        |
| 编译     | Maven + Lombok（1.18.46，兼容 JDK 21）           | -             |
| Java     | JDK 8（`source/target = 8`，可在 JDK 21 上运行） | 1.8           |

> 说明：`pom.xml` 中通过 `<lombok.version>1.18.46</lombok.version>` 覆盖 Spring Boot 管理的旧版 Lombok，以兼容 JDK 21 运行环境。

---

## 3. 环境要求

- **JDK 8+**（项目配置为 Java 8 语法，可在 JDK 21 上运行）
- **Maven 3.6+**
- **MySQL 5.7+**（数据库名 `hmdp`）
- **Redis 5.0+**（需支持 Stream，用于秒杀消息队列）
- **Nginx**（托管前端静态页面，可选，仅前端展示需要）

---

## 4. 快速启动

### 4.1 初始化数据库

1. 在 MySQL 中创建数据库 `hmdp`（utf8mb4）；
2. 导入 `db/` 目录下的建表 SQL（含 `tb_seckill_voucher.sql` 等）；
3. 按需插入测试数据（商户、优惠券等）。

### 4.2 修改配置

编辑 `src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC
    username: root
    password: 你的数据库密码
  redis:
    host: 你的Redis地址
    port: 6379
    password: "你的Redis密码" # 必须以字符串形式书写，避免 0 开头被解析为八进制
```

同时修改 `utils/SystemConstants.java` 中的 `IMAGE_UPLOAD_DIR`，指向你的 Nginx 静态资源目录：

```java
public static final String IMAGE_UPLOAD_DIR = "...\\forward\\nginx-1.18.0\\html\\hmdp\\imgs";
```

### 4.3 启动项目

```bash
mvn spring-boot:run
# 或
mvn clean package -DskipTests && java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

服务默认端口：**8081**。

### 4.4 启动前端（可选）

配置 Nginx 将 `forward/nginx-1.18.0/html/hmdp` 作为静态站点，并把 `/api` 反向代理到 `127.0.0.1:8081`，即可在浏览器中体验完整功能。

---

## 5. 项目结构

```
├── pom.xml                          # Maven 配置
├── db/                              # 数据库建表脚本
│   └── tb_seckill_voucher.sql
├── forward/                         # 前端静态资源 + Nginx
│   └── nginx-1.18.0/
│       └── html/hmdp/               # 前端页面
└── src/main/
    ├── java/com/hmdp/
    │   ├── HmDianPingApplication.java   # 启动类
    │   ├── config/                      # 配置类
    │   │   ├── MvcConfig.java           # 拦截器注册
    │   │   ├── MybatisConfig.java       # MyBatis-Plus 分页插件
    │   │   ├── RedisConfig.java         # RedisTemplate 序列化
    │   │   └── WebExceptionAdvice.java  # 全局异常处理
    │   ├── controller/                  # 控制器层
    │   ├── dto/                         # 数据传输对象
    │   │   ├── Result.java              # 统一返回结果
    │   │   ├── LoginFormDTO.java
    │   │   ├── ScrollResult.java        # 滚动分页结果
    │   │   └── UserDTO.java
    │   ├── entity/                      # 实体类（Blog、Shop、Voucher 等 10 张表）
    │   ├── mapper/                      # MyBatis-Plus Mapper
    │   ├── service/                     # 服务接口 + 实现
    │   └── utils/                       # 工具类
    │       ├── CacheClient.java         # 缓存工具（穿透/逻辑过期通用封装）
    │       ├── ILock.java / SimpleRedisLock.java  # 手写 Redis 互斥锁
    │       ├── RedisIdWorker.java       # 全局 ID 生成器
    │       ├── RedisConstants.java      # Redis Key 常量
    │       ├── RedisData.java           # 逻辑过期包装类
    │       ├── LoginInterceptor.java    # 登录拦截器（校验 Token）
    │       ├── RefreshTokenInterceptor.java # Token 刷新拦截器
    │       ├── UserHolder.java          # 当前用户 ThreadLocal
    │       ├── PasswordEncoder.java     # 密码加密
    │       ├── RegexPatterns.java / RegexUtils.java # 手机号校验
    │       └── SystemConstants.java     # 系统常量
    └── resources/
        ├── application.yaml             # 配置文件
        ├── seckill.lua                  # 秒杀 Lua 脚本
        └── mapper/VoucherMapper.xml
```

---

## 6. 核心功能与实现

### 6.1 短信登录

**流程**：

1. `POST /user/code`：校验手机号格式 → 生成 6 位验证码 → 存入 Redis `login:code:{phone}`（TTL 2 分钟）→ 短信发送（本项目打印到日志）；
2. `POST /user/login`：校验验证码 → 查询/创建用户 → 生成随机 Token（`UUID`）→ 用户信息存入 Redis `login:token:{token}`（TTL 30 天）→ 返回 Token；
3. 前端请求头携带 `authorization: {token}`，由 `RefreshTokenInterceptor` 解析并 **续期**（滑动过期），`LoginInterceptor` 校验是否登录。

**双拦截器设计**：

- `RefreshTokenInterceptor`：order = 0，拦截所有请求，刷新登录态；
- `LoginInterceptor`：order = 1，仅校验登录，未登录返回 401。

**要点**：使用 `UserHolder`（ThreadLocal）保存当前用户，请求结束由拦截器清理，防止内存泄漏。

### 6.2 商户查询缓存（三大缓存问题）

`ShopServiceImpl.queryById` 采用 **逻辑过期** 方案，同时代码中保留了三种方案的实现供学习：

| 方案                       | 解决                             | 实现                                                                                                         |
| -------------------------- | -------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| 缓存穿透（Pass Through）   | 查询不存在的数据                 | 缓存空值（TTL 2 分钟）                                                                                       |
| 缓存击穿（Mutex）          | 热点 Key 过期瞬间大量请求打到 DB | 互斥锁（`tryLock`/`unlock`），加锁线程重建缓存，其他线程休眠重试                                             |
| 缓存击穿（Logical Expire） | 同上，性能更好                   | 缓存中存入 `RedisData{data, expireTime}`，读时校验逻辑过期，过期则加锁后**独立线程**重建缓存，旧数据继续服务 |
| 缓存雪崩                   | 大量 Key 同时过期                | 为不同商铺设置随机 TTL                                                                                       |

通用封装在 `CacheClient` 中：

- `queryWithPassThrough(prefix, id, type, dbFallback, time, unit)`
- `queryWithLogicalExpire(prefix, id, type, dbFallback, time, unit)`

**商铺更新**：`PUT /shop` 先更新数据库，再删除缓存（保证最终一致性）。

### 6.3 附近商户（GEO）

- 商户入库时调用 `updateShopGeo`，将店铺坐标写入 `shop:geo:{typeId}` 的 **GEO ZSet**；
- `GET /shop/of/type?typeId=xxx&sortBy=distance&x=..&y=..` 通过 `GEOSEARCH` 按距离排序分页返回附近商户；
- 缓存未命中时自动 `loadShopGeo` 从数据库全量加载。

### 6.4 达人探店（博客）

- **发布**：`POST /blog` 保存博文，并**推模式**写入所有粉丝的 Feed 流 `feed:{粉丝id}`（ZSet，score = 发布时间戳）；
- **点赞**：`PUT /blog/like/{id}` 用 ZSet 记录点赞用户（score = 点赞时间），实现一人一赞（`ZSCORE` 判断是否已赞），并同步更新数据库 `liked` 字段；
- **点赞排行**：`GET /blog/likes/{id}` 返回点赞时间最早的 Top5 用户；
- **关注推送**：`GET /blog/of/follow?lastId=&offset=` 使用 ZSet **滚动分页**（`ZREVRANGEBYSCORE` + `offset` 去重），避免深分页问题。

### 6.5 关注与共同关注

- `PUT /follow/{id}/{isFollow}`：关注/取关，并维护 `follows` 表；
- `GET /follow/or/not/{id}`：是否关注；
- `GET /follow/common/{id}`：利用 **ZSet 交集（`ZINTERSTORE`）** 求当前用户与目标用户的共同关注。

### 6.6 用户签到（BitMap）

- `POST /user/sign`：使用 `SETBIT sign:{userId}:{yyyyMM}` 记录当月签到；
- `GET /user/sign/count`：统计本月连续签到天数（从今天往前数连续的 1）。

### 6.7 全局 ID 生成器

`RedisIdWorker.nextId(prefix)` 生成 64 位 ID：

```
[1bit 符号位] [31bit 秒级时间戳（相对 2026-01-01 00:00:00）] [32bit 序列号]
```

- 序列号由 Redis `INCR icr:{prefix}:{yyyy:MM:dd}` 生成（按天自增，天然支持过期清理）；
- 高并发下也可以引入**号段模式**等优化（本项目按天自增即可满足）。

---

## 7. 秒杀流程详解

秒杀是项目的核心亮点，采用 **Lua 脚本 + Redis Stream 异步下单** 架构。

### 7.1 整体流程

```mermaid
flowchart TD
    A[用户发起秒杀 POST /voucher-order/seckill/{id}] --> B[查询秒杀券信息]
    B --> C[生成全局订单ID]
    C --> D[执行 Lua 脚本原子校验]
    D -->|返回3 未开始/已结束| E[秒杀失败: 时间未到或已结束]
    D -->|返回1 库存不足| F[秒杀失败: 库存不足]
    D -->|返回2 已购买| G[秒杀失败: 一人一单限制]
    D -->|返回0 成功| H[返回订单ID给前端]
    H --> I[后台线程消费 stream.orders]
    I --> J[Redisson 分布式锁 一人一单]
    J --> K[代理调用 createVoucherOrder 落库]
    K --> L[ACK 确认消息]
```

### 7.2 Lua 脚本（`seckill.lua`）

一次 Redis 往返原子完成全部校验与写入：

```lua
-- 1. 时间校验（未开始/已结束返回 3）
-- 2. 库存判断（seckill:stock:{voucherId}，不足返回 1）
-- 3. 一人一单（seckill:order:{voucherId} 集合 SISMEMBER，重复返回 2）
-- 4. INCRBY 扣库存 + SADD 记录用户
-- 5. XADD stream.orders 发送消息（voucherId / userId / orderId）
-- 6. 返回 0 表示抢购成功
```

### 7.3 异步下单（Redis Stream）

- 抢购成功只代表**资格认定**，订单通过 `stream.orders` 消息由后台单线程 `VoucherOrderHandler` 消费落库；
- 消费时通过 **Redisson 分布式锁** `lock:order:{userId}` 保证一人一单（锁内调用 `proxy.createVoucherOrder`，走代理保证 `@Transactional` 生效）；
- 消费失败的消息进入 **pending-list**，`handlePendingList` 兜底重试；
- 服务启动时 `@PostConstruct` 自动创建消费者组 `g1`（`XGROUP CREATE ... MKSTREAM`）。

> 优势：秒杀请求只做一次 Redis 操作，吞吐量极高；MySQL 写入压力被异步削峰。

---

## 8. 接口一览

### 用户（`/user`）

| 方法 | 路径               | 说明             |
| ---- | ------------------ | ---------------- |
| POST | `/user/code`       | 发送短信验证码   |
| POST | `/user/login`      | 登录             |
| POST | `/user/logout`     | 退出登录         |
| GET  | `/user/me`         | 获取当前登录用户 |
| GET  | `/user/{id}`       | 根据 id 查询用户 |
| GET  | `/user/info/{id}`  | 用户详情         |
| POST | `/user/sign`       | 签到             |
| GET  | `/user/sign/count` | 连续签到天数     |

### 商户（`/shop`、`/shop-type`）

| 方法 | 路径              | 说明                               |
| ---- | ----------------- | ---------------------------------- |
| GET  | `/shop/{id}`      | 根据 id 查询商铺（带缓存）         |
| POST | `/shop`           | 新增商铺                           |
| PUT  | `/shop`           | 更新商铺                           |
| GET  | `/shop/of/type`   | 按类型查询（支持按距离排序、分页） |
| GET  | `/shop/of/name`   | 按名称模糊查询                     |
| GET  | `/shop-type/list` | 商铺类型列表                       |

### 博客（`/blog`、`/blog-comments`）

| 方法 | 路径               | 说明                      |
| ---- | ------------------ | ------------------------- |
| POST | `/blog`            | 发布博客（推送粉丝 Feed） |
| GET  | `/blog/{id}`       | 查询博客详情              |
| GET  | `/blog/hot`        | 热门博客分页              |
| GET  | `/blog/of/me`      | 我的博客                  |
| GET  | `/blog/of/user`    | 某用户的博客              |
| GET  | `/blog/of/follow`  | 关注推送 Feed（滚动分页） |
| PUT  | `/blog/like/{id}`  | 点赞/取消点赞             |
| GET  | `/blog/likes/{id}` | 点赞 Top5                 |
| POST | `/blog-comments`   | 发表评论                  |

### 关注（`/follow`）

| 方法 | 路径                      | 说明        |
| ---- | ------------------------- | ----------- |
| PUT  | `/follow/{id}/{isFollow}` | 关注 / 取关 |
| GET  | `/follow/or/not/{id}`     | 是否关注    |
| GET  | `/follow/common/{id}`     | 共同关注    |

### 优惠券（`/voucher`、`/voucher-order`）

| 方法 | 路径                          | 说明                     |
| ---- | ----------------------------- | ------------------------ |
| POST | `/voucher`                    | 新增普通优惠券           |
| POST | `/voucher/seckill`            | 新增秒杀券               |
| GET  | `/voucher/list/{shopId}`      | 查询商铺优惠券           |
| POST | `/voucher-order/seckill/{id}` | 秒杀下单（Lua + Stream） |

### 上传（`/upload`）

| 方法 | 路径                  | 说明         |
| ---- | --------------------- | ------------ |
| POST | `/upload/blog`        | 上传博客图片 |
| GET  | `/upload/blog/delete` | 删除图片     |

> 所有接口统一返回 `Result` 包装对象（`code` / `msg` / `data`）。

---

## 9. Redis Key 设计

| Key 模板                    | 类型   | 说明                             | TTL            |
| --------------------------- | ------ | -------------------------------- | -------------- |
| `login:code:{phone}`        | String | 短信验证码                       | 2 分钟         |
| `login:token:{token}`       | String | 登录用户信息                     | 30 天          |
| `cache:shop:{id}`           | String | 商铺缓存                         | 30 分钟        |
| `cache:shop:{id}`           | String | 商铺缓存（逻辑过期包装）         | 无（逻辑过期） |
| `lock:shop:{id}`            | String | 商铺缓存重建互斥锁               | 10 秒          |
| `lock:order:{userId}`       | String | 秒杀一人一单分布式锁（Redisson） | 看门狗续期     |
| `seckill:stock:{voucherId}` | String | 秒杀库存                         | 秒杀期间       |
| `seckill:order:{voucherId}` | Set    | 已抢购用户集合                   | 秒杀期间       |
| `icr:{prefix}:{yyyy:MM:dd}` | String | 全局 ID 序列                     | 按天           |
| `blog:liked:{blogId}`       | ZSet   | 点赞用户（score=点赞时间）       | 永久           |
| `feed:{userId}`             | ZSet   | 用户收件箱（score=发布时间）     | 永久           |
| `shop:geo:{typeId}`         | GEO    | 商铺地理位置                     | 永久           |
| `sign:{userId}:{yyyyMM}`    | BitMap | 当月签到                         | 按月           |
| `stream.orders`             | Stream | 秒杀订单消息队列                 | -              |

---

## 10. 数据库设计

项目使用 MyBatis-Plus，实体类与表一一对应（`type-aliases-package: com.hmdp.entity`）：

| 表                   | 实体             | 说明                                       |
| -------------------- | ---------------- | ------------------------------------------ |
| `tb_user`            | `User`           | 用户（账号、密码、手机号等）               |
| `tb_user_info`       | `UserInfo`       | 用户详情（粉丝数、关注数等）               |
| `tb_shop`            | `Shop`           | 商铺（含经纬度、评分）                     |
| `tb_shop_type`       | `ShopType`       | 商铺类型                                   |
| `tb_blog`            | `Blog`           | 探店博客                                   |
| `tb_blog_comments`   | `BlogComments`   | 博客评论                                   |
| `tb_follow`          | `Follow`         | 关注关系                                   |
| `tb_voucher`         | `Voucher`        | 优惠券                                     |
| `tb_seckill_voucher` | `SeckillVoucher` | 秒杀券（含库存、起止时间）                 |
| `tb_voucher_order`   | `VoucherOrder`   | 秒杀订单（`user_id` 唯一索引保证一人一单） |

秒杀券表（`db/tb_seckill_voucher.sql`）核心字段：`voucher_id`（主键）、`stock`（库存）、`begin_time` / `end_time`（秒杀时间窗）。

---

## 11. 常见问题与注意事项

### 11.1 启动报错

- **`allow-circular-references`**：项目使用自注入（`@Autowired IVoucherOrderService proxy`）保证事务代理生效，已在 `application.yaml` 中配置 `spring.main.allow-circular-references: true`，**不要移除**；
- **Lombok 版本**：JDK 21 下需使用 1.18.46（`pom.xml` 已覆盖），升级依赖时注意不要改回旧版；
- **Redis 密码**：`password` 必须以字符串书写，如 `"07210721"`，否则 YAML 会按八进制解析出错。

### 11.2 秒杀相关

- 秒杀券库存的初始化写入 Redis（`seckill:stock:{id}`）后，Lua 脚本才能正确扣减，否则 `get` 返回 nil 时按 0 处理会直接判库存不足；
- `handleVoucherOrder` 中 Redisson 锁 `tryLock(1, 10, TimeUnit.SECONDS)`：等待 1 秒、租约 10 秒（配合看门狗自动续期）；
- 事务方法必须通过 `proxy` 调用，否则 `@Transactional` 不生效（Spring 代理机制）。

### 11.3 缓存一致性

- 更新商铺采用「先更新 DB，再删除缓存」策略；
- 逻辑过期方案依赖缓存预热，未命中时会兜底查库写入逻辑过期缓存（见 `CacheClient.queryWithLogicalExpire` 注释）。

### 11.4 前端对接

- 上传目录 `IMAGE_UPLOAD_DIR` 与 Nginx `imgs` 目录必须一致，否则图片无法访问；
- 前端页面位于 `forward/nginx-1.18.0/html/hmdp/`，需自行配置 Nginx 代理 `/api` 到 `8081` 端口。

---

## 附：测试

项目包含单元测试：

- `HmDianPingApplicationTests`：Spring 上下文加载测试；
- `UserLoginBatchTest`：批量用户登录测试（可用于压测登录接口）。

运行：

```bash
mvn test
```

> 注意：测试类可能依赖本地 Redis / MySQL，请确保环境已就绪。
