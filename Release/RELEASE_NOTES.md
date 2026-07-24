# IM-System v1.0.0 发布说明

## 版本信息
- 版本号：**v1.0.0**
- 发布日期：2026-07-24
- 构建环境：Maven 3.8.5 + JDK 17
- 仓库：bestMagixx/Instant-Messaging-System

## 架构与模块
本系统为**分布式架构**，各模块独立部署、单模块故障不影响其他模块。提供 3 个可执行启动 jar：

| 层 | 文件名 | 主类 | 默认端口 | 说明 |
|----|--------|------|----------|------|
| 接入层 | im-system-tcp-1.0.0.jar | com.lld.im.tcp.Starter | TCP 9000 / WS 19000 | Netty 接入网关 |
| 业务层 | im-system-service-1.0.0.jar | com.lld.im.service.Application | HTTP 8000 | 业务逻辑 / 路由 |
| 存储层 | im-system-message-store-1.0.0.jar | com.lld.im.message.Application | 见 application.yml | 消息存储消费 |

> `codec` / `common` 为公共库（编解码器、实体、工具），已被上述模块依赖打包，**无需单独启动**。
> 注：原 `codec` 含启动类 `CodecApplication` 已移除，`codec` 现仅作库使用。

## 启动方式
```bash
# 建议顺序：基础设施就绪 → service → tcp → message-store
java -jar im-system-service-1.0.0.jar
java -jar im-system-tcp-1.0.0.jar
java -jar im-system-message-store-1.0.0.jar
```
可用 `--spring.profiles.active=xxx` 或 `-Dserver.port=xxxx` 覆盖配置。

各模块通过 **ZooKeeper** 注册与发现；`tcp` 通过 `logicUrl`（http://127.0.0.1:8000/v1）调用 `service`。

## 依赖的基础设施（需先就绪）
- MySQL 3306（库 `im-core`）
- Redis `192.168.74.128:6379`
- RabbitMQ 5672（guest/guest）
- ZooKeeper `192.168.74.128:2181`

## ⚠️ 部署前必读（配置脱敏）
当前 jar 内已打包 `application.yml` / `config.yaml`，包含**明文密码与私有网段 IP**：
- MySQL 密码、Redis 密码（明文）
- 私有 IP `192.168.74.128`（虚拟机/内网地址）
- RabbitMQ `guest/guest`

若为**公开仓库 / 公开 Release**，请先：
1. 将凭据改为占位符或环境变量注入；
2. 将 IP 改为目标环境实际地址（或提为配置项）；
3. 或仅发布到私有仓库 / 私有 Release。

## 构建说明
- 根 `pom.xml` 已纳入 `tcp`、`im-message-store` 两个启动模块；`codec` 为纯库（无启动类）。
- 本地构建命令：`mvn clean install -DskipTests`（使用 JDK 17）。
- Release 目录下的 jar 由对应模块 `target/*.jar` 复制并重命名而来，未进版本库（见 `.gitignore`）。
